package com.jizhang.service.vo;

import lombok.Data;

import java.util.List;

/**
 * 预算总览：总预算执行情况 + 分类预算明细 + 超支提醒。
 */
@Data
public class BudgetOverviewVO {

    private String period;

    /** 是否设置过总预算 */
    private Boolean hasBudget;

    /** 总预算记录 ID，未设置时为 null（客户端删除时需要） */
    private Long totalBudgetId;

    /** 预算金额（分），未设置时为 0 */
    private Long totalBudget;

    /** 已用金额（分）＝ 当月支出合计 */
    private Long totalUsed;

    /** 剩余金额（分），超支时为负 */
    private Long remaining;

    /** 使用比例，可能大于 1 */
    private Double usedRatio;

    private Boolean exceeded;

    /** 当月支出合计（分），未设总预算时也能看到 */
    private Long monthExpense;

    /** 当月总天数 */
    private Integer daysInMonth;

    /** 当月剩余天数（含今天） */
    private Integer daysLeft;

    /** 剩余额度摊到剩余天数，每天可花多少（分）；超支时为 0 */
    private Long dailyAvailable;

    /** 按预算算的日均建议值（分） */
    private Long suggestDaily;

    /** 分类预算明细（不含总预算） */
    private List<BudgetVO> items;

    /** 已超支的预算项 */
    private List<BudgetVO> overruns;
}
