package com.jizhang.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jizhang.common.api.ResultCode;
import com.jizhang.common.exception.BizException;
import com.jizhang.dao.entity.InvestFlowEntity;
import com.jizhang.dao.entity.InvestPositionEntity;
import com.jizhang.dao.entity.InvestValuationEntity;
import com.jizhang.dao.mapper.InvestFlowMapper;
import com.jizhang.dao.mapper.InvestPositionMapper;
import com.jizhang.dao.mapper.InvestValuationMapper;
import com.jizhang.service.InvestService;
import com.jizhang.service.dto.InvestSyncRequest;
import com.jizhang.service.support.BookAccessChecker;
import com.jizhang.service.vo.InvestDataVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 投资持仓服务实现。
 * <p>
 * 幂等键是客户端生成的 id：同一个 id 重复提交视为修改，不存在则新建。服务端不重新分配 ID，
 * 这样离线创建的记录在补推时不会因为「ID 变了」而在本地留下两条 —— 与固定支出一致。
 * <p>
 * **刻意不校验 {@code accountId} 是否真实存在**：账户在另一台设备上可能刚被删除、而删除
 * 还没同步过来，一旦强校验，整批推送都会失败，用户表现为「怎么点同步都报错」。
 * 投资数据属于附属数据，宁可先存下来让用户看到，也不要因为账户同步滞后而丢持仓 ——
 * 客户端渲染时会发现账号对不上，用户可以自己改。
 */
@Service
public class InvestServiceImpl implements InvestService {

    private final InvestPositionMapper positionMapper;
    private final InvestValuationMapper valuationMapper;
    private final InvestFlowMapper flowMapper;
    private final BookAccessChecker bookAccessChecker;

    public InvestServiceImpl(InvestPositionMapper positionMapper,
                             InvestValuationMapper valuationMapper,
                             InvestFlowMapper flowMapper,
                             BookAccessChecker bookAccessChecker) {
        this.positionMapper = positionMapper;
        this.valuationMapper = valuationMapper;
        this.flowMapper = flowMapper;
        this.bookAccessChecker = bookAccessChecker;
    }

    @Override
    public InvestDataVO load(Long bookId) {
        bookAccessChecker.requireReadable(bookId);
        InvestDataVO vo = new InvestDataVO();
        vo.setPositions(positionMapper.selectList(Wrappers.<InvestPositionEntity>lambdaQuery()
                        .eq(InvestPositionEntity::getBookId, bookId)
                        .orderByAsc(InvestPositionEntity::getSort)
                        .orderByAsc(InvestPositionEntity::getId))
                .stream()
                .map(InvestDataVO.PositionVO::from)
                .toList());
        vo.setValuations(valuationMapper.selectList(Wrappers.<InvestValuationEntity>lambdaQuery()
                        .eq(InvestValuationEntity::getBookId, bookId)
                        .orderByAsc(InvestValuationEntity::getValuedAt))
                .stream()
                .map(InvestDataVO.ValuationVO::from)
                .toList());
        vo.setFlows(flowMapper.selectList(Wrappers.<InvestFlowEntity>lambdaQuery()
                        .eq(InvestFlowEntity::getBookId, bookId)
                        .orderByAsc(InvestFlowEntity::getOccurredAt))
                .stream()
                .map(InvestDataVO.FlowVO::from)
                .toList());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sync(InvestSyncRequest request) {
        Long bookId = request.getBookId();
        bookAccessChecker.requireWritable(bookId);

        // 1. 删除持仓：逻辑删持仓本身，物理清掉它的估值与流水（持仓没了，它们再无意义）
        for (Long positionId : nullSafe(request.getDeletedPositionIds())) {
            InvestPositionEntity exist = positionMapper.selectById(positionId);
            if (exist == null || !bookId.equals(exist.getBookId())) {
                // 已经删过 / 属于别的账本：客户端重复推送同一个删除是正常的，幂等跳过
                continue;
            }
            positionMapper.deleteById(positionId);
            valuationMapper.deletePhysicallyByPositionId(positionId);
            flowMapper.deletePhysicallyByPositionId(positionId);
        }

        // 2. 持仓 upsert（客户端 id 幂等）
        Set<Long> touchedPositions = new HashSet<>();
        for (InvestSyncRequest.PositionDto dto : nullSafe(request.getPositions())) {
            upsertPosition(bookId, dto);
            touchedPositions.add(dto.getId());
        }

        // 3. 估值：只增不改，同 id 重复提交直接跳过
        for (InvestSyncRequest.ValuationDto dto : nullSafe(request.getValuations())) {
            // 归属校验用数据库而不是本次请求：客户端可能只推估值（改市值而持仓没动）
            InvestPositionEntity position = positionMapper.selectById(dto.getPositionId());
            if (position == null || !bookId.equals(position.getBookId())) {
                continue;
            }
            if (valuationMapper.selectById(dto.getId()) != null) {
                continue;
            }
            InvestValuationEntity entity = new InvestValuationEntity();
            entity.setId(dto.getId());
            entity.setBookId(bookId);
            entity.setPositionId(dto.getPositionId());
            entity.setValueFen(Math.max(dto.getValueFen(), 0L));
            entity.setValuedAt(dto.getValuedAt() == null ? System.currentTimeMillis() : dto.getValuedAt());
            valuationMapper.insert(entity);
        }

        // 4. 流水按持仓**全量覆盖**：先清空本次推送的每个持仓，再插入它这份。
        //    客户端删掉某笔流水后，请求里不再包含它，服务端跟着消失 —— 不需要删除标记。
        for (Long positionId : touchedPositions) {
            flowMapper.deletePhysicallyByPositionId(positionId);
        }
        for (InvestSyncRequest.FlowDto dto : nullSafe(request.getFlows())) {
            if (!touchedPositions.contains(dto.getPositionId())) {
                // 契约：推流水必须同时推它的持仓，否则无法判断「哪些流水是被删掉的」
                continue;
            }
            InvestFlowEntity entity = new InvestFlowEntity();
            entity.setId(dto.getId());
            entity.setBookId(bookId);
            entity.setPositionId(dto.getPositionId());
            entity.setTransactionId(dto.getTransactionId());
            entity.setSide(normalizeSide(dto.getSide()));
            entity.setAmountFen(Math.max(dto.getAmountFen(), 0L));
            entity.setOccurredAt(dto.getOccurredAt() == null ? System.currentTimeMillis() : dto.getOccurredAt());
            flowMapper.insert(entity);
        }
    }

    /* ---------------- 内部方法 ---------------- */

    private void upsertPosition(Long bookId, InvestSyncRequest.PositionDto dto) {
        InvestPositionEntity exist = positionMapper.selectById(dto.getId());
        if (exist == null) {
            InvestPositionEntity entity = new InvestPositionEntity();
            entity.setId(dto.getId());
            entity.setBookId(bookId);
            fill(entity, dto);
            positionMapper.insert(entity);
            return;
        }
        // id 撞上别的账本的记录：客户端 ID 全局唯一，出现这种情况只能说明请求有问题，
        // 直接拒绝而不是把别人的持仓改到当前账本名下
        if (!bookId.equals(exist.getBookId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "投资品种不存在");
        }
        fill(exist, dto);
        positionMapper.updateById(exist);
    }

    private void fill(InvestPositionEntity entity, InvestSyncRequest.PositionDto dto) {
        entity.setAccountId(dto.getAccountId());
        entity.setName(dto.getName().trim());
        entity.setCode(trimToNull(dto.getCode()));
        entity.setKind(normalizeKind(dto.getKind()));
        entity.setCostFen(dto.getCostFen() == null ? 0L : Math.max(dto.getCostFen(), 0L));
        entity.setSharesScaled(dto.getSharesScaled());
        entity.setTargetPercent(clamp(dto.getTargetPercent(), 0, 100, 0));
        entity.setSort(dto.getSort() == null ? 0 : dto.getSort());
        entity.setNote(trimToNull(dto.getNote()));
        entity.setArchived(dto.getArchived() != null && dto.getArchived() ? 1 : 0);
    }

    private int normalizeKind(Integer kind) {
        if (kind == null) {
            return 2;
        }
        return kind >= 1 && kind <= 5 ? kind : 2;
    }

    private int normalizeSide(Integer side) {
        return side != null && side == 2 ? 2 : 1;
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.min(Math.max(value, min), max);
    }

    private String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
