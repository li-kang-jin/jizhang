package com.jizhang.api.controller;

import com.jizhang.common.api.Result;
import com.jizhang.common.api.ResultCode;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.common.exception.BizException;
import com.jizhang.service.BudgetService;
import com.jizhang.service.dto.BudgetSaveRequest;
import com.jizhang.service.support.BudgetPeriod;
import com.jizhang.service.vo.BudgetOverviewVO;
import com.jizhang.service.vo.BudgetVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * C 端预算接口。
 * <p>
 * 周期参数支持两种传法：{@code cycle + periodStart}（推荐，月 / 周 / 年通用）与
 * {@code period=yyyy-MM}（兼容旧客户端，仅月周期）。
 */
@Tag(name = "预算", description = "月度/周/年总预算与分类预算、执行进度、每日预测与超支提醒")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/app/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @Operation(summary = "预算列表", description = "指定周期的分类预算，含已用 / 剩余 / 超支状态")
    @GetMapping
    public Result<List<BudgetVO>> list(@RequestParam Long bookId,
                                       @RequestParam(required = false) Integer cycle,
                                       @RequestParam(required = false) String periodStart,
                                       @RequestParam(required = false) String period) {
        return Result.success(budgetService.list(bookId, BudgetPeriod.resolve(cycle, periodStart, period)));
    }

    @Operation(summary = "预算总览", description = "总预算执行情况、每日可花额度与预测、超支明细")
    @GetMapping("/overview")
    public Result<BudgetOverviewVO> overview(@RequestParam Long bookId,
                                             @RequestParam(required = false) Integer cycle,
                                             @RequestParam(required = false) String periodStart,
                                             @RequestParam(required = false) String period,
                                             @RequestParam(required = false) String today) {
        BudgetPeriod budgetPeriod = BudgetPeriod.resolve(cycle, periodStart, period);
        return Result.success(budgetService.overview(bookId, budgetPeriod, parseToday(today)));
    }

    @Operation(summary = "保存预算", description = "同账本 + 分类 + 周期视为更新；categoryId 传 0 表示总预算")
    @PostMapping
    public Result<Long> save(@Valid @RequestBody BudgetSaveRequest request) {
        return Result.success(budgetService.save(request));
    }

    @Operation(summary = "删除预算")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, @RequestParam Long bookId) {
        budgetService.delete(bookId, id);
        return Result.success();
    }

    /**
     * 「今天」由调用方显式传入：客户端用本地时区的今天，服务端默认 UTC 的今天。
     * 不统一的话同一份预算在 App 和 Web 上会差一天。
     */
    private LocalDate parseToday(String today) {
        if (today == null || today.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(today.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ResultCode.PARAM_ERROR, "today 格式应为 yyyy-MM-dd");
        }
    }
}
