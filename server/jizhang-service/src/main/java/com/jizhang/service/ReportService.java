package com.jizhang.service;

import com.jizhang.service.vo.AssetReportVO;
import com.jizhang.service.vo.CategoryReportVO;
import com.jizhang.service.vo.ReportOverviewVO;
import com.jizhang.service.vo.TrendItemVO;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * C 端账本报表服务。所有接口都会校验账本访问权限。
 */
public interface ReportService {

    /**
     * 收支总览。
     *
     * @param month 统计月份 yyyy-MM，为空取当前月
     */
    ReportOverviewVO overview(Long bookId, String month);

    /**
     * 分类占比。
     *
     * @param type 1=支出 2=收入，为空表示不限制
     */
    CategoryReportVO category(Long bookId, Integer type, OffsetDateTime startTime, OffsetDateTime endTime);

    /**
     * 收支趋势。
     *
     * @param granularity day 或 month，默认 day
     */
    List<TrendItemVO> trend(Long bookId, String granularity, OffsetDateTime startTime, OffsetDateTime endTime);

    /** 账户资产分布 */
    AssetReportVO assets(Long bookId);
}
