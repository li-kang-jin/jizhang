package com.jizhang.service.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 预算总览：总预算执行情况 + 分类预算明细 + 超支提醒 + 每日预测。
 */
@Data
public class BudgetOverviewVO {

    /** 周期类型：1=每月 2=每周 3=每年 */
    private Integer cycle;

    /** 周期首日（含） */
    private LocalDate periodStart;

    /** 周期字符串（2026-10 / 2026-W42 / 2026） */
    private String period;

    /** 是否设置过总预算 */
    private Boolean hasBudget;

    /** 总预算记录 ID，未设置时为 null（客户端删除时需要） */
    private Long totalBudgetId;

    /** 预算金额（分），未设置时为 0 */
    private Long totalBudget;

    /** 已用金额（分）＝ 周期内支出合计 */
    private Long totalUsed;

    /** 剩余金额（分），超支时为负 */
    private Long remaining;

    /** 使用比例，可能大于 1 */
    private Double usedRatio;

    private Boolean exceeded;

    /** 周期内支出合计（分），未设总预算时也能看到 */
    private Long monthExpense;

    /** 周期总天数（字段名为历史兼容保留） */
    private Integer daysInMonth;

    /** 周期总天数 */
    private Integer daysTotal;

    /** 剩余天数（含今天） */
    private Integer daysLeft;

    /** 已过天数（含今天） */
    private Integer elapsedDays;

    /** 剩余额度摊到剩余天数，每天可花多少（分）；超支时为 0 */
    private Long dailyAvailable;

    /** 按预算算的日均建议值（分） */
    private Long suggestDaily;

    /** 今天已花（分），配合 dailyAvailable 看今天还能不能花 */
    private Long todayExpense;

    /** 日均消耗（分/天），周期未开始时为 null */
    private Double burnRate;

    /** 按当前消耗速度预测的期末支出（分），周期未开始时为 null */
    private Long forecastExpense;

    /** 预测状态：NO_BUDGET / ON_TRACK / AT_RISK（预测超支 10% 以上）/ OVER（已超支） */
    private String status;

    /** 分类预算明细（不含总预算） */
    private List<BudgetVO> items;

    /** 已超支的预算项 */
    private List<BudgetVO> overruns;
}
