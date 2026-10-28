package com.jizhang.service.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.jizhang.dao.entity.InvestFlowEntity;
import com.jizhang.dao.entity.InvestPositionEntity;
import com.jizhang.dao.entity.InvestValuationEntity;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个账本的全部投资数据。
 * <p>
 * 一次返回三张表，而不是三个接口：它们总是一起用（持仓列表要配最新市值看盈亏），
 * 拆开会让客户端为了一次渲染发三次请求，还得处理"其中一个失败"的中间态。
 * <p>
 * 不做增量：一个账本几十个持仓、一年几百条估值，全量几百 KB，
 * 为它维护一套游标和分页不值得。
 */
@Data
public class InvestDataVO {

    private List<PositionVO> positions = new ArrayList<>();

    private List<ValuationVO> valuations = new ArrayList<>();

    private List<FlowVO> flows = new ArrayList<>();

    @Data
    public static class PositionVO {

        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long bookId;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long accountId;

        private String name;

        private String code;

        private Integer kind;

        private Long costFen;

        private Long sharesScaled;

        private Integer targetPercent;

        private Integer sort;

        private String note;

        private Boolean archived;

        public static PositionVO from(InvestPositionEntity entity) {
            PositionVO vo = new PositionVO();
            vo.setId(entity.getId());
            vo.setBookId(entity.getBookId());
            vo.setAccountId(entity.getAccountId());
            vo.setName(entity.getName());
            vo.setCode(entity.getCode());
            vo.setKind(entity.getKind());
            vo.setCostFen(entity.getCostFen());
            vo.setSharesScaled(entity.getSharesScaled());
            vo.setTargetPercent(entity.getTargetPercent());
            vo.setSort(entity.getSort());
            vo.setNote(entity.getNote());
            vo.setArchived(entity.getArchived() != null && entity.getArchived() != 0);
            return vo;
        }
    }

    @Data
    public static class ValuationVO {

        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long bookId;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long positionId;

        private Long valueFen;

        /** epoch millis */
        private Long valuedAt;

        public static ValuationVO from(InvestValuationEntity entity) {
            ValuationVO vo = new ValuationVO();
            vo.setId(entity.getId());
            vo.setBookId(entity.getBookId());
            vo.setPositionId(entity.getPositionId());
            vo.setValueFen(entity.getValueFen());
            vo.setValuedAt(entity.getValuedAt());
            return vo;
        }
    }

    @Data
    public static class FlowVO {

        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long bookId;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long positionId;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long transactionId;

        private Integer side;

        private Long amountFen;

        /** epoch millis */
        private Long occurredAt;

        public static FlowVO from(InvestFlowEntity entity) {
            FlowVO vo = new FlowVO();
            vo.setId(entity.getId());
            vo.setBookId(entity.getBookId());
            vo.setPositionId(entity.getPositionId());
            vo.setTransactionId(entity.getTransactionId());
            vo.setSide(entity.getSide());
            vo.setAmountFen(entity.getAmountFen());
            vo.setOccurredAt(entity.getOccurredAt());
            return vo;
        }
    }
}
