package com.jizhang.app.data.repository

import com.jizhang.app.data.local.JizhangDatabase
import com.jizhang.app.data.local.dao.CategoryUsageRow
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

/** 转账的账单类型（1=支出 2=收入 3=转账，与 `LocalTransaction.type` 一致） */
private const val TRANSFER_TYPE = 3

/**
 * 「常用账户」的统计窗口：最近这么多笔账单。
 * <p>
 * 约等于几个月的日常记账量，既够稳定（不会被某一两笔带偏），又能让半年前的习惯自然退出，
 * 不必再维护「最近使用时间」之类的额外字段。
 */
private const val ACCOUNT_USAGE_WINDOW = 200

/**
 * 交易仓库。离线优先：写操作先落本地，再由 [SyncManager] 统一推送到服务端。
 */
@Singleton
class TransactionRepository @Inject constructor(
    private val api: ApiService,
    private val tokenStore: TokenStore,
    private val transactionDao: TransactionDao,
    private val investmentRepository: InvestmentRepository
) {

    fun observeTransactions(bookId: Long): Flow<List<LocalTransaction>> =
        transactionDao.observeByBook(bookId)

    fun observePendingCount(bookId: Long): Flow<Int> = transactionDao.observePendingCount(bookId)

    suspend fun listLocal(bookId: Long): List<LocalTransaction> = transactionDao.listByBook(bookId)

    /**
     * 某账户名下还有多少笔账单（转出 / 转入都算）。
     * <p>
     * 删除账户前要先问一句：账户一旦消失，挂在上面的账单就失去了依据
     * （同步推送时服务端会整批拒绝），所以有引用就该拦下来。
     */
    suspend fun countByAccount(accountId: Long): Int = transactionDao.countByAccount(accountId)

    /**
     * 区间内的账单明细（左闭右开）。
     * <p>
     * 给日历模块按月取数用：一次取整月，点某天就不必再查库。
     */
    suspend fun listBetween(bookId: Long, start: Long, end: Long): List<LocalTransaction> =
        transactionDao.listBetween(bookId, start, end)

    /** 最早一笔账单的时刻（日历的下界）；一笔都没有时为 null */
    suspend fun minTradeTime(bookId: Long): Long? = transactionDao.minTradeTime(bookId)

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

    /** 某个分类下最近若干条带标签账单的标签原文，记账页用它算「这个分类的常用标签」 */
    suspend fun recentTagsByCategory(bookId: Long, categoryId: Long, limit: Int): List<String> =
        transactionDao.recentTagsByCategory(bookId, categoryId, limit)

    /**
     * 每个账户被用过的次数（含转账的转入方），记账页据此只铺三个常用账户。
     * <p>
     * 口径是**最近 [ACCOUNT_USAGE_WINDOW] 笔**账单，不是全历史：换过卡、停用旧账户之后，
     * 全历史次数会长期压住真正在用的账户，芯片上挂着的就不再是「当下常用」的那几个。
     * 同一笔转账的出账方与入账方各算一次（两边都确实动过这个账户）。
     */
    suspend fun accountUsage(bookId: Long): Map<Long, Long> =
        transactionDao.recentAccountUses(bookId, ACCOUNT_USAGE_WINDOW)
            .flatMap { row ->
                val to = row.toAccountId
                if (row.type == TRANSFER_TYPE && to != null) {
                    listOf(row.accountId, to)
                } else {
                    listOf(row.accountId)
                }
            }
            .groupingBy { it }
            .eachCount()
            .mapValues { it.value.toLong() }

    /** 每个分类被用过的次数（按收支类型分开），记账页据此决定新建账单的默认分类 */
    suspend fun categoryUsage(bookId: Long, type: Int): Map<Long, Long> =
        transactionDao.categoryUsage(bookId, type).associate { it.categoryId to it.cnt }

    /** 每个分类的笔数与金额合计：分类管理页用它显示用量、判断该把哪个合并掉 */
    suspend fun categoryUsageDetail(bookId: Long, type: Int): List<CategoryUsageRow> =
        transactionDao.categoryUsage(bookId, type)

    /**
     * 把 [fromCategoryId] 下的账单整体改到 [toCategoryId]（分类合并）。
     * <p>
     * 逐笔改成 upsert 也能做，但一次 UPDATE 更省事，也不会在合并中途失败时留下「改了一半」的状态；
     * 待同步标记由 SQL 一并打上，登录状态下走常规同步推到服务端。
     *
     * @return 改动的笔数
     */
    suspend fun reassignCategory(bookId: Long, fromCategoryId: Long, toCategoryId: Long): Int =
        transactionDao.reassignCategory(
            bookId = bookId,
            fromCategoryId = fromCategoryId,
            toCategoryId = toCategoryId,
            now = System.currentTimeMillis()
        )

    /** 最近的带备注或标签的账单，记账页用它把备注 / 标签映射回分类（见 [CategorySuggester]） */
    suspend fun noteSamples(bookId: Long, limit: Int): List<NoteSample> =
        transactionDao.recentNotes(bookId, limit).map {
            NoteSample(
                remark = it.remark,
                tags = it.tags,
                categoryId = it.categoryId,
                tradeTime = it.tradeTime
            )
        }

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
        // 先解除投资关联：这笔转账如果记到某个品种上，成本必须跟着回退，
        // 否则删了账成本还挂着，盈亏会凭空好看一截
        investmentRepository.detachTransfer(id)
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
        // 账单全没了，挂在转账上的投资成本也没有意义：两个分支都要清
        investmentRepository.clearFlows(bookId)
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
        // 冲突：服务端版本比本地新（last-write-wins），这类记录服务端**不会**放进 successIds。
        // 早先这里完全忽略 conflicts，导致冲突的那条永远 pendingSync=1、每次同步重复推、
        // 界面却显示「上传 0 条」的正常结果。现在按服务端给的版本覆盖本地并清掉待同步标记。
        val conflictIds = deleteIds.toSet()
        val conflicts = data.conflicts.filter { it.bookId == bookId && it.id !in conflictIds }
        if (conflicts.isNotEmpty()) {
            transactionDao.upsertAll(conflicts.map { it.toLocal() })
            // toLocal 已把 pendingSync 置 false，这里再标一次，防 upsert 被同 id 的旧行覆盖
            transactionDao.markSynced(conflicts.map { it.id })
        }
        // 服务端删除是幂等的，请求成功即可安全清理本地记录
        if (deleteIds.isNotEmpty()) {
            transactionDao.deleteByIds(deleteIds)
        }

        // 推了东西却一条都没被接收（既没成功也没冲突）：说明服务端静默丢包，
        // 本地记录原样留着并按失败上报，避免又变成「同步完成，上传 0 条」
        if (pending.isNotEmpty() && data.successIds.isEmpty() && conflicts.isEmpty()) {
            return PushResult(
                updated = 0,
                deleted = deleteIds.size,
                error = "服务端未接收这 ${pending.size} 条记录，请稍后重试"
            )
        }
        return PushResult(
            updated = data.successIds.size,
            deleted = deleteIds.size,
            conflicted = conflicts.size
        )
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
    /** 服务端版本更新、已按服务端覆盖本地的条数 */
    val conflicted: Int = 0,
    /** 服务端拒绝时返回的业务错误信息（为空表示推送成功） */
    val error: String? = null
) {
    val total: Int get() = updated + deleted

    /** 是否被服务端整批拒绝（本地待同步记录原样保留） */
    val failed: Boolean get() = error != null
}
