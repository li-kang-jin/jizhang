package com.jizhang.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jizhang.common.api.PageResult;
import com.jizhang.common.api.ResultCode;
import com.jizhang.common.exception.BizException;
import com.jizhang.dao.entity.AccountEntity;
import com.jizhang.dao.entity.TransactionEntity;
import com.jizhang.dao.mapper.AccountMapper;
import com.jizhang.dao.mapper.TransactionMapper;
import com.jizhang.service.TransactionService;
import com.jizhang.service.dto.TransactionQueryRequest;
import com.jizhang.service.dto.TransactionSaveRequest;
import com.jizhang.service.support.BookAccessChecker;
import com.jizhang.service.vo.TransactionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class TransactionServiceImpl implements TransactionService {

    /** 交易类型 */
    private static final int TYPE_EXPENSE = 1;
    private static final int TYPE_INCOME = 2;
    private static final int TYPE_TRANSFER = 3;

    /** 余额方向：应用 / 回滚 */
    private static final int APPLY = 1;
    private static final int REVERT = -1;

    private final TransactionMapper transactionMapper;
    private final AccountMapper accountMapper;
    private final BookAccessChecker bookAccessChecker;

    public TransactionServiceImpl(TransactionMapper transactionMapper,
                                  AccountMapper accountMapper,
                                  BookAccessChecker bookAccessChecker) {
        this.transactionMapper = transactionMapper;
        this.accountMapper = accountMapper;
        this.bookAccessChecker = bookAccessChecker;
    }

    @Override
    public PageResult<TransactionVO> page(TransactionQueryRequest request) {
        bookAccessChecker.requireReadable(request.getBookId());

        Page<TransactionEntity> page = transactionMapper.selectPage(
                new Page<>(request.getPage(), request.getSize()),
                Wrappers.<TransactionEntity>lambdaQuery()
                        .eq(TransactionEntity::getBookId, request.getBookId())
                        .eq(request.getType() != null, TransactionEntity::getType, request.getType())
                        .eq(request.getAccountId() != null, TransactionEntity::getAccountId, request.getAccountId())
                        .eq(request.getCategoryId() != null, TransactionEntity::getCategoryId, request.getCategoryId())
                        .ge(request.getStartTime() != null, TransactionEntity::getTradeTime, request.getStartTime())
                        .le(request.getEndTime() != null, TransactionEntity::getTradeTime, request.getEndTime())
                        .ge(request.getMinAmount() != null, TransactionEntity::getAmount, request.getMinAmount())
                        .le(request.getMaxAmount() != null, TransactionEntity::getAmount, request.getMaxAmount())
                        .like(StrUtil.isNotBlank(request.getKeyword()), TransactionEntity::getRemark, request.getKeyword())
                        .orderByDesc(TransactionEntity::getTradeTime)
                        .orderByDesc(TransactionEntity::getId));

        List<TransactionVO> records = page.getRecords().stream().map(TransactionVO::from).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransactionVO create(TransactionSaveRequest request) {
        bookAccessChecker.requireWritable(request.getBookId());
        validate(request);

        // 幂等：客户端离线创建时自带 id，重复推送直接返回已存在记录
        if (request.getId() != null) {
            TransactionEntity existing = transactionMapper.selectById(request.getId());
            if (existing != null) {
                return TransactionVO.from(existing);
            }
        }

        TransactionEntity entity = toEntity(request);
        transactionMapper.insert(entity);
        applyBalance(entity, APPLY);
        return TransactionVO.from(entity);
    }

    @Override
    public TransactionVO detail(Long transactionId) {
        TransactionEntity entity = requireTransaction(transactionId);
        bookAccessChecker.requireReadable(entity.getBookId());
        return TransactionVO.from(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransactionVO update(Long transactionId, TransactionSaveRequest request) {
        TransactionEntity old = requireTransaction(transactionId);
        bookAccessChecker.requireWritable(old.getBookId());
        validate(request);

        // 先回滚旧记录对余额的影响，再应用新记录，避免金额/账户变更后余额错乱
        applyBalance(old, REVERT);

        TransactionEntity entity = toEntity(request);
        entity.setId(old.getId());
        entity.setBookId(old.getBookId());
        entity.setVersion(old.getVersion() == null ? 0 : old.getVersion() + 1);
        transactionMapper.updateById(entity);

        // toAccountId / categoryId 可能在修改中由有值变为 null，而 updateById 默认忽略 null 字段，
        // 因此需要在需要置空时补一次显式 update
        if (entity.getToAccountId() == null || entity.getCategoryId() == null) {
            transactionMapper.update(null, Wrappers.<TransactionEntity>lambdaUpdate()
                    .eq(TransactionEntity::getId, entity.getId())
                    .set(entity.getToAccountId() == null, TransactionEntity::getToAccountId, null)
                    .set(entity.getCategoryId() == null, TransactionEntity::getCategoryId, null));
        }
        applyBalance(entity, APPLY);

        return TransactionVO.from(transactionMapper.selectById(transactionId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long transactionId) {
        TransactionEntity entity = requireTransaction(transactionId);
        bookAccessChecker.requireWritable(entity.getBookId());
        applyBalance(entity, REVERT);
        transactionMapper.deleteById(transactionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<TransactionVO> batchCreate(List<TransactionSaveRequest> requests) {
        if (CollUtil.isEmpty(requests)) {
            return List.of();
        }
        List<TransactionVO> result = new ArrayList<>(requests.size());
        for (TransactionSaveRequest request : requests) {
            result.add(create(request));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> transactionIds) {
        if (CollUtil.isEmpty(transactionIds)) {
            return;
        }
        for (Long id : transactionIds) {
            TransactionEntity entity = id == null ? null : transactionMapper.selectById(id);
            if (entity == null) {
                // 记录不存在（可能已被其他端删除），跳过即可，保证同步的幂等性
                continue;
            }
            bookAccessChecker.requireWritable(entity.getBookId());
            applyBalance(entity, REVERT);
            transactionMapper.deleteById(entity.getId());
        }
    }

    /* ---------------- 内部方法 ---------------- */

    private TransactionEntity toEntity(TransactionSaveRequest request) {
        boolean transfer = Objects.equals(request.getType(), TYPE_TRANSFER);
        TransactionEntity entity = new TransactionEntity();
        entity.setId(request.getId());
        entity.setBookId(request.getBookId());
        entity.setType(request.getType());
        entity.setAmount(request.getAmount());
        entity.setAccountId(request.getAccountId());
        entity.setToAccountId(transfer ? request.getToAccountId() : null);
        entity.setCategoryId(transfer ? null : request.getCategoryId());
        entity.setTradeTime(request.getTradeTime());
        entity.setRemark(request.getRemark());
        entity.setImages(request.getImages());
        entity.setTags(request.getTags());
        entity.setLongitude(request.getLongitude());
        entity.setLatitude(request.getLatitude());
        entity.setSource(request.getSource() == null ? 1 : request.getSource());
        entity.setVersion(0);
        return entity;
    }

    /**
     * 按交易类型调整账户余额。
     *
     * @param sign {@link #APPLY} 应用；{@link #REVERT} 回滚
     */
    private void applyBalance(TransactionEntity entity, int sign) {
        if (entity.getType() == null || entity.getAmount() == null) {
            return;
        }
        long delta = entity.getAmount() * sign;
        switch (entity.getType()) {
            case TYPE_EXPENSE -> accountMapper.addBalance(entity.getAccountId(), -delta);
            case TYPE_INCOME -> accountMapper.addBalance(entity.getAccountId(), delta);
            case TYPE_TRANSFER -> {
                accountMapper.addBalance(entity.getAccountId(), -delta);
                if (entity.getToAccountId() != null) {
                    accountMapper.addBalance(entity.getToAccountId(), delta);
                }
            }
            default -> log.warn("未知交易类型: {}", entity.getType());
        }
    }

    private void validate(TransactionSaveRequest request) {
        if (request.getAmount() == null || request.getAmount() <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "金额必须大于 0");
        }
        if (Objects.equals(request.getType(), TYPE_TRANSFER)) {
            if (request.getToAccountId() == null) {
                throw new BizException(ResultCode.PARAM_ERROR, "转账必须指定转入账户");
            }
            if (Objects.equals(request.getToAccountId(), request.getAccountId())) {
                throw new BizException(ResultCode.PARAM_ERROR, "转出与转入账户不能相同");
            }
        }
        requireAccountInBook(request.getAccountId(), request.getBookId());
        if (request.getToAccountId() != null) {
            requireAccountInBook(request.getToAccountId(), request.getBookId());
        }
    }

    private void requireAccountInBook(Long accountId, Long bookId) {
        AccountEntity account = accountId == null ? null : accountMapper.selectById(accountId);
        if (account == null || !Objects.equals(account.getBookId(), bookId)) {
            throw new BizException(ResultCode.ACCOUNT_NOT_FOUND);
        }
    }

    private TransactionEntity requireTransaction(Long transactionId) {
        TransactionEntity entity = transactionId == null ? null : transactionMapper.selectById(transactionId);
        if (entity == null) {
            throw new BizException(ResultCode.TRANSACTION_NOT_FOUND);
        }
        return entity;
    }
}
