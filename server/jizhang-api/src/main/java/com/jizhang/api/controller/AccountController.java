package com.jizhang.api.controller;

import com.jizhang.common.api.Result;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.service.AccountService;
import com.jizhang.service.dto.AccountSaveRequest;
import com.jizhang.service.vo.AccountVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 账户接口。
 */
@Tag(name = "账户", description = "账户增删改查")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/app/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "账户列表")
    @GetMapping
    public Result<List<AccountVO>> list(@RequestParam Long bookId) {
        return Result.success(accountService.listByBook(bookId));
    }

    @Operation(summary = "创建账户")
    @PostMapping
    public Result<AccountVO> create(@Valid @RequestBody AccountSaveRequest request) {
        return Result.success(accountService.create(request));
    }

    @Operation(summary = "修改账户")
    @PutMapping("/{id}")
    public Result<AccountVO> update(@PathVariable Long id, @Valid @RequestBody AccountSaveRequest request) {
        return Result.success(accountService.update(id, request));
    }

    @Operation(summary = "删除账户")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        accountService.delete(id);
        return Result.success();
    }
}
