package com.jizhang.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class TransactionDto(
    val id: Long,
    val bookId: Long,
    /** 1=支出 2=收入 3=转账 */
    val type: Int,
    /** 金额（分） */
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    /** ISO 8601 UTC */
    val tradeTime: String,
    val remark: String? = null,
    val images: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val source: Int = 1,
    val updatedAt: String? = null
)

@Serializable
data class TransactionSaveDto(
    val id: Long? = null,
    val bookId: Long,
    val type: Int,
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val tradeTime: String,
    val remark: String? = null,
    val images: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val source: Int = 1,
    /** 仅同步场景使用，用于冲突判定 */
    val updatedAt: String? = null
)
