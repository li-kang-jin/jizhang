package com.jizhang.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 投资数据批量同步请求。
 * <p>
 * 一个账本的三张表一次提交，而不是每种资源一套增删改接口：它们是一体的
 * （持仓没了，它的估值和流水也没有意义），分批推会出现「推了流水但持仓还没到」的中间态。
 * <p>
 * **流水的语义是「覆盖」而不是「增量」**：服务端会先删掉本次 {@code positions} 里每个持仓
 * 的全部流水，再插入 {@code flows} 这份。所以客户端删掉一笔流水时不需要单独通知 ——
 * 只要把持仓标记为待推，下次推送它就不在列表里，服务端自然跟着消失。
 * 代价是客户端**必须**在推某个持仓时带上它的全部流水，不能只带变更的那几条。
 */
@Data
public class InvestSyncRequest {

    @NotNull(message = "账本不能为空")
    private Long bookId;

    @Valid
    private List<PositionDto> positions = new ArrayList<>();

    @Valid
    private List<ValuationDto> valuations = new ArrayList<>();

    @Valid
    private List<FlowDto> flows = new ArrayList<>();

    /** 要删除的持仓：服务端逻辑删持仓，并物理清掉它的估值与流水 */
    private List<Long> deletedPositionIds = new ArrayList<>();

    @Data
    public static class PositionDto {

        @NotNull(message = "持仓 ID 不能为空")
        private Long id;

        @NotNull(message = "所属账户不能为空")
        private Long accountId;

        @NotBlank(message = "名称不能为空")
        @Size(max = 64, message = "名称最长 64 个字符")
        private String name;

        @Size(max = 32, message = "代码最长 32 个字符")
        private String code;

        /** 1=股票 2=基金 3=债券 4=理财 5=其他；缺省按基金 */
        private Integer kind;

        /** 期初成本（分），不含转账自动累计 */
        private Long costFen;

        /** 份额 × 10000，可空 */
        private Long sharesScaled;

        /** 目标占比（%），0 表示未设 */
        private Integer targetPercent;

        private Integer sort;

        @Size(max = 255, message = "备注最长 255 个字符")
        private String note;

        /** 是否归档；缺省视为正常 */
        private Boolean archived;
    }

    @Data
    public static class ValuationDto {

        @NotNull(message = "估值 ID 不能为空")
        private Long id;

        @NotNull(message = "持仓 ID 不能为空")
        private Long positionId;

        @NotNull(message = "市值不能为空")
        private Long valueFen;

        /** 估值时点（epoch millis）；缺省按服务端当前时间 */
        private Long valuedAt;
    }

    @Data
    public static class FlowDto {

        @NotNull(message = "流水 ID 不能为空")
        private Long id;

        @NotNull(message = "持仓 ID 不能为空")
        private Long positionId;

        @NotNull(message = "账单 ID 不能为空")
        private Long transactionId;

        /** 1=买入 2=卖出；缺省按买入 */
        private Integer side;

        @NotNull(message = "金额不能为空")
        private Long amountFen;

        /** 发生时间（epoch millis） */
        private Long occurredAt;
    }
}
