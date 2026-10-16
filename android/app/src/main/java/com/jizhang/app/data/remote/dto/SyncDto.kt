package com.jizhang.app.data.remote.dto

import kotlinx.serialization.Serializable

/* ---------------- 同步 ---------------- */

@Serializable
data class SyncPullRequest(
    val deviceId: String,
    val bookId: Long,
    val lastSyncAt: String? = null
)

@Serializable
data class SyncPullDto(
    val serverTime: String? = null,
    /**
     * 下一次拉取应使用的游标（服务端计算）。
     * 不要用 serverTime 当游标，否则交易被条数上限截断时会漏数据。
     */
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val books: List<BookDto> = emptyList(),
    val accounts: List<AccountDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList(),
    val transactions: List<TransactionDto> = emptyList(),
    /**
     * 预算额度（不含进度，进度由客户端按本地流水实时聚合）。
     * <p>
     * 预算的**写**走 REST 接口，这里只负责把别的设备 / 家庭成员改过的额度拉回来。
     */
    val budgets: List<BudgetDto> = emptyList(),
    /**
     * 固定支出规则（不含「本期预留多少」，那由客户端按本地时区实时算）。
     * <p>
     * 与预算一样，**写**走 REST 接口，这里只把别的设备 / 家庭成员改过的规则拉回来。
     */
    val recurringExpenses: List<RecurringExpenseDto> = emptyList(),
    val deletedBookIds: List<Long> = emptyList(),
    val deletedAccountIds: List<Long> = emptyList(),
    val deletedCategoryIds: List<Long> = emptyList(),
    val deletedTransactionIds: List<Long> = emptyList(),
    val deletedBudgetIds: List<Long> = emptyList(),
    val deletedRecurringExpenseIds: List<Long> = emptyList()
)

@Serializable
data class SyncPushRequest(
    val deviceId: String,
    val bookId: Long,
    val transactions: List<TransactionSaveDto> = emptyList(),
    /** 离线期间删除的交易 ID */
    val deletedTransactionIds: List<Long> = emptyList()
)

@Serializable
data class SyncPushDto(
    val successIds: List<Long> = emptyList(),
    val conflicts: List<TransactionDto> = emptyList(),
    val serverTime: String? = null
)

/* ---------------- 报表 ---------------- */

@Serializable
data class ReportOverviewDto(
    val month: String = "",
    val income: Long = 0,
    val expense: Long = 0,
    val balance: Long = 0,
    val incomeRatio: Double? = null,
    val expenseRatio: Double? = null
)

@Serializable
data class CategoryStatDto(
    val categoryId: Long? = null,
    val categoryName: String = "未分类",
    val icon: String? = null,
    val color: String? = null,
    val amount: Long = 0,
    val ratio: Double = 0.0
)

@Serializable
data class CategoryReportDto(
    val total: Long = 0,
    val items: List<CategoryStatDto> = emptyList()
)

@Serializable
data class TrendItemDto(
    val period: String = "",
    val income: Long = 0,
    val expense: Long = 0,
    val balance: Long = 0
)

@Serializable
data class AccountAssetDto(
    val accountId: Long,
    val name: String,
    val type: Int? = null,
    val icon: String? = null,
    val balance: Long = 0,
    val includeInTotal: Int? = null
)

@Serializable
data class AssetReportDto(
    val totalAssets: Long = 0,
    val accounts: List<AccountAssetDto> = emptyList()
)
