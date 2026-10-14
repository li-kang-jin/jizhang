package com.jizhang.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jizhang.common.api.ResultCode;
import com.jizhang.common.exception.BizException;
import com.jizhang.dao.dto.CategoryStatRow;
import com.jizhang.dao.dto.PeriodStatRow;
import com.jizhang.dao.dto.SumRow;
import com.jizhang.dao.entity.AccountEntity;
import com.jizhang.dao.mapper.AccountMapper;
import com.jizhang.dao.mapper.TransactionMapper;
import com.jizhang.service.ReportService;
import com.jizhang.service.support.BookAccessChecker;
import com.jizhang.service.vo.AccountAssetVO;
import com.jizhang.service.vo.AssetReportVO;
import com.jizhang.service.vo.CategoryReportVO;
import com.jizhang.service.vo.CategoryStatVO;
import com.jizhang.service.vo.ReportOverviewVO;
import com.jizhang.service.vo.TrendItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    private static final String GRANULARITY_MONTH = "month";
    private static final String PATTERN_DAY = "YYYY-MM-DD";
    private static final String PATTERN_MONTH = "YYYY-MM";

    private static final int DEFAULT_TREND_DAYS = 30;
    private static final int DEFAULT_TREND_MONTHS = 12;

    /** includeInTotal = 1 表示计入总资产 */
    private static final int INCLUDE_IN_TOTAL = 1;

    private final TransactionMapper transactionMapper;
    private final AccountMapper accountMapper;
    private final BookAccessChecker bookAccessChecker;

    public ReportServiceImpl(TransactionMapper transactionMapper,
                             AccountMapper accountMapper,
                             BookAccessChecker bookAccessChecker) {
        this.transactionMapper = transactionMapper;
        this.accountMapper = accountMapper;
        this.bookAccessChecker = bookAccessChecker;
    }

    @Override
    public ReportOverviewVO overview(Long bookId, String month) {
        bookAccessChecker.requireReadable(bookId);

        YearMonth target = month == null || month.isBlank() ? YearMonth.now(ZoneOffset.UTC) : parseMonth(month);
        OffsetDateTime start = startOfMonth(target);
        OffsetDateTime end = startOfMonth(target.plusMonths(1));

        SumRow current = transactionMapper.sumIncomeExpense(bookId, start, end);
        SumRow previous = transactionMapper.sumIncomeExpense(bookId, startOfMonth(target.minusMonths(1)), start);

        long income = valueOf(current == null ? null : current.getIncome());
        long expense = valueOf(current == null ? null : current.getExpense());
        long previousIncome = valueOf(previous == null ? null : previous.getIncome());
        long previousExpense = valueOf(previous == null ? null : previous.getExpense());

        ReportOverviewVO vo = new ReportOverviewVO();
        vo.setMonth(target.toString());
        vo.setIncome(income);
        vo.setExpense(expense);
        vo.setBalance(income - expense);
        vo.setIncomeRatio(ratio(income, previousIncome));
        vo.setExpenseRatio(ratio(expense, previousExpense));
        return vo;
    }

    @Override
    public CategoryReportVO category(Long bookId, Integer type, OffsetDateTime startTime, OffsetDateTime endTime) {
        bookAccessChecker.requireReadable(bookId);
        OffsetDateTime[] range = normalizeRange(startTime, endTime);

        List<CategoryStatRow> rows = transactionMapper.statByCategory(bookId, type, range[0], range[1]);
        long total = rows.stream().mapToLong(row -> valueOf(row.getTotalAmount())).sum();

        List<CategoryStatVO> items = new ArrayList<>(rows.size());
        for (CategoryStatRow row : rows) {
            long amount = valueOf(row.getTotalAmount());
            CategoryStatVO item = new CategoryStatVO();
            item.setCategoryId(row.getCategoryId());
            item.setCategoryName(row.getCategoryName() == null ? "未分类" : row.getCategoryName());
            item.setIcon(row.getIcon());
            item.setColor(row.getColor());
            item.setAmount(amount);
            item.setRatio(total == 0 ? 0D : (double) amount / total);
            items.add(item);
        }

        CategoryReportVO vo = new CategoryReportVO();
        vo.setTotal(total);
        vo.setItems(items);
        return vo;
    }

    @Override
    public List<TrendItemVO> trend(Long bookId, String granularity,
                                   OffsetDateTime startTime, OffsetDateTime endTime) {
        bookAccessChecker.requireReadable(bookId);

        boolean monthly = GRANULARITY_MONTH.equalsIgnoreCase(granularity);
        OffsetDateTime start;
        OffsetDateTime end;

        if (startTime != null || endTime != null) {
            OffsetDateTime[] range = normalizeRange(startTime, endTime);
            start = range[0];
            end = range[1];
        } else if (monthly) {
            YearMonth current = YearMonth.now(ZoneOffset.UTC);
            start = startOfMonth(current.minusMonths(DEFAULT_TREND_MONTHS - 1L));
            end = startOfMonth(current.plusMonths(1));
        } else {
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            start = today.minusDays(DEFAULT_TREND_DAYS - 1L).atStartOfDay().atOffset(ZoneOffset.UTC);
            end = today.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        }

        List<PeriodStatRow> rows = transactionMapper.statByPeriod(
                bookId, monthly ? PATTERN_MONTH : PATTERN_DAY, start, end);
        Map<String, PeriodStatRow> rowMap = rows.stream()
                .collect(Collectors.toMap(PeriodStatRow::getPeriod, Function.identity(), (a, b) -> a));

        // 补全没有数据的区间，保证折线图 X 轴连续
        List<TrendItemVO> result = new ArrayList<>();
        OffsetDateTime lastPoint = end.minusSeconds(1);
        if (monthly) {
            YearMonth cursor = YearMonth.from(start);
            YearMonth last = YearMonth.from(lastPoint);
            while (!cursor.isAfter(last)) {
                result.add(buildTrendItem(cursor.toString(), rowMap));
                cursor = cursor.plusMonths(1);
            }
        } else {
            LocalDate cursor = start.toLocalDate();
            LocalDate last = lastPoint.toLocalDate();
            while (!cursor.isAfter(last)) {
                result.add(buildTrendItem(cursor.format(DateTimeFormatter.ISO_LOCAL_DATE), rowMap));
                cursor = cursor.plusDays(1);
            }
        }
        return result;
    }

    @Override
    public AssetReportVO assets(Long bookId) {
        bookAccessChecker.requireReadable(bookId);

        List<AccountEntity> accounts = accountMapper.selectList(Wrappers.<AccountEntity>lambdaQuery()
                .eq(AccountEntity::getBookId, bookId)
                .orderByAsc(AccountEntity::getSort)
                .orderByAsc(AccountEntity::getId));

        long totalAssets = 0L;
        List<AccountAssetVO> items = new ArrayList<>(accounts.size());
        for (AccountEntity account : accounts) {
            AccountAssetVO item = AccountAssetVO.from(account);
            items.add(item);
            if (Objects.equals(account.getIncludeInTotal(), INCLUDE_IN_TOTAL)) {
                totalAssets += item.getBalance();
            }
        }

        AssetReportVO vo = new AssetReportVO();
        vo.setTotalAssets(totalAssets);
        vo.setAccounts(items);
        return vo;
    }

    /* ---------------- 内部方法 ---------------- */

    private TrendItemVO buildTrendItem(String period, Map<String, PeriodStatRow> rowMap) {
        PeriodStatRow row = rowMap.get(period);
        long income = valueOf(row == null ? null : row.getIncome());
        long expense = valueOf(row == null ? null : row.getExpense());

        TrendItemVO item = new TrendItemVO();
        item.setPeriod(period);
        item.setIncome(income);
        item.setExpense(expense);
        item.setBalance(income - expense);
        return item;
    }

    private OffsetDateTime[] normalizeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        OffsetDateTime end = endTime == null ? OffsetDateTime.now(ZoneOffset.UTC).plusDays(1) : endTime;
        OffsetDateTime start = startTime == null ? end.minusDays(DEFAULT_TREND_DAYS) : startTime;
        if (start.isAfter(end)) {
            throw new BizException(ResultCode.PARAM_ERROR, "开始时间不能晚于结束时间");
        }
        return new OffsetDateTime[]{start, end};
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BizException(ResultCode.PARAM_ERROR, "月份格式应为 yyyy-MM");
        }
    }

    private OffsetDateTime startOfMonth(YearMonth yearMonth) {
        return yearMonth.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private Double ratio(long current, long previous) {
        if (previous == 0L) {
            return null;
        }
        return (double) (current - previous) / previous;
    }

    private long valueOf(Long value) {
        return value == null ? 0L : value;
    }
}
