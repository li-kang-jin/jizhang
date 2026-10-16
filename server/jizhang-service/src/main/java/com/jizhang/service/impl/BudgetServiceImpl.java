package com.jizhang.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jizhang.common.api.ResultCode;
import com.jizhang.common.exception.BizException;
import com.jizhang.dao.dto.CategoryStatRow;
import com.jizhang.dao.dto.SumRow;
import com.jizhang.dao.entity.BudgetEntity;
import com.jizhang.dao.entity.CategoryEntity;
import com.jizhang.dao.mapper.BudgetMapper;
import com.jizhang.dao.mapper.CategoryMapper;
import com.jizhang.dao.mapper.TransactionMapper;
import com.jizhang.service.BudgetService;
import com.jizhang.service.dto.BudgetSaveRequest;
import com.jizhang.service.support.BookAccessChecker;
import com.jizhang.service.support.BudgetPeriod;
import com.jizhang.service.vo.BudgetOverviewVO;
import com.jizhang.service.vo.BudgetVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预算服务实现。
 * <p>
 * 只持久化「额度」，实际花销每次按交易流水实时聚合 ——
 * 避免记账 / 修改 / 删除 / 迁移等每条路径都要维护预算进度的一致性。
 */
@Slf4j
@Service
public class BudgetServiceImpl implements BudgetService {

    /** category_id = 0 代表账本总预算 */
    private static final long TOTAL_BUDGET_CATEGORY = 0L;

    /** 预算只统计支出 */
    private static final int TYPE_EXPENSE = 1;

    private static final String TOTAL_BUDGET_NAME = "总预算";

    /** 预测超支超过这个比例才算「有风险」 */
    private static final double AT_RISK_RATIO = 1.1d;

    private final BudgetMapper budgetMapper;
    private final CategoryMapper categoryMapper;
    private final TransactionMapper transactionMapper;
    private final BookAccessChecker bookAccessChecker;

    public BudgetServiceImpl(BudgetMapper budgetMapper,
                             CategoryMapper categoryMapper,
                             TransactionMapper transactionMapper,
                             BookAccessChecker bookAccessChecker) {
        this.budgetMapper = budgetMapper;
        this.categoryMapper = categoryMapper;
        this.transactionMapper = transactionMapper;
        this.bookAccessChecker = bookAccessChecker;
    }

    @Override
    public List<BudgetVO> list(Long bookId, BudgetPeriod period) {
        bookAccessChecker.requireReadable(bookId);

        List<BudgetEntity> budgets = budgetMapper.selectByPeriod(bookId, period.cycle(), period.start());
        Map<Long, Long> usedMap = expenseByCategory(bookId, period);

        List<BudgetVO> result = new ArrayList<>();
        for (BudgetEntity budget : budgets) {
            if (isTotal(budget.getCategoryId())) {
                continue;
            }
            result.add(toVO(budget, null, usedMap.getOrDefault(budget.getCategoryId(), 0L), period));
        }
        return result;
    }

    @Override
    public BudgetOverviewVO overview(Long bookId, BudgetPeriod period, LocalDate today) {
        bookAccessChecker.requireReadable(bookId);

        LocalDate day = today == null ? LocalDate.now(ZoneOffset.UTC) : today;
        List<BudgetEntity> budgets = budgetMapper.selectByPeriod(bookId, period.cycle(), period.start());
        Map<Long, Long> usedMap = expenseByCategory(bookId, period);

        BudgetEntity totalBudget = budgets.stream()
                .filter(b -> isTotal(b.getCategoryId()))
                .findFirst()
                .orElse(null);
        SumRow periodSum = transactionMapper.sumIncomeExpense(
                bookId, startOfDayUtc(period.start()), startOfDayUtc(period.endExclusive()));
        long periodExpense = periodSum == null ? 0L : safe(periodSum.getExpense());

        long totalAmount = totalBudget == null ? 0L : safe(totalBudget.getAmount());
        long remaining = totalAmount - periodExpense;

        int daysTotal = period.daysTotal();
        int elapsedDays = elapsedDays(period, day, daysTotal);
        int daysLeft = daysLeft(period, day, daysTotal);

        BudgetOverviewVO vo = new BudgetOverviewVO();
        vo.setCycle(period.cycle());
        vo.setPeriodStart(period.start());
        vo.setPeriod(period.label());
        vo.setHasBudget(totalBudget != null);
        vo.setTotalBudgetId(totalBudget == null ? null : totalBudget.getId());
        vo.setTotalBudget(totalAmount);
        vo.setTotalUsed(periodExpense);
        // 未设置总预算时不展示「剩余」，避免出现负数误导用户
        vo.setRemaining(totalAmount == 0 ? 0L : remaining);
        vo.setUsedRatio(totalAmount == 0 ? null : (double) periodExpense / totalAmount);
        vo.setExceeded(totalAmount > 0 && periodExpense > totalAmount);
        vo.setMonthExpense(periodExpense);
        vo.setDaysInMonth(daysTotal);
        vo.setDaysTotal(daysTotal);
        vo.setDaysLeft(daysLeft);
        vo.setElapsedDays(elapsedDays);
        vo.setDailyAvailable(remaining > 0 && daysLeft > 0 ? remaining / daysLeft : 0L);
        vo.setSuggestDaily(totalAmount == 0 ? 0L : totalAmount / daysTotal);
        vo.setTodayExpense(todayExpense(bookId, day));
        vo.setBurnRate(burnRate(periodExpense, elapsedDays));
        vo.setForecastExpense(forecastExpense(periodExpense, elapsedDays, daysLeft));
        vo.setStatus(status(totalAmount, periodExpense, vo.getForecastExpense()));

        Map<Long, CategoryEntity> categoryMap = loadCategories(bookId);
        List<BudgetVO> items = new ArrayList<>();
        List<BudgetVO> overruns = new ArrayList<>();
        for (BudgetEntity budget : budgets) {
            if (isTotal(budget.getCategoryId())) {
                continue;
            }
            BudgetVO item = toVO(budget, categoryMap.get(budget.getCategoryId()),
                    usedMap.getOrDefault(budget.getCategoryId(), 0L), period);
            items.add(item);
            if (Boolean.TRUE.equals(item.getExceeded())) {
                overruns.add(item);
            }
        }
        vo.setItems(items);
        vo.setOverruns(overruns);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(BudgetSaveRequest request) {
        bookAccessChecker.requireWritable(request.getBookId());
        BudgetPeriod period = BudgetPeriod.resolve(request.getCycle(), request.getPeriodStart(), request.getPeriod());

        long categoryId = request.getCategoryId() == null ? TOTAL_BUDGET_CATEGORY : request.getCategoryId();
        if (categoryId < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "分类不正确");
        }
        if (!isTotal(categoryId) && !categoryExists(request.getBookId(), categoryId)) {
            throw new BizException(ResultCode.CATEGORY_NOT_FOUND);
        }

        BudgetEntity exist = budgetMapper.selectOne(Wrappers.<BudgetEntity>lambdaQuery()
                .eq(BudgetEntity::getBookId, request.getBookId())
                .eq(BudgetEntity::getCategoryId, categoryId)
                .eq(BudgetEntity::getCycle, period.cycle())
                .eq(BudgetEntity::getPeriodStart, period.start())
                .last("limit 1"));

        if (exist == null) {
            BudgetEntity entity = new BudgetEntity();
            entity.setBookId(request.getBookId());
            entity.setCategoryId(categoryId);
            entity.setCycle(period.cycle());
            entity.setPeriodStart(period.start());
            entity.setAmount(request.getAmount());
            entity.setRemark(request.getRemark());
            entity.setVersion(0);
            budgetMapper.insert(entity);
            return entity.getId();
        }

        exist.setAmount(request.getAmount());
        exist.setRemark(request.getRemark());
        exist.setVersion(exist.getVersion() == null ? 0 : exist.getVersion() + 1);
        budgetMapper.updateById(exist);
        return exist.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long bookId, Long id) {
        bookAccessChecker.requireWritable(bookId);
        BudgetEntity exist = budgetMapper.selectById(id);
        if (exist == null || !exist.getBookId().equals(bookId)) {
            throw new BizException(ResultCode.PARAM_ERROR, "预算不存在");
        }
        budgetMapper.deleteById(id);
    }

    /* ---------------- 内部方法 ---------------- */

    private BudgetVO toVO(BudgetEntity budget, CategoryEntity category, long used, BudgetPeriod period) {
        long amount = safe(budget.getAmount());
        long remaining = amount - used;

        BudgetVO vo = new BudgetVO();
        vo.setId(budget.getId());
        vo.setCategoryId(budget.getCategoryId());
        vo.setCategoryName(category == null ? TOTAL_BUDGET_NAME : category.getName());
        vo.setCategoryIcon(category == null ? null : category.getIcon());
        vo.setCycle(period.cycle());
        vo.setPeriodStart(period.start());
        vo.setPeriod(period.label());
        vo.setAmount(amount);
        vo.setUsed(used);
        vo.setRemaining(remaining);
        vo.setUsedRatio(amount == 0 ? null : (double) used / amount);
        vo.setExceeded(amount > 0 && used > amount);
        vo.setRemark(budget.getRemark());
        return vo;
    }

    /** 周期内各分类的支出合计，key 为 categoryId */
    private Map<Long, Long> expenseByCategory(Long bookId, BudgetPeriod period) {
        List<CategoryStatRow> rows = transactionMapper.statByCategory(
                bookId, TYPE_EXPENSE, startOfDayUtc(period.start()), startOfDayUtc(period.endExclusive()));

        Map<Long, Long> map = new HashMap<>();
        for (CategoryStatRow row : rows) {
            if (row.getCategoryId() != null) {
                map.put(row.getCategoryId(), safe(row.getTotalAmount()));
            }
        }
        return map;
    }

    private long todayExpense(Long bookId, LocalDate today) {
        SumRow sum = transactionMapper.sumIncomeExpense(
                bookId, startOfDayUtc(today), startOfDayUtc(today.plusDays(1)));
        return sum == null ? 0L : safe(sum.getExpense());
    }

    private Map<Long, CategoryEntity> loadCategories(Long bookId) {
        List<CategoryEntity> list = categoryMapper.selectList(Wrappers.<CategoryEntity>lambdaQuery()
                .eq(CategoryEntity::getBookId, bookId));

        Map<Long, CategoryEntity> map = new HashMap<>();
        for (CategoryEntity category : list) {
            map.put(category.getId(), category);
        }
        return map;
    }

    /** 分类需存在，且属于该账本或为系统内置（book_id = 0） */
    private boolean categoryExists(Long bookId, long categoryId) {
        Long count = categoryMapper.selectCount(Wrappers.<CategoryEntity>lambdaQuery()
                .eq(CategoryEntity::getId, categoryId)
                .and(w -> w.eq(CategoryEntity::getBookId, bookId).or().eq(CategoryEntity::getBookId, 0L)));
        return count != null && count > 0;
    }

    /**
     * 周期内已过天数（含今天）。
     * 周期未开始为 0，已结束为整个周期长度。
     */
    private int elapsedDays(BudgetPeriod period, LocalDate today, int daysTotal) {
        if (today.isBefore(period.start())) {
            return 0;
        }
        if (!today.isBefore(period.endExclusive())) {
            return daysTotal;
        }
        return (int) (today.toEpochDay() - period.start().toEpochDay()) + 1;
    }

    /**
     * 周期内剩余天数（含今天）。
     * 注意不能写成 {@code daysTotal - elapsedDays + 1}：周期已结束时 elapsedDays == daysTotal，
     * 那个式子会算出 1，把「已经没有剩余天数」错报成「还剩今天」。
     */
    private int daysLeft(BudgetPeriod period, LocalDate today, int daysTotal) {
        if (today.isBefore(period.start())) {
            return daysTotal;
        }
        if (!today.isBefore(period.endExclusive())) {
            return 0;
        }
        return (int) (period.endExclusive().toEpochDay() - today.toEpochDay());
    }

    /** 日均消耗（分/天）。周期还没开始时不预测。 */
    private Double burnRate(long used, int elapsedDays) {
        return elapsedDays <= 0 ? null : (double) used / elapsedDays;
    }

    /**
     * 按当前速度预测期末支出。
     * <p>
     * 已花 + 日均 × 今天之后还会剩余的天数。故意**不复用** dailyAvailable（那是「还能花多少」，
     * 不是「按现状会花多少」），两者语义相反，混用会把预测算成永远不超支。
     */
    private Long forecastExpense(long used, int elapsedDays, int daysLeft) {
        if (elapsedDays <= 0) {
            return null;
        }
        int remainAfterToday = Math.max(daysLeft - 1, 0);
        return used + Math.round((double) used / elapsedDays * remainAfterToday);
    }

    private String status(long totalAmount, long used, Long forecast) {
        if (totalAmount <= 0) {
            return "NO_BUDGET";
        }
        if (used > totalAmount) {
            return "OVER";
        }
        if (forecast != null && forecast > totalAmount * AT_RISK_RATIO) {
            return "AT_RISK";
        }
        return "ON_TRACK";
    }

    private OffsetDateTime startOfDayUtc(LocalDate date) {
        return date.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private boolean isTotal(Long categoryId) {
        return categoryId == null || categoryId == TOTAL_BUDGET_CATEGORY;
    }

    private long safe(Long value) {
        return value == null ? 0L : value;
    }
}
