package com.jizhang.api.controller.admin;

import com.jizhang.common.api.PageResult;
import com.jizhang.common.api.Result;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.service.AdminService;
import com.jizhang.service.dto.AdminBookQueryRequest;
import com.jizhang.service.vo.BookVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 账本管理。
 */
@Tag(name = "管理后台-账本", description = "全平台账本列表")
@Validated
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/admin/books")
public class AdminBookController {

    private final AdminService adminService;

    public AdminBookController(AdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "账本列表")
    @GetMapping
    public Result<PageResult<BookVO>> page(@Valid AdminBookQueryRequest request) {
        return Result.success(adminService.pageBooks(request));
    }
}
