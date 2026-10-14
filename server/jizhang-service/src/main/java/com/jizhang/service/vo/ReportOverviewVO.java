package com.jizhang.service.vo;

import lombok.Data;

/**
 * 账本收支总览（金额单位：分）。
 */
@Data
public class ReportOverviewVO {

    /** 统计月份 yyyy-MM */
    private String month;

    private long income;

    private long expense;

    /** 结余 = 收入 - 支出 */
    private long balance;

    /** 收入环比（与上月相比），0.12 表示增长 12%；上月无数据时为 null */
    private Double incomeRatio;

    /** 支出环比 */
    private Double expenseRatio;
}
