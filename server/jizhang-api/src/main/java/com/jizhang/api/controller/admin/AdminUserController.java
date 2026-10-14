package com.jizhang.api.controller.admin;

import com.jizhang.common.api.PageResult;
import com.jizhang.common.api.Result;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.service.AdminService;
import com.jizhang.service.dto.AdminUserQueryRequest;
import com.jizhang.service.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 用户管理。
 */
@Tag(name = "管理后台-用户", description = "用户列表与状态管理")
@Validated
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/admin/users")
public class AdminUserController {

    private final AdminService adminService;

    public AdminUserController(AdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "用户列表")
    @GetMapping
    public Result<PageResult<UserVO>> page(@Valid AdminUserQueryRequest request) {
        return Result.success(adminService.pageUsers(request));
    }

    @Operation(summary = "启用/禁用用户")
    @PutMapping("/{id}/status")
    public Result<UserVO> updateStatus(@PathVariable Long id,
                                       @RequestParam @Min(0) @Max(1) Integer status) {
        return Result.success(adminService.updateUserStatus(id, status));
    }
}
