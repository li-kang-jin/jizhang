package com.jizhang.service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 预算保存请求。同一账本 + 分类 + 周期只保留一条，重复提交视为更新。
 * <p>
 * 周期用 {@code cycle + periodStart} 表达；{@code period}（yyyy-MM）只为兼容旧客户端保留，
 * 优先级低于 {@code periodStart}。
 */
@Data
public class BudgetSaveRequest {

    @NotNull(message = "账本不能为空")
    private Long bookId;

    /** 0 或 null 表示账本总预算 */
    private Long categoryId;

    /** 周期类型：1=每月 2=每周 3=每年；缺省按每月处理 */
    private Integer cycle;

    /** 周期首日，格式 yyyy-MM-dd（会归一到该周期的首日） */
    private String periodStart;

    /** 兼容旧客户端：月份，格式 yyyy-MM，仅表示月周期 */
    private String period;

    @NotNull(message = "预算金额不能为空")
    @Min(value = 1, message = "预算金额必须大于 0")
    private Long amount;

    private String remark;
}
