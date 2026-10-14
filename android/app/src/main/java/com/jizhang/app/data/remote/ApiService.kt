package com.jizhang.app.data.remote

import com.jizhang.app.data.remote.dto.AccountDto
import com.jizhang.app.data.remote.dto.ApiResult
import com.jizhang.app.data.remote.dto.AssetReportDto
import com.jizhang.app.data.remote.dto.BookDto
import com.jizhang.app.data.remote.dto.CategoryDto
import com.jizhang.app.data.remote.dto.CategoryReportDto
import com.jizhang.app.data.remote.dto.LoginRequest
import com.jizhang.app.data.remote.dto.PageResult
import com.jizhang.app.data.remote.dto.RefreshTokenRequest
import com.jizhang.app.data.remote.dto.RegisterRequest
import com.jizhang.app.data.remote.dto.ReportOverviewDto
import com.jizhang.app.data.remote.dto.SyncPullDto
import com.jizhang.app.data.remote.dto.SyncPullRequest
import com.jizhang.app.data.remote.dto.SyncPushDto
import com.jizhang.app.data.remote.dto.SyncPushRequest
import com.jizhang.app.data.remote.dto.TokenDto
import com.jizhang.app.data.remote.dto.TransactionDto
import com.jizhang.app.data.remote.dto.TransactionSaveDto
import com.jizhang.app.data.remote.dto.TrendItemDto
import com.jizhang.app.data.remote.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 后端接口定义。baseUrl 见 BuildConfig.API_BASE_URL，已包含 /api/v1/ 前缀。
 */
interface ApiService {

    /* ---------------- 认证 ---------------- */

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResult<TokenDto>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiResult<UserDto>

    /** 用 refreshToken 换新的令牌对（TokenAuthenticator 内部使用） */
    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): ApiResult<TokenDto>

    @GET("auth/me")
    suspend fun me(): ApiResult<UserDto>

    /* ---------------- 账本 / 账户 / 分类 ---------------- */

    @GET("app/books")
    suspend fun books(): ApiResult<List<BookDto>>

    @GET("app/accounts")
    suspend fun accounts(@Query("bookId") bookId: Long): ApiResult<List<AccountDto>>

    @GET("app/categories")
    suspend fun categories(
        @Query("bookId") bookId: Long,
        @Query("type") type: Int? = null
    ): ApiResult<List<CategoryDto>>

    /* ---------------- 交易 ---------------- */

    @GET("app/transactions")
    suspend fun transactions(
        @Query("bookId") bookId: Long,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
        @Query("type") type: Int? = null,
        @Query("keyword") keyword: String? = null
    ): ApiResult<PageResult<TransactionDto>>

    @POST("app/transactions")
    suspend fun createTransaction(@Body request: TransactionSaveDto): ApiResult<TransactionDto>

    @DELETE("app/transactions/{id}")
    suspend fun deleteTransaction(@Path("id") id: Long): ApiResult<Unit>

    /* ---------------- 同步 ---------------- */

    @POST("app/sync/pull")
    suspend fun syncPull(@Body request: SyncPullRequest): ApiResult<SyncPullDto>

    @POST("app/sync/push")
    suspend fun syncPush(@Body request: SyncPushRequest): ApiResult<SyncPushDto>

    /* ---------------- 报表 ---------------- */

    @GET("app/reports/overview")
    suspend fun reportOverview(
        @Query("bookId") bookId: Long,
        @Query("month") month: String? = null
    ): ApiResult<ReportOverviewDto>

    @GET("app/reports/category")
    suspend fun reportCategory(
        @Query("bookId") bookId: Long,
        @Query("type") type: Int? = 1
    ): ApiResult<CategoryReportDto>

    @GET("app/reports/trend")
    suspend fun reportTrend(
        @Query("bookId") bookId: Long,
        @Query("granularity") granularity: String = "day"
    ): ApiResult<List<TrendItemDto>>

    @GET("app/reports/assets")
    suspend fun reportAssets(@Query("bookId") bookId: Long): ApiResult<AssetReportDto>
}
