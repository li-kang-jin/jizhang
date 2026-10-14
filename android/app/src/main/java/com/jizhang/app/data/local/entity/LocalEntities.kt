package com.jizhang.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 本地交易记录。这是「离线记账」的核心表：
 * 新增的交易先落本地（pendingSync = true），再由同步任务推送到服务端。
 */
@Entity(
    tableName = "local_transaction",
    indices = [Index("bookId"), Index("pendingSync"), Index("pendingDelete")]
)
data class LocalTransaction(
    @PrimaryKey val id: Long,
    val bookId: Long,
    /** 1=支出 2=收入 3=转账 */
    val type: Int,
    /** 金额（分） */
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    /** 交易时间（epoch millis） */
    val tradeTime: Long,
    val remark: String? = null,
    /** 是否待同步到服务端 */
    val pendingSync: Boolean = true,
    /**
     * 是否已在本地删除、等待把删除动作同步到服务端。
     * <p>
     * 删除不能直接抹掉本地记录：那样断网时会丢失删除意图，
     * 服务端数据仍在，下次拉取又会把记录带回来。
     */
    val pendingDelete: Boolean = false,
    /** 本地最后修改时间（epoch millis），用于同步冲突判定 */
    val clientUpdatedAt: Long = System.currentTimeMillis(),
    /** 服务端返回的更新时间（ISO 8601），未同步时为 null */
    val serverUpdatedAt: String? = null
)

@Entity(tableName = "local_category", indices = [Index("bookId")])
data class LocalCategory(
    @PrimaryKey val id: Long,
    val bookId: Long,
    val parentId: Long = 0,
    val name: String,
    /** 1=支出 2=收入 */
    val type: Int,
    val icon: String? = null,
    val color: String? = null,
    val sort: Int = 0
)

@Entity(tableName = "local_account", indices = [Index("bookId")])
data class LocalAccount(
    @PrimaryKey val id: Long,
    val bookId: Long,
    val name: String,
    val type: Int,
    /** 余额（分） */
    val balance: Long = 0,
    val icon: String? = null,
    val sort: Int = 0
)
