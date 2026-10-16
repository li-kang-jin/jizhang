package com.jizhang.service.support;

import com.jizhang.common.api.ResultCode;
import com.jizhang.common.exception.BizException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;

/**
 * 预算周期。
 * <p>
 * 用「周期类型 + 周期首日」表达，而不是 {@code yyyy-MM} / {@code 2026-W42} / {@code 2026} 这类字符串：
 * 字符串格式需要按周期分别解析，ISO 周还有跨年的归属歧义（2026-01-01 属于 2025 年第 53 周）。
 * 区间是左闭右开的 {@code [start, endExclusive)}，与交易流水的时间区间查询口径一致。
 */
public record BudgetPeriod(int cycle, LocalDate start, LocalDate endExclusive) {

    /** 每月 */
    public static final int CYCLE_MONTH = 1;

    /** 每周（ISO 周，周一为周期首日） */
    public static final int CYCLE_WEEK = 2;

    /** 每年 */
    public static final int CYCLE_YEAR = 3;

    private static final WeekFields ISO = WeekFields.ISO;

    /** 周期总天数 */
    public int daysTotal() {
        return (int) ChronoUnit.DAYS.between(start, endExclusive);
    }

    /**
     * 周期字符串，与客户端的 {@code BudgetPeriods.of()} 格式一致：
     * {@code 2026-10} / {@code 2026-W42} / {@code 2026}。
     */
    public String label() {
        return switch (cycle) {
            case CYCLE_WEEK -> String.format("%04d-W%02d",
                    start.get(ISO.weekBasedYear()), start.get(ISO.weekOfWeekBasedYear()));
            case CYCLE_YEAR -> String.valueOf(start.getYear());
            default -> String.format("%04d-%02d", start.getYear(), start.getMonthValue());
        };
    }

    /** 把周期内的任意一天归一到周期首日 */
    public static BudgetPeriod of(int cycle, LocalDate anyDayInPeriod) {
        LocalDate firstDay = switch (cycle) {
            case CYCLE_WEEK -> anyDayInPeriod.with(ISO.dayOfWeek(), 1L);
            case CYCLE_YEAR -> anyDayInPeriod.withDayOfYear(1);
            default -> anyDayInPeriod.withDayOfMonth(1);
        };
        return new BudgetPeriod(cycle, firstDay, plus(cycle, firstDay));
    }

    /**
     * 解析请求里的周期参数。
     * <p>
     * 优先级：{@code cycle + periodStart}（新客户端）&gt; {@code period}（旧客户端的 yyyy-MM）&gt; 当前月。
     */
    public static BudgetPeriod resolve(Integer cycle, String periodStart, String period) {
        int resolvedCycle = isValidCycle(cycle) ? cycle : CYCLE_MONTH;

        if (periodStart != null && !periodStart.isBlank()) {
            return of(resolvedCycle, parseDate(periodStart));
        }
        if (period != null && !period.isBlank()) {
            // 兼容老接口：period 只可能是月
            return of(CYCLE_MONTH, parseMonth(period).atDay(1));
        }
        return of(resolvedCycle, LocalDate.now(ZoneOffset.UTC));
    }

    public static boolean isValidCycle(Integer cycle) {
        return cycle != null && cycle >= CYCLE_MONTH && cycle <= CYCLE_YEAR;
    }

    private static LocalDate plus(int cycle, LocalDate date) {
        return switch (cycle) {
            case CYCLE_WEEK -> date.plusWeeks(1);
            case CYCLE_YEAR -> date.plusYears(1);
            default -> date.plusMonths(1);
        };
    }

    private static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ResultCode.PARAM_ERROR, "周期首日格式应为 yyyy-MM-dd");
        }
    }

    private static YearMonth parseMonth(String text) {
        try {
            return YearMonth.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ResultCode.PARAM_ERROR, "月份格式应为 yyyy-MM");
        }
    }
}
