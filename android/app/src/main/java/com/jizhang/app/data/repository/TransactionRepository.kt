package com.jizhang.app.data.repository

import com.jizhang.app.data.local.dao.TransactionDao
import com.jizhang.app.data.local.entity.LocalTransaction
import com.jizhang.app.data.local.newClientId
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.remote.ApiService
import com.jizhang.app.data.remote.dto.SyncPushRequest
import com.jizhang.app.data.remote.dto.TransactionSaveDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 交易仓库。离线优先：写操作先落本地，再由 [SyncManager] 统一推送到服务端。
 */
@Singleton
class TransactionRepository @Inject constructor(
    private val api: ApiService,
    private val tokenStore: TokenStore,
    private val transactionDao: TransactionDao
) {

    fun observeTransactions(bookId: Long): Flow<List<LocalTransaction>> =
        transactionDao.observeByBook(bookId)

    fun observePendingCount(bookId: Long): Flow<Int> = transactionDao.observePendingCount(bookId)

    suspend fun listLocal(bookId: Long): List<LocalTransaction> = transactionDao.listByBook(bookId)

    /** 离线记账：只写本地，立即返回，联网后自动同步 */
    suspend fun addLocal(
        bookId: Long,
        type: Int,
        amount: Long,
        accountId: Long,
        categoryId: Long?,
        tradeTime: Long,
        remark: String?,
        /** 标签，多个用逗号分隔 */
        tags: String? = null,
        /** 转账的对方账户（type = 3 时使用） */
        toAccountId: Long? = null
    ): Long {
        val id = newClientId()
        transactionDao.upsert(
            LocalTransaction(
                id = id,
                bookId = bookId,
                type = type,
                amount = amount,
                accountId = accountId,
                toAccountId = toAccountId,
                categoryId = categoryId,
                tradeTime = tradeTime,
                remark = remark,
                tags = tags,
                pendingSync = true,
                pendingDelete = false
            )
        )
        return id
    }

    /**
     * 删除记账。
     * <p>
     * 只在本地打上待删除标记（UI 立刻不可见），真正的删除动作由同步任务推送到服务端。
     * 这样断网删除也不会「删了又回来」。
     */
    suspend fun deleteLocal(id: Long) {
        transactionDao.markPendingDelete(listOf(id))
    }

    /**
     * 推送本地变更（新增 / 修改 / 删除）。
     */
    suspend fun pushPending(bookId: Long): PushResult {
        val pending = transactionDao.listPending(bookId)
        val deleteIds = transactionDao.listPendingDeleteIds(bookId)
        if (pending.isEmpty() && deleteIds.isEmpty()) {
            return PushResult(0, 0)
        }

        val result = api.syncPush(
            SyncPushRequest(
                deviceId = tokenStore.deviceId(),
                bookId = bookId,
                transactions = pending.map { it.toSaveDto() },
                deletedTransactionIds = deleteIds
            )
        )
        val data = result.data ?: return PushResult(0, 0)

        if (data.successIds.isNotEmpty()) {
            transactionDao.markSynced(data.successIds)
        }
        // 服务端删除是幂等的，请求成功即可安全清理本地记录
        if (deleteIds.isNotEmpty()) {
            transactionDao.deleteByIds(deleteIds)
        }
        return PushResult(data.successIds.size, deleteIds.size)
    }

    /** 在线新增（不走本地队列，主要用于调试） */
    suspend fun createOnline(dto: TransactionSaveDto) = api.createTransaction(dto)
}

/** 一次推送的结果 */
data class PushResult(
    val updated: Int,
    val deleted: Int
) {
    val total: Int get() = updated + deleted
}
