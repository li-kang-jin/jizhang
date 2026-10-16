package com.jizhang.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * 保存预算。
 * <p>
 * 周期用「类型 + 首日」表达（与服务端 `BudgetPeriod` 一致）：
 * 字符串周期（`2026-W42`）需要两端各自解析，ISO 周还有跨年归属歧义。
 */
@Serializable
data class BudgetSaveDto(
    val bookId: Long,
    /** 0 表示账本总预算 */
    val categoryId: Long = 0L,
    /** 周期类型：1=每月 2=每周 3=每年 */
    val cycle: Int = 1,
    /** 周期首日，yyyy-MM-dd */
    val periodStart: String,
    /** 金额（分） */
    val amount: Long,
    val remark: String? = null
)

@Serializable
data class BudgetDto(
    val id: Long = 0,
    val categoryId: Long = 0,
    val categoryName: String = "总预算",
    val categoryIcon: String? = null,
    /** 周期类型：1=每月 2=每周 3=每年 */
    val cycle: Int = 1,
    /** 周期首日，yyyy-MM-dd */
    val periodStart: String? = null,
    /** 周期字符串（2026-10 / 2026-W42 / 2026），仅用于展示 */
    val period: String = "",
    val amount: Long = 0,
    val used: Long = 0,
    val remaining: Long = 0,
    val usedRatio: Double? = null,
    val exceeded: Boolean = false,
    val remark: String? = null
)

@Serializable
data class BudgetOverviewDto(
    /** 周期类型：1=每月 2=每周 3=每年 */
    val cycle: Int = 1,
    /** 周期首日，yyyy-MM-dd */
    val periodStart: String? = null,
    val period: String = "",
    val hasBudget: Boolean = false,
    /** 总预算记录 ID，删除时需要 */
    val totalBudgetId: Long? = null,
    val totalBudget: Long = 0,
    val totalUsed: Long = 0,
    val remaining: Long = 0,
    val usedRatio: Double? = null,
    val exceeded: Boolean = false,
    /** 本周期支出合计，未设总预算时也可见 */
    val monthExpense: Long = 0,
    /** 周期总天数（字段名为历史兼容保留） */
    val daysInMonth: Int = 30,
    /** 周期总天数 */
    val daysTotal: Int = 30,
    val daysLeft: Int = 0,
    /** 已过天数（含今天） */
    val elapsedDays: Int = 0,
    /** 剩余额度摊到剩余天数 */
    val dailyAvailable: Long = 0,
    /** 按预算算的日均建议值 */
    val suggestDaily: Long = 0,
    /** 今天已花（分） */
    val todayExpense: Long = 0,
    /** 日均消耗（分/天），周期未开始为 null */
    val burnRate: Double? = null,
    /** 按当前速度预测的期末支出（分），周期未开始为 null */
    val forecastExpense: Long? = null,
    /**
     * 本期还没到期的固定支出预留（分）。
     * <p>
     * 固定支出目前只有客户端有（见 `local_recurring_expense`），服务端返回的这份总览里恒为 0。
     */
    val fixedReserved: Long = 0,
    /** 扣掉固定支出预留后还能自由支配的余额（分） */
    val flexibleRemaining: Long = 0,
    /** NO_BUDGET / ON_TRACK / AT_RISK / OVER */
    val status: String = STATUS_NO_BUDGET,
    val items: List<BudgetDto> = emptyList(),
    val overruns: List<BudgetDto> = emptyList()
) {
    companion object {
        const val STATUS_NO_BUDGET = "NO_BUDGET"
        const val STATUS_ON_TRACK = "ON_TRACK"
        const val STATUS_AT_RISK = "AT_RISK"
        const val STATUS_OVER = "OVER"
    }
}
