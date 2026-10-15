package com.jizhang.service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 预算保存请求。同一账本 + 分类 + 周期只保留一条，重复提交视为更新。
 */
@Data
public class BudgetSaveRequest {

    @NotNull(message = "账本不能为空")
    private Long bookId;

    /** 0 或 null 表示账本总预算 */
    private Long categoryId;

    @NotBlank(message = "预算周期不能为空")
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "预算周期格式应为 yyyy-MM")
    private String period;

    @NotNull(message = "预算金额不能为空")
    @Min(value = 1, message = "预算金额必须大于 0")
    private Long amount;

    private String remark;
}
