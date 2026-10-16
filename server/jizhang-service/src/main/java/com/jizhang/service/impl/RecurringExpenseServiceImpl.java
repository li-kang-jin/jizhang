package com.jizhang.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jizhang.common.api.ResultCode;
import com.jizhang.common.exception.BizException;
import com.jizhang.dao.entity.CategoryEntity;
import com.jizhang.dao.entity.RecurringExpenseEntity;
import com.jizhang.dao.mapper.CategoryMapper;
import com.jizhang.dao.mapper.RecurringExpenseMapper;
import com.jizhang.service.RecurringExpenseService;
import com.jizhang.service.dto.RecurringExpenseSaveRequest;
import com.jizhang.service.support.BookAccessChecker;
import com.jizhang.service.vo.RecurringExpenseVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 固定支出服务实现。
 * <p>
 * 幂等键是客户端生成的 id：同一个 id 重复提交视为修改，不存在则新建。服务端不重新分配 ID，
 * 这样离线创建的记录在补推时不会因为「ID 变了」而在本地留下两条。
 */
@Service
public class RecurringExpenseServiceImpl implements RecurringExpenseService {

    /** 未指定分类 */
    private static final long NO_CATEGORY = 0L;

    private static final int FREQ_WEEKLY = 1;
    private static final int FREQ_MONTHLY = 2;
    private static final int FREQ_YEARLY = 3;

    private final RecurringExpenseMapper recurringExpenseMapper;
    private final CategoryMapper categoryMapper;
    private final BookAccessChecker bookAccessChecker;

    public RecurringExpenseServiceImpl(RecurringExpenseMapper recurringExpenseMapper,
                                       CategoryMapper categoryMapper,
                                       BookAccessChecker bookAccessChecker) {
        this.recurringExpenseMapper = recurringExpenseMapper;
        this.categoryMapper = categoryMapper;
        this.bookAccessChecker = bookAccessChecker;
    }

    @Override
    public List<RecurringExpenseVO> list(Long bookId) {
        bookAccessChecker.requireReadable(bookId);
        return recurringExpenseMapper.selectList(Wrappers.<RecurringExpenseEntity>lambdaQuery()
                        .eq(RecurringExpenseEntity::getBookId, bookId)
                        .orderByAsc(RecurringExpenseEntity::getStartDate)
                        .orderByAsc(RecurringExpenseEntity::getId))
                .stream()
                .map(RecurringExpenseVO::from)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(RecurringExpenseSaveRequest request) {
        bookAccessChecker.requireWritable(request.getBookId());

        long categoryId = request.getCategoryId() == null ? NO_CATEGORY : request.getCategoryId();
        if (categoryId < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "分类不正确");
        }
        if (categoryId != NO_CATEGORY && !categoryExists(request.getBookId(), categoryId)) {
            throw new BizException(ResultCode.CATEGORY_NOT_FOUND);
        }

        // startDate 缺省按服务端今天：客户端总是显式传，只有裸调接口才会走到这个分支
        LocalDate startDate = parseDate(request.getStartDate(), LocalDate.now(),
                "startDate 格式应为 yyyy-MM-dd");
        LocalDate endDate = parseDate(request.getEndDate(), null, "endDate 格式应为 yyyy-MM-dd");
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BizException(ResultCode.PARAM_ERROR, "失效日不能早于生效首日");
        }

        RecurringExpenseEntity exist = recurringExpenseMapper.selectById(request.getId());
        if (exist == null) {
            RecurringExpenseEntity entity = new RecurringExpenseEntity();
            entity.setId(request.getId());
            entity.setBookId(request.getBookId());
            fill(entity, request, categoryId, startDate, endDate);
            entity.setVersion(0);
            recurringExpenseMapper.insert(entity);
            return entity.getId();
        }

        // id 撞上了别的账本的记录：客户端 ID 是全局唯一的，出现这种情况只能说明请求有问题，
        // 直接拒绝而不是把别人的固定支出改到当前账本名下
        if (!exist.getBookId().equals(request.getBookId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "固定支出不存在");
        }

        fill(exist, request, categoryId, startDate, endDate);
        exist.setVersion(exist.getVersion() == null ? 0 : exist.getVersion() + 1);
        recurringExpenseMapper.updateById(exist);
        return exist.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long bookId, Long id) {
        bookAccessChecker.requireWritable(bookId);
        RecurringExpenseEntity exist = recurringExpenseMapper.selectById(id);
        if (exist == null || !exist.getBookId().equals(bookId)) {
            throw new BizException(ResultCode.PARAM_ERROR, "固定支出不存在");
        }
        recurringExpenseMapper.deleteById(id);
    }

    /* ---------------- 内部方法 ---------------- */

    private void fill(RecurringExpenseEntity entity,
                      RecurringExpenseSaveRequest request,
                      long categoryId,
                      LocalDate startDate,
                      LocalDate endDate) {
        entity.setName(request.getName().trim());
        entity.setCategoryId(categoryId);
        entity.setFreq(normalizeFreq(request.getFreq()));
        entity.setDayOfWeek(clamp(request.getDayOfWeek(), 1, 7, 1));
        entity.setDayOfMonth(clamp(request.getDayOfMonth(), 1, 31, 1));
        entity.setMonthOfYear(clamp(request.getMonthOfYear(), 1, 12, 1));
        entity.setAmount(request.getAmount());
        entity.setStartDate(startDate);
        entity.setEndDate(endDate);
        entity.setEnabled(request.getEnabled() == null || request.getEnabled() ? 1 : 0);
        entity.setRemark(request.getRemark());
    }

    private boolean categoryExists(Long bookId, Long categoryId) {
        CategoryEntity category = categoryMapper.selectById(categoryId);
        return category != null && category.getBookId().equals(bookId);
    }

    private int normalizeFreq(Integer freq) {
        if (freq == null) {
            return FREQ_MONTHLY;
        }
        return switch (freq) {
            case FREQ_WEEKLY, FREQ_MONTHLY, FREQ_YEARLY -> freq;
            default -> FREQ_MONTHLY;
        };
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.min(Math.max(value, min), max);
    }

    private LocalDate parseDate(String text, LocalDate fallback, String errorMessage) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ResultCode.PARAM_ERROR, errorMessage);
        }
    }
}
