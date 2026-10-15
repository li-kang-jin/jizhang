package com.jizhang.app.data.repository

import com.jizhang.app.data.local.dao.BookDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 同步编排器。
 * <p>
 * 顺序很重要：**先推送本地变更，再拉取服务端增量**。
 * 这样本地待同步的记录不会在拉取阶段被服务端的旧版本覆盖。
 * <p>
 * 多账本：每个账本有独立的同步游标（见 [com.jizhang.app.data.prefs.TokenStore]），
 * 进入应用或手动同步时用 [syncAll] 逐个账本跑一遍。
 */
@Singleton
class SyncManager @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val ledgerRepository: LedgerRepository,
    private val bookDao: BookDao
) {

    /** 执行单个账本的完整同步。 */
    suspend fun sync(bookId: Long): Result<SyncOutcome> = runCatching { doSync(bookId) }

    /** 首次进入应用或手动触发时的全量同步。 */
    suspend fun fullSync(bookId: Long): Result<SyncOutcome> = runCatching {
        val push = transactionRepository.pushPending(bookId)
        val pulled = ledgerRepository.fullPull(bookId)
        SyncOutcome(pushed = push.updated, deleted = push.deleted, pulled = pulled)
    }

    /**
     * 同步本机全部服务端账本（个人账本 + 所有家庭账本）。
     * <p>
     * 单个账本失败不影响其余账本，失败账本名会汇总到 [SyncSummary.failedBooks]。
     */
    suspend fun syncAll(): Result<SyncSummary> = runCatching {
        val books = bookDao.listRemote()
        var pushed = 0
        var deleted = 0
        var pulled = 0
        val failed = mutableListOf<String>()

        for (book in books) {
            runCatching { doSync(book.id) }
                .onSuccess {
                    pushed += it.pushed
                    deleted += it.deleted
                    pulled += it.pulled
                }
                .onFailure { failed += book.name }
        }
        SyncSummary(pushed = pushed, deleted = deleted, pulled = pulled, failedBooks = failed)
    }

    private suspend fun doSync(bookId: Long): SyncOutcome {
        val push = transactionRepository.pushPending(bookId)
        val pulled = ledgerRepository.pull(bookId)
        return SyncOutcome(pushed = push.updated, deleted = push.deleted, pulled = pulled)
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

/** 多账本同步的汇总结果 */
data class SyncSummary(
    val pushed: Int,
    val deleted: Int,
    val pulled: Int,
    /** 同步失败的账本名 */
    val failedBooks: List<String> = emptyList()
) {
    val bookCount: Int get() = failedBooks.size
}
