package com.jizhang.app.ui.bill

import com.jizhang.app.data.local.entity.LocalTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 账单时间范围 */
enum class BillDateRange(val label: String) {
    THIS_MONTH("本月"),
    LAST_MONTH("上月"),
    LAST_7_DAYS("近 7 天"),
    LAST_30_DAYS("近 30 天"),
    ALL("全部时间"),
    CUSTOM("自定义");

    val isCustom: Boolean get() = this == CUSTOM
}

/** 账单排序方式 */
enum class BillSort(val label: String) {
    TIME_DESC("时间"),
    AMOUNT_DESC("金额")
}

/**
 * 从别的页面跳进账单页时携带的筛选预设。
 *
 * 典型场景：统计页点「餐饮」分类 / 某个账户 / 某个标签，直接跳到账单页看到对应的那批账单，
 * 并且把时间范围限定成统计页正在看的那个周期（否则点进去是「本月」，跟看到的数字对不上）。
 * <p>
 * 字段全部可空：只填需要的，其余保持账单页默认。
 */
data class BillPreset(
    val type: Int? = null,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val keyword: String? = null,
    /** 左闭右闭的毫秒区间（与统计页周期对齐） */
    val startMillis: Long? = null,
    val endMillis: Long? = null
)

/**
 * 账单筛选条件。
 *
 * 全部在内存里对已加载的账单做过滤 —— 单账本数据量小，没必要为筛选再改 Room 查询，
 * 好处是筛选结果能立刻联动「按日分组」与图表，且断网 / 未登录同样可用。
 */
data class BillFilter(
    val range: BillDateRange = BillDateRange.THIS_MONTH,
    /** 0 = 全部，1/2/3 = 支出 / 收入 / 转账 */
    val type: Int = 0,
    /** null = 全部账户 */
    val accountId: Long? = null,
    /** 空集合 = 全部分类 */
    val categoryIds: Set<Long> = emptySet(),
    /** 备注 / 标签关键字 */
    val keyword: String = "",
    val sort: BillSort = BillSort.TIME_DESC,
    /**
     * 自定义范围：**左闭右开**的 epoch 毫秒区间（与 [com.jizhang.app.data.repository.ReportPeriod]
     * 和 Room 查询同一个口径）。
     * <p>
     * 别往这里塞「最后一天的 23:59:59.999」：那样上界就成了第二天开头，
     * 会把第二天的账一起框进来。外部传的是闭区间时，先过 [withInclusiveRange] 换算。
     */
    val customStart: Long? = null,
    val customEnd: Long? = null
) {

    /** 是否偏离默认值（默认 = 本月 + 全部类型 + 全部账户 + 全部分类 + 无搜索 + 时间倒序） */
    val active: Boolean
        get() = range != BillDateRange.THIS_MONTH ||
            type != 0 ||
            accountId != null ||
            categoryIds.isNotEmpty() ||
            keyword.isNotBlank() ||
            sort != BillSort.TIME_DESC

    /** 时间区间（epoch millis），null 表示该侧不限 */
    fun timeBounds(
        now: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Pair<Long?, Long?> {
        val todayStart = now.atStartOfDay(zone).toInstant().toEpochMilli()
        return when (range) {
            BillDateRange.THIS_MONTH -> monthBounds(YearMonth.from(now), zone)
            BillDateRange.LAST_MONTH -> monthBounds(YearMonth.from(now).minusMonths(1), zone)
            BillDateRange.LAST_7_DAYS -> (todayStart - 6 * DAY_MILLIS) to (todayStart + DAY_MILLIS)
            BillDateRange.LAST_30_DAYS -> (todayStart - 29 * DAY_MILLIS) to (todayStart + DAY_MILLIS)
            BillDateRange.ALL -> null to null
            BillDateRange.CUSTOM -> customStart to customEnd
        }
    }

    /**
     * 用「闭区间」写时间范围（统计页 / 日历跳过来时带的就是这种：止日是最后一天的 23:59:59.999）。
     * 存进来仍按左闭右开，所以这里给止点加 1 毫秒。
     */
    fun withInclusiveRange(startMillis: Long?, endMillis: Long?): BillFilter = copy(
        range = if (startMillis != null && endMillis != null) BillDateRange.CUSTOM else range,
        customStart = startMillis,
        customEnd = endMillis?.plus(1)
    )

    /**
     * 用「起止两个自然日」写时间范围（日期选择器给的是两端的当天 0 点）。
     * 止日整天都要算进去，所以上界取止日次日 0 点。
     */
    fun withDayRange(startDayMillis: Long, endDayMillis: Long): BillFilter = withInclusiveRange(
        startMillis = minOf(startDayMillis, endDayMillis),
        endMillis = maxOf(startDayMillis, endDayMillis) + DAY_MILLIS - 1
    )

    fun matches(item: LocalTransaction, bounds: Pair<Long?, Long?>): Boolean {
        val (start, end) = bounds
        if (start != null && item.tradeTime < start) return false
        if (end != null && item.tradeTime >= end) return false
        if (type != 0 && item.type != type) return false
        // 转账的两个账户都算数，否则筛某个账户时会把转账漏掉
        if (accountId != null && item.accountId != accountId && item.toAccountId != accountId) return false
        if (categoryIds.isNotEmpty() && item.categoryId !in categoryIds) return false
        if (keyword.isNotBlank()) {
            val haystack = item.remark.orEmpty() + " " + item.tags.orEmpty()
            if (!haystack.contains(keyword.trim(), ignoreCase = true)) return false
        }
        return true
    }

    /** 时间区间的可读文案（自定义范围用）；起止同一天时只显示一个日期 */
    fun rangeLabel(): String {
        if (!range.isCustom) return range.label
        val fmt = java.time.format.DateTimeFormatter.ofPattern("M月d日")
        val start = customStart?.let { millisToLocalDate(it) }
        // customEnd 是开区间上界，减 1 毫秒才是用户眼里的「止日」
        val end = customEnd?.let { millisToLocalDate(it - 1) }
        return when {
            start != null && end != null && start == end -> start.format(fmt)
            else -> (start?.format(fmt) ?: "?") + " - " + (end?.format(fmt) ?: "?")
        }
    }

    private fun millisToLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000

        fun monthBounds(month: YearMonth, zone: ZoneId): Pair<Long, Long> {
            val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return start to end
        }
    }
}

/** 筛选结果汇总 */
data class BillSummary(
    val count: Int = 0,
    val income: Long = 0L,
    val expense: Long = 0L
) {
    val balance: Long get() = income - expense
}

/** 按日分组的账单：组头显示当天收支小计，组内是该日账单 */
data class BillDayGroup(
    val date: LocalDate,
    val income: Long,
    val expense: Long,
    val items: List<LocalTransaction>
)

data class BillChartDay(val date: LocalDate, val amount: Long)

data class BillCategorySlice(
    val categoryId: Long?,
    val name: String,
    val icon: String?,
    val amount: Long,
    val ratio: Float
)

/**
 * 账单页的迷你图表：直接由「筛选后的账单」算出来，
 * 所以改筛选条件时图会跟着变（这是它比统计页更实用的地方）。
 */
data class BillChart(
    /** 1 = 支出分布，2 = 收入分布（跟随类型筛选） */
    val type: Int = 1,
    val days: List<BillChartDay> = emptyList(),
    val topCategories: List<BillCategorySlice> = emptyList(),
    val total: Long = 0L
)
