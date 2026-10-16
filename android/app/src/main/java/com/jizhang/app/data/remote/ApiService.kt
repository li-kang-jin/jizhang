package com.jizhang.app.data.remote

import com.jizhang.app.data.remote.dto.AccountDto
import com.jizhang.app.data.remote.dto.AccountSaveDto
import com.jizhang.app.data.remote.dto.ApiResult
import com.jizhang.app.data.remote.dto.AssetReportDto
import com.jizhang.app.data.remote.dto.BookDto
import com.jizhang.app.data.remote.dto.BookInviteDto
import com.jizhang.app.data.remote.dto.BookMemberDto
import com.jizhang.app.data.remote.dto.BookSaveDto
import com.jizhang.app.data.remote.dto.BudgetDto
import com.jizhang.app.data.remote.dto.BudgetOverviewDto
import com.jizhang.app.data.remote.dto.BudgetSaveDto
import com.jizhang.app.data.remote.dto.CategoryDto
import com.jizhang.app.data.remote.dto.CategoryReportDto
import com.jizhang.app.data.remote.dto.CategorySaveDto
import com.jizhang.app.data.remote.dto.InviteCreateDto
import com.jizhang.app.data.remote.dto.JoinBookDto
import com.jizhang.app.data.remote.dto.LoginRequest
import com.jizhang.app.data.remote.dto.MemberReportDto
import com.jizhang.app.data.remote.dto.MemberUpdateDto
import com.jizhang.app.data.remote.dto.PageResult
import com.jizhang.app.data.remote.dto.RecurringExpenseDto
import com.jizhang.app.data.remote.dto.RecurringExpenseSaveDto
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
import retrofit2.http.PUT
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

    @POST("app/books")
    suspend fun createBook(@Body request: BookSaveDto): ApiResult<BookDto>

    @PUT("app/books/{id}")
    suspend fun updateBook(
        @Path("id") id: Long,
        @Body request: BookSaveDto
    ): ApiResult<BookDto>

    @DELETE("app/books/{id}")
    suspend fun deleteBook(@Path("id") id: Long): ApiResult<Unit>

    /* ---------------- 账本成员 / 邀请码 ---------------- */

    @GET("app/books/{id}/members")
    suspend fun bookMembers(@Path("id") id: Long): ApiResult<List<BookMemberDto>>

    @PUT("app/books/{id}/members/{userId}")
    suspend fun updateBookMember(
        @Path("id") id: Long,
        @Path("userId") userId: Long,
        @Body request: MemberUpdateDto
    ): ApiResult<Unit>

    @DELETE("app/books/{id}/members/{userId}")
    suspend fun removeBookMember(
        @Path("id") id: Long,
        @Path("userId") userId: Long
    ): ApiResult<Unit>

    @GET("app/books/{id}/invites")
    suspend fun bookInvites(@Path("id") id: Long): ApiResult<List<BookInviteDto>>

    @POST("app/books/{id}/invites")
    suspend fun createBookInvite(
        @Path("id") id: Long,
        @Body request: InviteCreateDto
    ): ApiResult<BookInviteDto>

    @DELETE("app/books/{id}/invites/{code}")
    suspend fun revokeBookInvite(
        @Path("id") id: Long,
        @Path("code") code: String
    ): ApiResult<Unit>

    @POST("app/books/join")
    suspend fun joinBook(@Body request: JoinBookDto): ApiResult<BookDto>

    @GET("app/accounts")
    suspend fun accounts(@Query("bookId") bookId: Long): ApiResult<List<AccountDto>>

    @POST("app/accounts")
    suspend fun createAccount(@Body request: AccountSaveDto): ApiResult<AccountDto>

    @PUT("app/accounts/{id}")
    suspend fun updateAccount(
        @Path("id") id: Long,
        @Body request: AccountSaveDto
    ): ApiResult<AccountDto>

    @DELETE("app/accounts/{id}")
    suspend fun deleteAccount(@Path("id") id: Long): ApiResult<Unit>

    @GET("app/categories")
    suspend fun categories(
        @Query("bookId") bookId: Long,
        @Query("type") type: Int? = null
    ): ApiResult<List<CategoryDto>>

    @POST("app/categories")
    suspend fun createCategory(@Body request: CategorySaveDto): ApiResult<CategoryDto>

    @PUT("app/categories/{id}")
    suspend fun updateCategory(
        @Path("id") id: Long,
        @Body request: CategorySaveDto
    ): ApiResult<CategoryDto>

    @DELETE("app/categories/{id}")
    suspend fun deleteCategory(@Path("id") id: Long): ApiResult<Unit>

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

    /** 家庭账本「谁花了多少」 */
    @GET("app/reports/member")
    suspend fun reportMember(
        @Query("bookId") bookId: Long,
        @Query("startTime") startTime: String? = null,
        @Query("endTime") endTime: String? = null
    ): ApiResult<MemberReportDto>

    /* ---------------- 预算 ---------------- */

    /** 周期用 cycle(1=月 2=周 3=年) + periodStart(yyyy-MM-dd) 表达；period 仅为兼容旧服务端 */
    @GET("app/budgets")
    suspend fun budgets(
        @Query("bookId") bookId: Long,
        @Query("cycle") cycle: Int? = null,
        @Query("periodStart") periodStart: String? = null,
        @Query("period") period: String? = null
    ): ApiResult<List<BudgetDto>>

    @GET("app/budgets/overview")
    suspend fun budgetOverview(
        @Query("bookId") bookId: Long,
        @Query("cycle") cycle: Int? = null,
        @Query("periodStart") periodStart: String? = null,
        @Query("period") period: String? = null,
        /** 本地时区的「今天」，与服务端 UTC 口径对齐，避免算出的每日额度差一天 */
        @Query("today") today: String? = null
    ): ApiResult<BudgetOverviewDto>

    @POST("app/budgets")
    suspend fun saveBudget(@Body request: BudgetSaveDto): ApiResult<Long>

    @DELETE("app/budgets/{id}")
    suspend fun deleteBudget(
        @Path("id") id: Long,
        @Query("bookId") bookId: Long
    ): ApiResult<Unit>

    /* ---------------- 固定支出 ---------------- */

    /** 全部固定支出（含已停用）。写走 REST，读也走 REST，跨设备一致性由同步拉取带回 */
    @GET("app/recurring-expenses")
    suspend fun recurringExpenses(@Query("bookId") bookId: Long): ApiResult<List<RecurringExpenseDto>>

    /** id 由客户端生成；同 id 重复提交视为修改 */
    @POST("app/recurring-expenses")
    suspend fun saveRecurringExpense(@Body request: RecurringExpenseSaveDto): ApiResult<Long>

    @DELETE("app/recurring-expenses/{id}")
    suspend fun deleteRecurringExpense(
        @Path("id") id: Long,
        @Query("bookId") bookId: Long
    ): ApiResult<Unit>
}
