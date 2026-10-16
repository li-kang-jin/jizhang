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
    private val budgetRepository: BudgetRepository,
    private val bookDao: BookDao
) {

    /** 执行单个账本的完整同步。 */
    suspend fun sync(bookId: Long): Result<SyncOutcome> = runCatching { doSync(bookId) }

    /** 首次进入应用或手动触发时的全量同步。 */
    suspend fun fullSync(bookId: Long): Result<SyncOutcome> = runCatching {
        val push = pushWithRepair(bookId)
        pushPendingExtras(bookId)
        val pulled = ledgerRepository.fullPull(bookId)
        SyncOutcome(pushed = push.updated, deleted = push.deleted, pulled = pulled)
    }

    /**
     * 补推本地待同步的预算与固定支出。
     * <p>
     * 同步推送协议只处理交易，这两类必须单独走 REST；离线期间改的、登录时从本地账本迁过来的
     * 都在这里补上。失败不影响交易同步（属于附属数据），所以异常直接吞掉。
     */
    private suspend fun pushPendingExtras(bookId: Long) {
        runCatching { budgetRepository.pushPending(bookId) }
        runCatching { budgetRepository.pushPendingRecurring(bookId) }
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
                .onFailure { failed += "$book.name（${it.message ?: "未知错误"}）" }
        }
        SyncSummary(pushed = pushed, deleted = deleted, pulled = pulled, failedBooks = failed)
    }

    /**
     * 推送本地变更；被服务端整批拒绝时自动修复一次再重试。
     * <p>
     * 最常见的拒绝原因：导入账单时自动新建的账户只存在于本地，
     * 服务端校验不过（「账户不存在」）导致整批回滚。
     * 这里先把账户对齐到服务端（缺的建出来、本地引用改写），再重试一次。
     */
    private suspend fun pushWithRepair(bookId: Long): PushResult {
        var push = transactionRepository.pushPending(bookId)
        if (push.failed) {
            ledgerRepository.reconcileLedger(bookId)
            push = transactionRepository.pushPending(bookId)
        }
        return push
    }

    private suspend fun doSync(bookId: Long): SyncOutcome {
        val push = pushWithRepair(bookId)
        // 修复后仍被拒绝：本地记录原样留着，把服务端返回的原因抛给上层展示，
        // 不要报告成「上传 0 条」这种看起来正常的结果
        if (push.failed) {
            error(push.error ?: "同步推送失败")
        }
        pushPendingExtras(bookId)
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
