package com.jizhang.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
public class TransactionSaveRequest {

    /** 客户端生成的 ID（离线创建时提供），新增时可为空 */
    private Long id;

    @NotNull(message = "账本不能为空")
    private Long bookId;

    /** 1=支出 2=收入 3=转账 */
    @NotNull(message = "交易类型不能为空")
    @Min(1)
    @Max(3)
    private Integer type;

    /** 金额（分），恒为正数 */
    @NotNull(message = "金额不能为空")
    @Positive(message = "金额必须大于 0")
    private Long amount;

    @NotNull(message = "账户不能为空")
    private Long accountId;

    /** 转入账户，type=3 时必填 */
    private Long toAccountId;

    /** 分类，type=3 时忽略 */
    private Long categoryId;

    @NotNull(message = "交易时间不能为空")
    private OffsetDateTime tradeTime;

    @Size(max = 255, message = "备注最长 255 个字符")
    private String remark;

    private List<String> images;

    private List<String> tags;

    private BigDecimal longitude;

    private BigDecimal latitude;

    /** 1=手动 2=导入 3=周期自动 */
    private Integer source = 1;

    /**
     * 客户端本地的最后修改时间，仅多端同步（/app/sync/push）场景使用，用于冲突判定。
     * 普通新增 / 修改接口会忽略该字段。
     */
    private OffsetDateTime updatedAt;
}
