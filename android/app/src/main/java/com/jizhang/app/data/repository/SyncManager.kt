package com.jizhang.app.data.repository

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 同步编排器。
 * <p>
 * 顺序很重要：**先推送本地变更，再拉取服务端增量**。
 * 这样本地待同步的记录不会在拉取阶段被服务端的旧版本覆盖。
 */
@Singleton
class SyncManager @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val ledgerRepository: LedgerRepository
) {

    /**
     * 执行一次完整同步。
     */
    suspend fun sync(bookId: Long): Result<SyncOutcome> = runCatching {
        val push = transactionRepository.pushPending(bookId)
        val pulled = ledgerRepository.pull(bookId)
        SyncOutcome(pushed = push.updated, deleted = push.deleted, pulled = pulled)
    }

    /** 首次进入应用或手动触发时的全量同步 */
    suspend fun fullSync(bookId: Long): Result<SyncOutcome> = runCatching {
        val push = transactionRepository.pushPending(bookId)
        val pulled = ledgerRepository.fullPull(bookId)
        SyncOutcome(pushed = push.updated, deleted = push.deleted, pulled = pulled)
    }
}

data class SyncOutcome(
    /** 推送成功的新增 / 修改条数 */
    val pushed: Int,
    /** 推送成功的删除条数 */
    val deleted: Int,
    /** 拉取到的交易条数 */
    val pulled: Int
)
