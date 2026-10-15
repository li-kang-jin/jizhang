package com.jizhang.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class BudgetSaveDto(
    val bookId: Long,
    /** 0 表示账本总预算 */
    val categoryId: Long = 0L,
    /** yyyy-MM */
    val period: String,
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
    val period: String = "",
    /** 周期类型：MONTH / WEEK / YEAR（默认按月，兼容历史数据） */
    val cycle: String = "MONTH",
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
    /** 周期总天数（按月的历史命名为 daysInMonth，周/年周期下语义为周期长度） */
    val daysInMonth: Int = 30,
    val daysLeft: Int = 0,
    /** 剩余额度摊到剩余天数 */
    val dailyAvailable: Long = 0,
    val suggestDaily: Long = 0,
    val items: List<BudgetDto> = emptyList(),
    val overruns: List<BudgetDto> = emptyList()
)
