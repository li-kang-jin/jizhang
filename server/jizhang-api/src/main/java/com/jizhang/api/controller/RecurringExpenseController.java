package com.jizhang.api.controller;

import com.jizhang.common.api.Result;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.service.RecurringExpenseService;
import com.jizhang.service.dto.RecurringExpenseSaveRequest;
import com.jizhang.service.vo.RecurringExpenseVO;
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
 * C 端固定支出接口。
 * <p>
 * 固定支出不生成交易，只作为「本期还没到期就要付的钱」参与客户端的预算预留计算；
 * 这里只负责规则的增删查，预留金额由客户端按本地时区实时算。
 */
@Tag(name = "固定支出", description = "房租 / 房贷 / 会员费等按期固定开销的规则管理（只做预算预留，不生成账单）")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/app/recurring-expenses")
public class RecurringExpenseController {

    private final RecurringExpenseService recurringExpenseService;

    public RecurringExpenseController(RecurringExpenseService recurringExpenseService) {
        this.recurringExpenseService = recurringExpenseService;
    }

    @Operation(summary = "固定支出列表", description = "含已停用的规则，按生效首日排序")
    @GetMapping
    public Result<List<RecurringExpenseVO>> list(@RequestParam Long bookId) {
        return Result.success(recurringExpenseService.list(bookId));
    }

    @Operation(summary = "保存固定支出", description = "id 由客户端生成；同 id 重复提交视为修改")
    @PostMapping
    public Result<Long> save(@Valid @RequestBody RecurringExpenseSaveRequest request) {
        return Result.success(recurringExpenseService.save(request));
    }

    @Operation(summary = "删除固定支出")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, @RequestParam Long bookId) {
        recurringExpenseService.delete(bookId, id);
        return Result.success();
    }
}
