package com.jizhang.app.data.repository

import com.jizhang.app.data.local.JizhangDatabase
import com.jizhang.app.data.local.dao.SumAmountRow
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

    /** 本地可见账单数（不含已标记删除的） */
    suspend fun countLocal(bookId: Long): Int = transactionDao.countByBook(bookId)

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
     * 更新一笔已有记账。
     * <p>
     * 保留原 id 与 createdBy：同步协议按客户端 id 幂等（服务端该 id 不存在就新建、存在就更新），
     * 一旦换了 id 就会在服务端多出一笔。修改后重新打上待同步标记，断网修改也不会丢。
     *
     * @return 是否更新成功；目标账单已被删除（或不存在）时返回 false
     */
    suspend fun updateLocal(
        id: Long,
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
    ): Boolean {
        val exist = transactionDao.findById(id) ?: return false
        // 已标记删除的账单不再接受编辑：否则「删除」和「修改」两个动作会在同步时打架
        if (exist.pendingDelete) {
            return false
        }
        transactionDao.upsert(
            exist.copy(
                type = type,
                amount = amount,
                accountId = accountId,
                toAccountId = toAccountId,
                categoryId = categoryId,
                tradeTime = tradeTime,
                remark = remark,
                tags = tags,
                pendingSync = true
            )
        )
        return true
    }

    /** 最近若干条带标签的账单的标签原文，记账页用它算「常用标签」 */
    suspend fun recentTags(bookId: Long, limit: Int): List<String> =
        transactionDao.recentTags(bookId, limit)

    /** 最近记过的若干笔账，记账页「再来一笔」模板用它做照抄 */
    suspend fun recentTransactions(bookId: Long, limit: Int): List<LocalTransaction> =
        transactionDao.recentTransactions(bookId, limit)

    /** 某个账户的流水（转出 / 转入都算），账户详情页用 */
    suspend fun listByAccount(accountId: Long, limit: Int): List<LocalTransaction> =
        transactionDao.listByAccount(accountId, limit)

    /** 某账户区间内的收支汇总（不含转账） */
    suspend fun sumByAccount(accountId: Long, start: Long, end: Long): SumAmountRow =
        transactionDao.sumByAccount(accountId, start, end)

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
     * 清空整本账本的账单（导入脏数据的清理入口）。
     * <p>
     * 已登录的服务端账本：只打「待删除」标记，删除动作交给同步推送（服务端逻辑删除并
     * 回滚账户余额），推送成功后本地才物理删除，断网也不会「删了又回来」。
     * 本地账本（未登录）没有服务端副本，直接物理删除，避免留下永远推不出去的墓碑行。
     *
     * @return 清掉的条数，以及是否需要立刻同步才能让服务端一并删除
     */
    suspend fun clearBook(bookId: Long): ClearResult {
        val count = transactionDao.countByBook(bookId)
        if (count == 0) {
            return ClearResult(0, needSync = false)
        }

        val localOnly = bookId == JizhangDatabase.LOCAL_BOOK_ID ||
            tokenStore.accessToken().isNullOrBlank()
        if (localOnly) {
            transactionDao.deleteByBook(bookId)
            return ClearResult(count, needSync = false)
        }

        transactionDao.markAllPendingDelete(bookId)
        return ClearResult(count, needSync = true)
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
        // 业务失败（例如导入时自动新建的账户还不在服务端 → 服务端整批拒绝）时 data 为空，
        // 这里必须把服务端的原因带出去，否则 UI 会显示成「同步完成：上传 0 条」而看不出出错
        val data = result.data
            ?: return PushResult(0, 0, error = result.message.ifBlank { "同步推送失败" })

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

/** 清空账本账单的结果 */
data class ClearResult(
    /** 清掉的账单条数 */
    val count: Int,
    /** 是否还需要同步一次才能让服务端一并删除（本地账本为 false） */
    val needSync: Boolean
)

/** 一次推送的结果 */
data class PushResult(
    /** 推送成功的新增 / 修改条数 */
    val updated: Int,
    /** 推送成功的删除条数 */
    val deleted: Int,
    /** 服务端拒绝时返回的业务错误信息（为空表示推送成功） */
    val error: String? = null
) {
    val total: Int get() = updated + deleted

    /** 是否被服务端整批拒绝（本地待同步记录原样保留） */
    val failed: Boolean get() = error != null
}
