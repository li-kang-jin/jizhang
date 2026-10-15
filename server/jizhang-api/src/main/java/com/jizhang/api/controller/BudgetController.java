package com.jizhang.api.controller;

import com.jizhang.common.api.Result;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.service.BudgetService;
import com.jizhang.service.dto.BudgetSaveRequest;
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

import java.util.List;

/**
 * C 端预算接口。
 */
@Tag(name = "预算", description = "月度总预算与分类预算、执行进度与超支提醒")
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
                                       @RequestParam(required = false) String period) {
        return Result.success(budgetService.list(bookId, period));
    }

    @Operation(summary = "预算总览", description = "总预算执行情况、日均可用额度与超支明细")
    @GetMapping("/overview")
    public Result<BudgetOverviewVO> overview(@RequestParam Long bookId,
                                             @RequestParam(required = false) String period) {
        return Result.success(budgetService.overview(bookId, period));
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
}
