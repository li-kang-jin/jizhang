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
import com.jizhang.service.vo.BudgetOverviewVO;
import com.jizhang.service.vo.BudgetVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
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
    public List<BudgetVO> list(Long bookId, String period) {
        bookAccessChecker.requireReadable(bookId);
        YearMonth month = resolveMonth(period);

        List<BudgetEntity> budgets = budgetMapper.selectByPeriod(bookId, month.toString());
        Map<Long, Long> usedMap = expenseByCategory(bookId, month);

        List<BudgetVO> result = new ArrayList<>();
        for (BudgetEntity budget : budgets) {
            if (isTotal(budget.getCategoryId())) {
                continue;
            }
            result.add(toVO(budget, null, usedMap.getOrDefault(budget.getCategoryId(), 0L)));
        }
        return result;
    }

    @Override
    public BudgetOverviewVO overview(Long bookId, String period) {
        bookAccessChecker.requireReadable(bookId);
        YearMonth month = resolveMonth(period);

        OffsetDateTime start = startOfMonth(month);
        OffsetDateTime end = startOfMonth(month.plusMonths(1));

        List<BudgetEntity> budgets = budgetMapper.selectByPeriod(bookId, month.toString());
        Map<Long, Long> usedMap = expenseByCategory(bookId, month);

        BudgetEntity totalBudget = budgets.stream()
                .filter(b -> isTotal(b.getCategoryId()))
                .findFirst()
                .orElse(null);
        SumRow monthSum = transactionMapper.sumIncomeExpense(bookId, start, end);
        long monthExpense = monthSum == null ? 0L : safe(monthSum.getExpense());

        long totalAmount = totalBudget == null ? 0L : safe(totalBudget.getAmount());
        long remaining = totalAmount - monthExpense;

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int daysInMonth = month.lengthOfMonth();
        int daysLeft = daysLeft(month, today, daysInMonth);

        BudgetOverviewVO vo = new BudgetOverviewVO();
        vo.setPeriod(month.toString());
        vo.setHasBudget(totalBudget != null);
        vo.setTotalBudgetId(totalBudget == null ? null : totalBudget.getId());
        vo.setTotalBudget(totalAmount);
        vo.setTotalUsed(monthExpense);
        // 未设置总预算时不展示「剩余」，避免出现负数误导用户
        vo.setRemaining(totalAmount == 0 ? 0L : remaining);
        vo.setUsedRatio(totalAmount == 0 ? null : (double) monthExpense / totalAmount);
        vo.setExceeded(totalAmount > 0 && monthExpense > totalAmount);
        vo.setMonthExpense(monthExpense);
        vo.setDaysInMonth(daysInMonth);
        vo.setDaysLeft(daysLeft);
        vo.setDailyAvailable(remaining > 0 && daysLeft > 0 ? remaining / daysLeft : 0L);
        vo.setSuggestDaily(totalAmount == 0 ? 0L : totalAmount / daysInMonth);

        Map<Long, CategoryEntity> categoryMap = loadCategories(bookId);
        List<BudgetVO> items = new ArrayList<>();
        List<BudgetVO> overruns = new ArrayList<>();
        for (BudgetEntity budget : budgets) {
            if (isTotal(budget.getCategoryId())) {
                continue;
            }
            BudgetVO item = toVO(budget, categoryMap.get(budget.getCategoryId()),
                    usedMap.getOrDefault(budget.getCategoryId(), 0L));
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
        YearMonth month = resolveMonth(request.getPeriod());

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
                .eq(BudgetEntity::getPeriod, month.toString())
                .last("limit 1"));

        if (exist == null) {
            BudgetEntity entity = new BudgetEntity();
            entity.setBookId(request.getBookId());
            entity.setCategoryId(categoryId);
            entity.setPeriod(month.toString());
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

    private BudgetVO toVO(BudgetEntity budget, CategoryEntity category, long used) {
        long amount = safe(budget.getAmount());
        long remaining = amount - used;

        BudgetVO vo = new BudgetVO();
        vo.setId(budget.getId());
        vo.setCategoryId(budget.getCategoryId());
        vo.setCategoryName(category == null ? TOTAL_BUDGET_NAME : category.getName());
        vo.setCategoryIcon(category == null ? null : category.getIcon());
        vo.setPeriod(budget.getPeriod());
        vo.setAmount(amount);
        vo.setUsed(used);
        vo.setRemaining(remaining);
        vo.setUsedRatio(amount == 0 ? null : (double) used / amount);
        vo.setExceeded(amount > 0 && used > amount);
        vo.setRemark(budget.getRemark());
        return vo;
    }

    /** 当月各分类的支出合计，key 为 categoryId */
    private Map<Long, Long> expenseByCategory(Long bookId, YearMonth month) {
        List<CategoryStatRow> rows = transactionMapper.statByCategory(
                bookId, TYPE_EXPENSE, startOfMonth(month), startOfMonth(month.plusMonths(1)));

        Map<Long, Long> map = new HashMap<>();
        for (CategoryStatRow row : rows) {
            if (row.getCategoryId() != null) {
                map.put(row.getCategoryId(), safe(row.getTotalAmount()));
            }
        }
        return map;
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
     * 当月剩余天数（含今天）。
     * 已过去的月份返回 0，未来月份返回整月天数。
     */
    private int daysLeft(YearMonth month, LocalDate today, int daysInMonth) {
        YearMonth current = YearMonth.from(today);
        if (month.isBefore(current)) {
            return 0;
        }
        if (month.isAfter(current)) {
            return daysInMonth;
        }
        return daysInMonth - today.getDayOfMonth() + 1;
    }

    private YearMonth resolveMonth(String period) {
        if (period == null || period.isBlank()) {
            return YearMonth.now(ZoneOffset.UTC);
        }
        try {
            return YearMonth.parse(period);
        } catch (DateTimeParseException e) {
            throw new BizException(ResultCode.PARAM_ERROR, "月份格式应为 yyyy-MM");
        }
    }

    private OffsetDateTime startOfMonth(YearMonth month) {
        return month.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private boolean isTotal(Long categoryId) {
        return categoryId == null || categoryId == TOTAL_BUDGET_CATEGORY;
    }

    private long safe(Long value) {
        return value == null ? 0L : value;
    }
}
