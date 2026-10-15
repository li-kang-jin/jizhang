package com.jizhang.app.data.repository

import com.jizhang.app.data.local.dao.AccountDao
import com.jizhang.app.data.local.dao.BookMemberDao
import com.jizhang.app.data.local.dao.CategoryDao
import com.jizhang.app.data.local.dao.TransactionDao
import com.jizhang.app.data.remote.dto.AccountAssetDto
import com.jizhang.app.data.remote.dto.AssetReportDto
import com.jizhang.app.data.remote.dto.CategoryReportDto
import com.jizhang.app.data.remote.dto.CategoryStatDto
import com.jizhang.app.data.remote.dto.MemberReportDto
import com.jizhang.app.data.remote.dto.MemberStatDto
import com.jizhang.app.data.remote.dto.ReportOverviewDto
import com.jizhang.app.data.remote.dto.TrendItemDto
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 报表仓库。
 * <p>
 * 全部基于本地 Room 聚合计算，不再请求服务端 —— 这样断网 / 未登录也能看统计，
 * 而且登录后本地本身就是服务端数据的镜像，结果是一致的。
 */
@Singleton
class ReportRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val bookMemberDao: BookMemberDao
) {

    private companion object {
        /** 1 = 支出 */
        const val EXPENSE_TYPE = 1
    }

    suspend fun overview(bookId: Long, month: YearMonth = YearMonth.now()): ReportOverviewDto {
        val start = monthStart(month)
        val end = monthStart(month.plusMonths(1))
        val current = transactionDao.sumBetween(bookId, start, end)
        val previous = transactionDao.sumBetween(bookId, monthStart(month.minusMonths(1)), start)

        return ReportOverviewDto(
            month = month.toString(),
            income = current.income,
            expense = current.expense,
            balance = current.income - current.expense,
            incomeRatio = ratio(current.income, previous.income),
            expenseRatio = ratio(current.expense, previous.expense)
        )
    }

    suspend fun category(
        bookId: Long,
        type: Int,
        month: YearMonth = YearMonth.now()
    ): CategoryReportDto {
        val start = monthStart(month)
        val end = monthStart(month.plusMonths(1))

        val rows = transactionDao.sumByCategory(bookId, type, start, end)
        val categoryMap = categoryDao.listByBook(bookId).associateBy { it.id }
        val total = rows.sumOf { it.total }

        val items = rows.map { row ->
            val category = row.categoryId?.let { categoryMap[it] }
            CategoryStatDto(
                categoryId = row.categoryId,
                categoryName = category?.name ?: "未分类",
                icon = category?.icon,
                color = category?.color,
                amount = row.total,
                ratio = if (total == 0L) 0.0 else row.total.toDouble() / total
            )
        }
        return CategoryReportDto(total = total, items = items)
    }

    /** 近 N 天的收支趋势，缺失的日期会补 0，保证折线连续 */
    suspend fun trend(bookId: Long, days: Int = 30): List<TrendItemDto> {
        val today = LocalDate.now()
        return dailyTrend(bookId, today.minusDays((days - 1).toLong()), today)
    }

    /**
     * 指定月份的每日收支趋势。
     * <p>
     * 只到「今天」为止（当月）或整月（历史月），避免未来日期拉出一排 0 把图压扁。
     */
    suspend fun trendOfMonth(bookId: Long, month: YearMonth): List<TrendItemDto> {
        val first = month.atDay(1)
        val today = LocalDate.now()
        val last = when {
            month.isAfter(YearMonth.from(today)) -> first
            month == YearMonth.from(today) -> today
            else -> month.atEndOfMonth()
        }
        if (first.isAfter(last)) {
            return emptyList()
        }
        return dailyTrend(bookId, first, last)
    }

    private suspend fun dailyTrend(bookId: Long, startDate: LocalDate, endDate: LocalDate): List<TrendItemDto> {
        val zone = ZoneId.systemDefault()
        val start = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val rowMap = transactionDao.sumByDay(bookId, start, end).associateBy { it.period }

        val days = (endDate.toEpochDay() - startDate.toEpochDay()).toInt() + 1
        return (0 until days).map { offset ->
            val date = startDate.plusDays(offset.toLong()).toString()
            val row = rowMap[date]
            val income = row?.income ?: 0L
            val expense = row?.expense ?: 0L
            TrendItemDto(
                period = date,
                income = income,
                expense = expense,
                balance = income - expense
            )
        }
    }

    /** 该账本的支出分类，供预算设置选择范围 */
    suspend fun expenseCategories(bookId: Long): List<CategoryStatDto> =
        categoryDao.listByBookAndType(bookId, EXPENSE_TYPE).map {
            CategoryStatDto(
                categoryId = it.id,
                categoryName = it.name,
                icon = it.icon,
                color = it.color
            )
        }

    /**
     * 按成员统计收支（家庭账本「谁花了多少」）。
     * <p>
     * 与其它报表一致，全部由本地数据聚合：交易上的 createdBy + 缓存的成员名单，
     * 因此断网也能看。个人账本只有一条记录。
     */
    suspend fun memberStats(bookId: Long, month: YearMonth = YearMonth.now()): MemberReportDto {
        val start = monthStart(month)
        val end = monthStart(month.plusMonths(1))

        val rows = transactionDao.sumByMember(bookId, start, end)
        val memberMap = bookMemberDao.listByBook(bookId).associateBy { it.userId }

        val items = rows.map { row ->
            val member = row.createdBy?.let { memberMap[it] }
            MemberStatDto(
                userId = row.createdBy,
                username = member?.username,
                nickname = member?.nickname,
                alias = member?.alias,
                displayName = member?.label ?: if (row.createdBy == null) "未记录记账人" else "已退出的成员",
                income = row.income,
                expense = row.expense,
                balance = row.income - row.expense,
                count = row.txCount
            )
        }
        return MemberReportDto(
            totalIncome = items.sumOf { it.income },
            totalExpense = items.sumOf { it.expense },
            items = items
        )
    }

    suspend fun assets(bookId: Long): AssetReportDto {
        val accounts = accountDao.listWithBalance(bookId)
        val items = accounts.map {
            AccountAssetDto(
                accountId = it.id,
                name = it.name,
                type = it.type,
                icon = it.icon,
                balance = it.balance,
                includeInTotal = 1
            )
        }
        return AssetReportDto(
            totalAssets = items.sumOf { it.balance },
            accounts = items
        )
    }

    private fun monthStart(month: YearMonth): Long =
        month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun ratio(current: Long, previous: Long): Double? {
        if (previous == 0L) {
            return null
        }
        return (current - previous).toDouble() / previous
    }
}
