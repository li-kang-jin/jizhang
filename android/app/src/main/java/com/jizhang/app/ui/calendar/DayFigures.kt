package com.jizhang.app.ui.calendar

import com.jizhang.app.data.local.entity.LocalTransaction

/**
 * 某一天的账目小计。
 * <p>
 * 直接从「当天明细」里数出来，而不是再查一次聚合 SQL：列表和数字来自同一批数据，
 * 不会出现「上面写 4 笔、下面只列了 3 条」这种对不上的情况。
 * 转账单独记一笔（它不算收支，但用户会想知道）。
 */
internal data class DayFigures(
    val income: Long,
    val expense: Long,
    val incomeCount: Int,
    val expenseCount: Int,
    val transferCount: Int
) {
    val balance: Long get() = income - expense

    /** 收支笔数（不含转账） */
    val txCount: Int get() = incomeCount + expenseCount

    val isEmpty: Boolean get() = txCount == 0 && transferCount == 0
}

internal fun figuresOf(items: List<LocalTransaction>): DayFigures {
    var income = 0L
    var expense = 0L
    var incomeCount = 0
    var expenseCount = 0
    var transferCount = 0
    items.forEach { item ->
        when (item.type) {
            2 -> {
                income += item.amount
                incomeCount++
            }

            1 -> {
                expense += item.amount
                expenseCount++
            }

            else -> transferCount++
        }
    }
    return DayFigures(
        income = income,
        expense = expense,
        incomeCount = incomeCount,
        expenseCount = expenseCount,
        transferCount = transferCount
    )
}
