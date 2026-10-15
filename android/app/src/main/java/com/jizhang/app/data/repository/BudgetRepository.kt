package com.jizhang.app.data.repository

import com.jizhang.app.data.local.dao.BudgetDao
import com.jizhang.app.data.local.dao.CategoryDao
import com.jizhang.app.data.local.dao.TransactionDao
import com.jizhang.app.data.local.entity.LocalBudget
import com.jizhang.app.data.remote.dto.BudgetDto
import com.jizhang.app.data.remote.dto.BudgetOverviewDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 预算仓库。
 * <p>
 * 与报表一样基于本地 Room 计算：额度来自 local_budget，已用金额由交易流水实时聚合。
 * 这样预算进度在离线状态下也能正常显示，且不会出现「额度更新了但进度还是旧值」的不一致。
 *
 * 周期由 [BudgetPeriods] 统一处理，支持每月 / 每周 / 每年。
 */
@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao
) {

    /** 账本总预算对应的分类 ID */
    private val totalCategoryId = 0L

    /** 预算只统计支出 */
    private val typeExpense = 1

    fun observeOverview(
        bookId: Long,
        period: String = BudgetPeriods.of(BudgetCycle.MONTH)
    ): Flow<BudgetOverviewDto> =
        budgetDao.observeByPeriod(bookId, period).map { buildOverview(bookId, period, it) }

    suspend fun overview(
        bookId: Long,
        period: String = BudgetPeriods.of(BudgetCycle.MONTH)
    ): BudgetOverviewDto =
        buildOverview(bookId, period, budgetDao.listByPeriod(bookId, period))

    /**
     * 保存预算。同一账本 + 分类 + 周期只保留一条，重复保存视为修改额度。
     * 本地新建的 ID 从 maxId + 1 递增，同步成功后由服务端 ID 覆盖。
     */
    suspend fun save(
        bookId: Long,
        categoryId: Long,
        period: String,
        amount: Long,
        remark: String? = null
    ) {
        val exist = budgetDao.find(bookId, categoryId, period)
        val id = exist?.id ?: (budgetDao.maxId() + 1)

        budgetDao.upsert(
            LocalBudget(
                id = id,
                bookId = bookId,
                categoryId = categoryId,
                period = period,
                amount = amount,
                remark = remark,
                pendingSync = true,
                clientUpdatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun delete(budgetId: Long) {
        budgetDao.deleteById(budgetId)
    }

    suspend fun listByPeriod(bookId: Long, period: String): List<LocalBudget> =
        budgetDao.listByPeriod(bookId, period)

    /** 某分类在指定周期的额度（分），未设置返回 null */
    suspend fun amountOf(bookId: Long, categoryId: Long, period: String): Long? =
        budgetDao.find(bookId, categoryId, period)?.amount

    /**
     * 新周期还没有任何预算时，自动沿用上一周期的额度。
     * <p>
     * 用户上个月设了预算，这个月一进来又是空的会很烦躁；复制一份可以马上接着用，
     * 复制出来的记录同样标记待同步。
     *
     * @return 复制的条数，0 表示没有可继承的内容
     */
    suspend fun inheritPrevious(bookId: Long, period: String): Int {
        if (budgetDao.listByPeriod(bookId, period).isNotEmpty()) {
            return 0
        }
        val source = budgetDao.listByPeriod(bookId, BudgetPeriods.previous(period))
        if (source.isEmpty()) {
            return 0
        }

        var id = budgetDao.maxId()
        val now = System.currentTimeMillis()
        val copies = source.map { budget ->
            LocalBudget(
                id = ++id,
                bookId = bookId,
                categoryId = budget.categoryId,
                period = period,
                amount = budget.amount,
                remark = budget.remark,
                pendingSync = true,
                clientUpdatedAt = now
            )
        }
        budgetDao.upsertAll(copies)
        return copies.size
    }

    /* ---------------- 内部方法 ---------------- */

    private suspend fun buildOverview(
        bookId: Long,
        period: String,
        budgets: List<LocalBudget>
    ): BudgetOverviewDto {
        val start = BudgetPeriods.startMillis(period)
        val end = BudgetPeriods.endMillis(period)
        val daysTotal = BudgetPeriods.daysTotal(period)
        val daysLeft = BudgetPeriods.daysLeft(period, LocalDate.now())

        val sum = transactionDao.sumBetween(bookId, start, end)
        val periodExpense = sum.expense

        val categoryMap = categoryDao.listByBook(bookId).associateBy { it.id }
        val usedMap = transactionDao.sumByCategory(bookId, typeExpense, start, end)
            .filter { it.categoryId != null }
            .associate { it.categoryId!! to it.total }

        val totalBudget = budgets.firstOrNull { it.categoryId == totalCategoryId }
        val totalAmount = totalBudget?.amount ?: 0L
        val remaining = totalAmount - periodExpense

        val items = budgets
            .filter { it.categoryId != totalCategoryId }
            .map { budget ->
                val used = usedMap[budget.categoryId] ?: 0L
                toDto(
                    budget,
                    categoryMap[budget.categoryId]?.name,
                    categoryMap[budget.categoryId]?.icon,
                    used
                )
            }

        return BudgetOverviewDto(
            period = period,
            cycle = BudgetPeriods.cycleOf(period).name,
            hasBudget = totalBudget != null,
            totalBudgetId = totalBudget?.id,
            totalBudget = totalAmount,
            totalUsed = periodExpense,
            remaining = if (totalAmount == 0L) 0L else remaining,
            usedRatio = if (totalAmount == 0L) null else periodExpense.toDouble() / totalAmount,
            exceeded = totalAmount > 0 && periodExpense > totalAmount,
            monthExpense = periodExpense,
            daysInMonth = daysTotal,
            daysLeft = daysLeft,
            dailyAvailable = if (remaining > 0 && daysLeft > 0) remaining / daysLeft else 0L,
            suggestDaily = if (totalAmount == 0L) 0L else totalAmount / daysTotal,
            items = items,
            overruns = items.filter { it.exceeded }
        )
    }

    private fun toDto(budget: LocalBudget, name: String?, icon: String?, used: Long): BudgetDto {
        val amount = budget.amount
        return BudgetDto(
            id = budget.id,
            categoryId = budget.categoryId,
            categoryName = name ?: "未分类",
            categoryIcon = icon,
            period = budget.period,
            amount = amount,
            used = used,
            remaining = amount - used,
            usedRatio = if (amount == 0L) null else used.toDouble() / amount,
            exceeded = amount > 0 && used > amount,
            remark = budget.remark
        )
    }
}
