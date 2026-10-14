package com.jizhang.app.data.repository

import com.jizhang.app.data.remote.ApiService
import com.jizhang.app.data.remote.dto.AssetReportDto
import com.jizhang.app.data.remote.dto.CategoryReportDto
import com.jizhang.app.data.remote.dto.ReportOverviewDto
import com.jizhang.app.data.remote.dto.TrendItemDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportRepository @Inject constructor(
    private val api: ApiService
) {

    suspend fun overview(bookId: Long, month: String? = null): ReportOverviewDto =
        api.reportOverview(bookId, month).data ?: ReportOverviewDto()

    suspend fun category(bookId: Long, type: Int = 1): CategoryReportDto =
        api.reportCategory(bookId, type).data ?: CategoryReportDto()

    suspend fun trend(bookId: Long, granularity: String = "day"): List<TrendItemDto> =
        api.reportTrend(bookId, granularity).data.orEmpty()

    suspend fun assets(bookId: Long): AssetReportDto =
        api.reportAssets(bookId).data ?: AssetReportDto()
}
