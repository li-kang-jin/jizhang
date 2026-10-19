package com.jizhang.app.ui.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

/**
 * 日历页的「页码 ↔ 年月」换算。
 * <p>
 * 用**绝对页码**（以 1970-01 为第 0 页）而不是「相对第几页」：月份在时间轴上本来就是等距的，
 * 绝对页码让 `page ± 1` 天然就是「上个月 / 下个月」，跨年也不用特判（2025-12 → 2026-01 就是 +1）。
 * <p>
 * 换算逻辑单独放在这里，是为了能脱离 Compose / Room 直接写单测 —— 翻月、跨年、边界这些
 * 最容易写错又最难在界面上看出来。
 */
internal object CalendarMonths {

    private val BASE: YearMonth = YearMonth.of(1970, 1)

    /** 没有更早账单时默认能往回翻的月数：一年足够回看，也不至于让用户一路滑到 1970 年 */
    const val DEFAULT_BACK_MONTHS = 12

    fun pageOf(month: YearMonth): Int =
        (month.year - BASE.year) * 12 + (month.monthValue - BASE.monthValue)

    fun monthOf(page: Int): YearMonth = BASE.plusMonths(page.toLong())

    /** 时间戳落在哪个月（按本机时区，与聚合查询的 `localtime` 口径一致） */
    fun monthOfMillis(millis: Long): YearMonth =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).let { YearMonth.from(it) }

    /**
     * 可分页范围（闭区间，单位是页码）。
     * <p>
     * 上界**固定为当前月**：未来没有账，允许往后滑只会让人看花眼（与统计页「不允许翻到未来」一致）。
     * 下界取「最早一笔账单所在月」与「当前月往前 [DEFAULT_BACK_MONTHS] 个月」里更早的那个：
     * - 有更早的账 → 能一直翻到第一笔；
     * - 账都是近期的 → 也留出一年的空月份，别把回看的路堵死。
     */
    fun range(today: YearMonth, earliest: YearMonth?): IntRange {
        val last = pageOf(today)
        val byDefault = last - DEFAULT_BACK_MONTHS
        val first = earliest?.let { minOf(byDefault, pageOf(it)) } ?: byDefault
        return first..last
    }

    /** 从 [from] 出发、距离 [target] 有几页（用来淘汰离当前月最远的缓存） */
    fun distance(from: YearMonth, target: YearMonth): Int = abs(pageOf(from) - pageOf(target))

    /** 某个日期所在月是否就是这一页对应的月 */
    fun isInMonth(date: LocalDate, month: YearMonth): Boolean = YearMonth.from(date) == month
}
