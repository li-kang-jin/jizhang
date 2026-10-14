package com.jizhang.api.controller;

import com.jizhang.common.api.PageResult;
import com.jizhang.common.api.Result;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.service.TransactionService;
import com.jizhang.service.dto.TransactionQueryRequest;
import com.jizhang.service.dto.TransactionSaveRequest;
import com.jizhang.service.vo.TransactionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 交易流水接口。
 */
@Tag(name = "交易", description = "交易流水增删改查，写操作自动维护账户余额")
@Validated
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/app/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(summary = "分页查询交易")
    @GetMapping
    public Result<PageResult<TransactionVO>> page(@Valid TransactionQueryRequest request) {
        return Result.success(transactionService.page(request));
    }

    @Operation(summary = "新增交易", description = "客户端可自带 id 实现离线记账的幂等写入")
    @PostMapping
    public Result<TransactionVO> create(@Valid @RequestBody TransactionSaveRequest request) {
        return Result.success(transactionService.create(request));
    }

    @Operation(summary = "交易详情")
    @GetMapping("/{id}")
    public Result<TransactionVO> detail(@PathVariable Long id) {
        return Result.success(transactionService.detail(id));
    }

    @Operation(summary = "修改交易", description = "自动回滚并重新应用账户余额")
    @PutMapping("/{id}")
    public Result<TransactionVO> update(@PathVariable Long id,
                                        @Valid @RequestBody TransactionSaveRequest request) {
        return Result.success(transactionService.update(id, request));
    }

    @Operation(summary = "删除交易")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        transactionService.delete(id);
        return Result.success();
    }

    @Operation(summary = "批量新增交易", description = "账单导入 / 多端同步批量推送")
    @PostMapping("/batch")
    public Result<List<TransactionVO>> batchCreate(@RequestBody @Valid List<TransactionSaveRequest> requests) {
        return Result.success(transactionService.batchCreate(requests));
    }
}
