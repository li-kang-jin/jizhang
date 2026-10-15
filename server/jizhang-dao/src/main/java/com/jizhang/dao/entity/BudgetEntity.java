package com.jizhang.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 预算（按月度周期设置，可针对整个账本或某个分类）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_budget")
public class BudgetEntity extends BaseEntity {

    private Long bookId;

    /** 0 表示账本总预算，其余为对应分类的预算 */
    private Long categoryId;

    /** 预算周期，格式 yyyy-MM */
    private String period;

    /** 预算金额（分） */
    private Long amount;

    private String remark;

    private Integer version;
}
