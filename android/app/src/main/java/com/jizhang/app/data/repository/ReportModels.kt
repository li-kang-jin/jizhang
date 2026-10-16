package com.jizhang.app.data.repository

import com.jizhang.app.data.remote.dto.AccountAssetDto
import java.time.LocalDate

/**
 * 统计页的领域模型。
 * <p>
 * 与 `data/remote/dto` 里那些 DTO 的区别：这些是**本地聚合**算出来的结果（统计页不再请求服务端），
 * 字段按界面需要组织，不追求和服务端一一对应。放在 repository 层，UI 直接消费。
 */

/**
 * 区间总览。
 * <p>
 * 转账单独列字段：转账只是钱在账户之间挪，不应该算成「花掉」，
 * 但它的金额和笔数本身是用户关心的数据，所以既不计入收支，也不丢掉。
 */
data class ReportOverview(
    val periodLabel: String,
    val income: Long,
    val expense: Long,
    val balance: Long,
    val incomeCount: Long,
    val expenseCount: Long,
    val transferCount: Long,
    val transferAmount: Long,
    /** 有记账的天数（不含只有转账的天） */
    val activeDays: Long,
    /** 日均支出 / 日均收入（分），按「已过天数」摊 */
    val avgExpensePerDay: Long,
    val avgIncomePerDay: Long,
    /** 环比：与上一期（昨天 / 上月 / 去年）比 */
    val expenseChainRatio: Double?,
    val incomeChainRatio: Double?,
    /** 同比：与去年同期比（年粒度时等于环比） */
    val expenseYoYRatio: Double?,
    val incomeYoYRatio: Double?
) {
    val txCount: Long get() = incomeCount + expenseCount
}

/** 分类构成里的一项；父分类归并时 children 带着子分类明细 */
data class CategorySlice(
    val categoryId: Long?,
    val name: String,
    val icon: String?,
    val amount: Long,
    val ratio: Float,
    val children: List<CategorySlice> = emptyList()
)

data class CategoryBreakdown(val total: Long, val items: List<CategorySlice>)

/** 排行项：账户排行与标签排行共用（key 是账户 ID 或标签名的哈希） */
data class RankItem(
    val key: Long,
    val name: String,
    val icon: String?,
    val amount: Long,
    val ratio: Float,
    val count: Long = 0
)

/** 某一天的收支（日历热力图 / 极值日） */
data class DayAmount(val date: LocalDate, val income: Long, val expense: Long)

/**
 * 某一天的当天明细（日历点开那一格时展示）。
 * <p>
 * 比 [DayAmount] 多出笔数与转账：日历格子只回答「花了多少」，点开之后用户想知道
 * 「几笔、有没有转账」，所以复用总览那套查询（口径完全一致，不会出现两处数字对不上）。
 */
data class DayDetail(
    val date: LocalDate,
    val income: Long,
    val expense: Long,
    val incomeCount: Long,
    val expenseCount: Long,
    val transferCount: Long,
    val transferAmount: Long
) {
    val balance: Long get() = income - expense

    /** 收支笔数（不含转账） */
    val txCount: Long get() = incomeCount + expenseCount
}

/** 记账习惯：把「记了多少、怎么记的」量化出来 */
data class HabitStats(
    val activeDays: Int,
    val txCount: Long,
    val avgTxPerDay: Double,
    val avgAmountPerDay: Long,
    val avgAmountPerTx: Long,
    /** 支出最多 / 最少（仅统计有支出的那天） */
    val maxDay: DayAmount?,
    val minDay: DayAmount?,
    /** 记账最勤 / 花钱最多的星期：0=周日 … 6=周六 */
    val busiestWeekday: Int?,
    /** 周一 … 周日 的支出（分），长度为 7 */
    val weekdayExpense: List<Long>,
    /** 当期首笔 / 末笔记账时刻（epoch millis） */
    val firstRecordTime: Long?,
    val lastRecordTime: Long?
)

/**
 * 资产总览。
 * <p>
 * 信用卡（type = 3）余额为负，代表欠款，单独算「负债」；净资产 = 资产 - 负债。
 * 只有 `includeInTotal = 1` 的账户参与合计 —— 只用来走账的中间账户不该算进身家。
 */
data class AssetSummary(
    val totalAssets: Long,
    val totalLiabilities: Long,
    val netAssets: Long,
    val accounts: List<AccountAssetDto>
) {
    companion object {
        /** 信用卡：余额为负表示欠款 */
        const val CREDIT_CARD_TYPE = 3

        val EMPTY = AssetSummary(0L, 0L, 0L, emptyList())
    }
}
