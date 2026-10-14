package com.jizhang.api.controller.admin;

import com.jizhang.common.api.Result;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.service.AdminService;
import com.jizhang.service.vo.PlatformOverviewVO;
import com.jizhang.service.vo.TrendPointVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台 - 平台统计。
 */
@Tag(name = "管理后台-统计", description = "平台运营数据看板")
@Validated
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/admin/statistics")
public class AdminStatisticsController {

    private final AdminService adminService;

    public AdminStatisticsController(AdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "平台总览")
    @GetMapping("/overview")
    public Result<PlatformOverviewVO> overview() {
        return Result.success(adminService.overview());
    }

    @Operation(summary = "增长趋势", description = "按日统计新增用户、交易笔数与金额")
    @GetMapping("/trend")
    public Result<List<TrendPointVO>> trend(@RequestParam(defaultValue = "30") @Min(7) @Max(90) Integer days) {
        return Result.success(adminService.trend(days));
    }
}
