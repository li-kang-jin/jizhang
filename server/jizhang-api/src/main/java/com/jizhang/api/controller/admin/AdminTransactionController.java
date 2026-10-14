package com.jizhang.api.controller.admin;

import com.jizhang.common.api.PageResult;
import com.jizhang.common.api.Result;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.service.AdminService;
import com.jizhang.service.dto.AdminTransactionQueryRequest;
import com.jizhang.service.vo.TransactionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 交易流水查询。
 */
@Tag(name = "管理后台-交易", description = "全平台交易流水查询")
@Validated
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/admin/transactions")
public class AdminTransactionController {

    private final AdminService adminService;

    public AdminTransactionController(AdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "交易流水列表")
    @GetMapping
    public Result<PageResult<TransactionVO>> page(@Valid AdminTransactionQueryRequest request) {
        return Result.success(adminService.pageTransactions(request));
    }
}
