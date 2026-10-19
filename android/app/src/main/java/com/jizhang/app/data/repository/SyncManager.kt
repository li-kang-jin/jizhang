package com.jizhang.app.data.repository

import com.jizhang.app.data.local.dao.BookDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /**
     * 同步串行化。
     * <p>
     * 「进应用时的全量同步」和「账单页自己的同步」会几乎同时触发，两条线跑同一个账本
     * 会重复推送、重复跑自愈（曾因此各建出一个兜底账户），所以统一排队执行。
     */
    private val syncLock = Mutex()

    /** 执行单个账本的完整同步。 */
    suspend fun sync(bookId: Long): Result<SyncOutcome> = runCatching { doSync(bookId) }

    /** 首次进入应用或手动触发时的全量同步。 */
    suspend fun fullSync(bookId: Long): Result<SyncOutcome> = runCatching {
        val push = pushWithRepair(bookId)
        pushPendingExtras(bookId)
        val pulled = ledgerRepository.fullPull(bookId)
        SyncOutcome(
            pushed = push.updated,
            deleted = push.deleted,
            pulled = pulled,
            conflicts = push.conflicted
        )
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
        var conflicts = 0
        val failed = mutableListOf<String>()

        for (book in books) {
            runCatching { doSync(book.id) }
                .onSuccess {
                    pushed += it.pushed
                    deleted += it.deleted
                    pulled += it.pulled
                    conflicts += it.conflicts
                }
                // 注意要用 ${book.name}：写成 "$book.name" 会把整个数据类 toString 出来再接上 ".name"
                .onFailure { failed += "${book.name}（${it.message ?: "未知错误"}）" }
        }
        SyncSummary(
            pushed = pushed,
            deleted = deleted,
            pulled = pulled,
            conflicts = conflicts,
            failedBooks = failed
        )
    }

    /**
     * 推送本地变更；被服务端整批拒绝时自动修复一次再重试。
     * <p>
     * 服务端推送是整批事务，一条不过就全批回滚，所以两种「账户对不上」的脏数据必须先在本地修好：
     * 1. 导入账单时自动新建的账户只存在于本地 —— [LedgerRepository.reconcileLedger] 把它们建到服务端并改写引用；
     * 2. 账单挂着的账户已经被删掉了（本地只剩一个对不上号的 id）——
     *    [LedgerRepository.repairDanglingAccountRefs] 把引用改指到兜底账户。
     * 修完重试一次；仍旧失败才把服务端给的原因抛给上层。
     */
    private suspend fun pushWithRepair(bookId: Long): PushResult {
        var push = transactionRepository.pushPending(bookId)
        if (push.failed) {
            ledgerRepository.reconcileLedger(bookId)
            ledgerRepository.repairDanglingAccountRefs(bookId)
            push = transactionRepository.pushPending(bookId)
        }
        return push
    }

    /** 排队执行，避免两条同步线同时跑同一个账本（见 [syncLock]） */
    private suspend fun doSync(bookId: Long): SyncOutcome = syncLock.withLock {
        runSync(bookId)
    }

    private suspend fun runSync(bookId: Long): SyncOutcome {
        val push = pushWithRepair(bookId)
        // 修复后仍被拒绝：本地记录原样留着，把服务端返回的原因抛给上层展示，
        // 不要报告成「上传 0 条」这种看起来正常的结果
        if (push.failed) {
            error(push.error ?: "同步推送失败")
        }
        pushPendingExtras(bookId)
        val pulled = ledgerRepository.pull(bookId)
        return SyncOutcome(
            pushed = push.updated,
            deleted = push.deleted,
            pulled = pulled,
            conflicts = push.conflicted
        )
    }
}

data class SyncOutcome(
    /** 推送成功的新增 / 修改条数 */
    val pushed: Int,
    /** 推送成功的删除条数 */
    val deleted: Int,
    /** 拉取到的交易条数 */
    val pulled: Int,
    /** 服务端版本更新、已按服务端覆盖本地的条数 */
    val conflicts: Int = 0
)

/** 多账本同步的汇总结果 */
data class SyncSummary(
    val pushed: Int,
    val deleted: Int,
    val pulled: Int,
    /** 服务端版本更新、已按服务端覆盖本地的条数 */
    val conflicts: Int = 0,
    /** 同步失败的账本名 */
    val failedBooks: List<String> = emptyList()
) {
    val bookCount: Int get() = failedBooks.size
}
