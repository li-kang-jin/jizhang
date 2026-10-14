package com.jizhang.api.controller;

import com.jizhang.common.api.Result;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.service.ReportService;
import com.jizhang.service.vo.AssetReportVO;
import com.jizhang.service.vo.CategoryReportVO;
import com.jizhang.service.vo.ReportOverviewVO;
import com.jizhang.service.vo.TrendItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * C 端账本报表接口。
 */
@Tag(name = "报表", description = "账本收支总览、分类占比、趋势与资产分布")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/app/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "收支总览", description = "含与上月的环比")
    @GetMapping("/overview")
    public Result<ReportOverviewVO> overview(@RequestParam Long bookId,
                                             @RequestParam(required = false) String month) {
        return Result.success(reportService.overview(bookId, month));
    }

    @Operation(summary = "分类占比", description = "饼图数据源")
    @GetMapping("/category")
    public Result<CategoryReportVO> category(
            @RequestParam Long bookId,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        return Result.success(reportService.category(bookId, type, startTime, endTime));
    }

    @Operation(summary = "收支趋势", description = "折线图数据源，granularity 取 day 或 month")
    @GetMapping("/trend")
    public Result<List<TrendItemVO>> trend(
            @RequestParam Long bookId,
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        return Result.success(reportService.trend(bookId, granularity, startTime, endTime));
    }

    @Operation(summary = "资产分布", description = "账户余额与总资产")
    @GetMapping("/assets")
    public Result<AssetReportVO> assets(@RequestParam Long bookId) {
        return Result.success(reportService.assets(bookId));
    }
}
