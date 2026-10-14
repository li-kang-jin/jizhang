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
    val deletedBookIds: List<Long> = emptyList(),
    val deletedAccountIds: List<Long> = emptyList(),
    val deletedCategoryIds: List<Long> = emptyList(),
    val deletedTransactionIds: List<Long> = emptyList()
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
