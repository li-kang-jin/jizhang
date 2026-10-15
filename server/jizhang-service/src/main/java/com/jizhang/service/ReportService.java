package com.jizhang.service;

import com.jizhang.service.vo.AssetReportVO;
import com.jizhang.service.vo.CategoryReportVO;
import com.jizhang.service.vo.MemberReportVO;
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

    /**
     * 按成员统计收支（家庭账本「谁花了多少」）。
     * <p>
     * 统计维度是交易的记账人 {@code created_by}。个人账本也可以调用，只是只有一条记录。
     */
    MemberReportVO memberReport(Long bookId, OffsetDateTime startTime, OffsetDateTime endTime);
}
