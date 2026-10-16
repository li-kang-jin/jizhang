package com.jizhang.api.controller.admin;

import com.jizhang.common.api.PageResult;
import com.jizhang.common.api.Result;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.service.AdminService;
import com.jizhang.service.dto.AccountSaveRequest;
import com.jizhang.service.dto.AdminAccountQueryRequest;
import com.jizhang.service.vo.AdminAccountVO;
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

/**
 * 管理后台 - 账户管理。
 * <p>
 * 与 `/app/accounts` 的区别：后台面向全平台，不走账本成员权限校验（由 URL 规则限制为 ADMIN），
 * 但列表会带上所属账本与所有者，便于管理员定位数据。
 */
@Tag(name = "管理后台-账户", description = "全平台账户增删改查")
@Validated
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/admin/accounts")
public class AdminAccountController {

    private final AdminService adminService;

    public AdminAccountController(AdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "账户列表")
    @GetMapping
    public Result<PageResult<AdminAccountVO>> page(@Valid AdminAccountQueryRequest request) {
        return Result.success(adminService.pageAccounts(request));
    }

    @Operation(summary = "创建账户")
    @PostMapping
    public Result<AdminAccountVO> create(@Valid @RequestBody AccountSaveRequest request) {
        return Result.success(adminService.createAccount(request));
    }

    @Operation(summary = "修改账户", description = "不允许改动所属账本，bookId 会被忽略")
    @PutMapping("/{id}")
    public Result<AdminAccountVO> update(@PathVariable Long id, @Valid @RequestBody AccountSaveRequest request) {
        return Result.success(adminService.updateAccount(id, request));
    }

    @Operation(summary = "删除账户", description = "账户下仍有交易流水时拒绝删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminService.deleteAccount(id);
        return Result.success();
    }
}
