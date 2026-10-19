package com.jizhang.app.data.repository

import com.jizhang.app.data.local.entity.LocalRecurringExpense
import com.jizhang.app.data.remote.dto.BudgetDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「建议总预算」的去重规则：房租既设了分类预算、又登记了固定支出时，不能算两遍。
 */
class UncoveredFixedTest {

    private val rentCategoryId = 11L

    private fun rentItem(categoryId: Long, amount: Long) = FixedReservationItem(
        expense = LocalRecurringExpense(
            id = 1L,
            bookId = 1L,
            name = "房租",
            categoryId = categoryId,
            amount = amount
        ),
        nextDate = java.time.LocalDate.of(2026, 10, 20),
        count = 1,
        amount = amount
    )

    private fun reservationOf(vararg items: FixedReservationItem) =
        FixedReservation(total = items.sumOf { it.amount }, items = items.toList())

    private fun budget(categoryId: Long, amount: Long) =
        BudgetDto(categoryId = categoryId, categoryName = "房租", amount = amount)

    @Test
    fun `分类预算已覆盖时不重复计入`() {
        val uncovered = uncoveredFixedTotal(
            budgetItems = listOf(budget(rentCategoryId, 76_000L)),
            reservation = reservationOf(rentItem(rentCategoryId, 76_000L))
        )
        assertEquals(0L, uncovered)
    }

    @Test
    fun `分类预算不足时只补差额`() {
        val uncovered = uncoveredFixedTotal(
            budgetItems = listOf(budget(rentCategoryId, 50_000L)),
            reservation = reservationOf(rentItem(rentCategoryId, 76_000L))
        )
        assertEquals(26_000L, uncovered)
    }

    @Test
    fun `固定支出没有分类时全额计入`() {
        val uncovered = uncoveredFixedTotal(
            budgetItems = listOf(budget(rentCategoryId, 76_000L)),
            reservation = reservationOf(rentItem(0L, 76_000L))
        )
        assertEquals(76_000L, uncovered)
    }

    @Test
    fun `没有分类预算时全额计入`() {
        val uncovered = uncoveredFixedTotal(
            budgetItems = emptyList(),
            reservation = reservationOf(rentItem(rentCategoryId, 76_000L))
        )
        assertEquals(76_000L, uncovered)
    }

    @Test
    fun `建议总预算等于分类合计加未覆盖部分`() {
        val items = listOf(budget(rentCategoryId, 76_000L), budget(12L, 120_000L))
        val uncovered = uncoveredFixedTotal(items, reservationOf(rentItem(rentCategoryId, 76_000L)))
        // 分类合计 1960 + 未覆盖 0
        assertEquals(196_000L, items.sumOf { it.amount } + uncovered)
    }
}
