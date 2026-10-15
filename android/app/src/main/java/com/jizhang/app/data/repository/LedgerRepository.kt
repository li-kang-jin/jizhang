package com.jizhang.app.data.repository

import com.jizhang.app.data.local.dao.AccountDao
import com.jizhang.app.data.local.dao.CategoryDao
import com.jizhang.app.data.local.dao.TransactionDao
import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.remote.ApiService
import com.jizhang.app.data.remote.dto.SyncPullRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 账本数据仓库：把服务端数据同步到本地缓存（账户 / 分类 / 交易）。
 */
@Singleton
class LedgerRepository @Inject constructor(
    private val api: ApiService,
    private val tokenStore: TokenStore,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao
) {

    suspend fun cachedCategories(bookId: Long, type: Int): List<LocalCategory> =
        categoryDao.listByBookAndType(bookId, type)

    suspend fun cachedAccounts(bookId: Long): List<LocalAccount> = accountDao.listByBook(bookId)

    /**
     * 增量拉取服务端变更并落库。
     *
     * @return 本次写入本地的交易条数
     */
    suspend fun pull(bookId: Long): Int {
        val cursor = tokenStore.lastSyncAt(bookId)?.takeIf { it.isNotBlank() }
        val result = api.syncPull(
            SyncPullRequest(
                deviceId = tokenStore.deviceId(),
                bookId = bookId,
                lastSyncAt = cursor
            )
        )
        val data = result.data ?: error(result.message.ifBlank { "同步失败" })

        if (data.categories.isNotEmpty()) {
            categoryDao.upsertAll(data.categories.map { it.toLocal() })
        }
        if (data.deletedCategoryIds.isNotEmpty()) {
            categoryDao.deleteByIds(data.deletedCategoryIds)
        }

        if (data.accounts.isNotEmpty()) {
            accountDao.upsertAll(data.accounts.map { it.toLocal() })
        }
        if (data.deletedAccountIds.isNotEmpty()) {
            accountDao.deleteByIds(data.deletedAccountIds)
        }

        if (data.transactions.isNotEmpty()) {
            // 本地已标记删除、但删除动作还没推上去的记录，不能被服务端的旧数据覆盖
            val pendingDeleteIds = transactionDao.listAllPendingDeleteIds().toSet()
            val incoming = data.transactions
                .filter { it.id !in pendingDeleteIds }
                .map { it.toLocal() }
            if (incoming.isNotEmpty()) {
                transactionDao.upsertAll(incoming)
            }
        }
        if (data.deletedTransactionIds.isNotEmpty()) {
            transactionDao.deleteByIds(data.deletedTransactionIds)
        }

        // 用服务端返回的 nextCursor 推进游标（不要用 serverTime，分页截断时会漏数据）
        val next = data.nextCursor?.takeIf { it.isNotBlank() }
            ?: data.serverTime?.takeIf { it.isNotBlank() }
        next?.let { tokenStore.saveLastSyncAt(bookId, it) }

        return data.transactions.size
    }

    /** 全量同步：清空该账本的游标后重新拉取 */
    suspend fun fullPull(bookId: Long): Int {
        tokenStore.saveLastSyncAt(bookId, "")
        return pull(bookId)
    }
}
