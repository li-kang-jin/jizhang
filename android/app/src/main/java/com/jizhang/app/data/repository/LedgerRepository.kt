package com.jizhang.app.data.repository

import com.jizhang.app.data.local.JizhangDatabase
import com.jizhang.app.data.local.dao.AccountDao
import com.jizhang.app.data.local.dao.CategoryDao
import com.jizhang.app.data.local.dao.TransactionDao
import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.remote.ApiService
import com.jizhang.app.data.remote.dto.AccountSaveDto
import com.jizhang.app.data.remote.dto.SyncPullRequest
import kotlinx.coroutines.flow.Flow
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
    private val transactionDao: TransactionDao,
    private val budgetRepository: BudgetRepository
) {

    suspend fun cachedCategories(bookId: Long, type: Int): List<LocalCategory> =
        categoryDao.listByBookAndType(bookId, type)

    suspend fun cachedAccounts(bookId: Long): List<LocalAccount> = accountDao.listByBook(bookId)

    /** 全部分类（支出 + 收入），账单页筛选用 */
    fun observeCategories(bookId: Long): Flow<List<LocalCategory>> = categoryDao.observeByBook(bookId)

    /**
     * 账户列表的变化流。
     * <p>
     * 必须用 Flow：在「账户管理 / 分类管理」里新建后，账单页的筛选项要立刻能看到，
     * 一次性读取会因为「同一账本不重复绑定」的早退而停留在旧数据。
     */
    fun observeAccounts(bookId: Long): Flow<List<LocalAccount>> = accountDao.observeByBook(bookId)

    /**
     * 把本地账本的账户与分类对齐到服务端。
     * <p>
     * 使用场景：导入账单时会按名称自动新建账户 / 分类。如果导入时本地缓存还是空的
     * （新装、刚清过数据、或登录后还没拉取完就导入），这批账户就只存在于本地且 id 是本地生成的；
     * 推送交易时服务端校验不过（「账户不存在」code 3001）整批回滚，
     * 表现为「待同步一直有、点同步却上传 0 条」。
     * <p>
     * 这里把服务端缺的账户建出来、把同名账户的本地引用改写成服务端 ID，
     * 使推送能正常落库；分类只做匹配改写（客户端没有建分类的接口）。
     * 只用于服务端账本：本地账本没有服务端副本，直接跳过。
     *
     * @return 新建到服务端的账户数
     */
    suspend fun reconcileLedger(bookId: Long): Int {
        if (bookId == JizhangDatabase.LOCAL_BOOK_ID || tokenStore.accessToken().isNullOrBlank()) {
            return 0
        }
        val created = reconcileAccounts(bookId)
        reconcileCategories(bookId)
        return created
    }

    /** 账户：同名同类型复用（并把本地引用改写成服务端 ID），服务端没有的就建出来 */
    private suspend fun reconcileAccounts(bookId: Long): Int {
        val localAccounts = accountDao.listByBook(bookId)
        if (localAccounts.isEmpty()) {
            return 0
        }

        val remoteAccounts = api.accounts(bookId).data.orEmpty().toMutableList()
        val keptLocalIds = mutableSetOf<Long>()
        var created = 0

        localAccounts.forEach { account ->
            val matched = remoteAccounts.firstOrNull {
                it.type == account.type && it.name.trim().equals(account.name.trim(), ignoreCase = true)
            }
            if (matched != null) {
                remapAccount(account.id, matched.id)
                return@forEach
            }
            val createdAccount = api.createAccount(
                AccountSaveDto(
                    bookId = bookId,
                    name = account.name,
                    type = account.type,
                    initialBalance = account.initialBalance,
                    icon = account.icon,
                    sort = account.sort
                )
            ).data
            if (createdAccount == null) {
                // 建不出来就保留本地行，别让交易的账户引用悬空
                keptLocalIds += account.id
                return@forEach
            }
            created++
            remoteAccounts += createdAccount
            remapAccount(account.id, createdAccount.id)
        }

        // 本地账户缓存整体换成服务端数据，保证 id 与服务端一致（拉取时才不会错位）
        val keptRows = localAccounts.filter { it.id in keptLocalIds }
        accountDao.deleteByBook(bookId)
        accountDao.upsertAll(remoteAccounts.map { it.toLocal() } + keptRows)
        return created
    }

    /**
     * 分类：服务端已有同名同类型分类时，把本地交易的分类引用改成服务端 ID，
     * 并删掉本地那条自建分类 —— 否则它会和服务端拉回来的同名分类在本地并存，
     * 分类管理里出现两个「餐饮」，一个挂着全部账单、一个空的。
     */
    private suspend fun reconcileCategories(bookId: Long) {
        val remoteCategories = api.categories(bookId, null).data.orEmpty()
            .flatMap { listOf(it) + it.children }
        if (remoteCategories.isEmpty()) {
            return
        }

        categoryDao.listByBook(bookId).forEach { local ->
            val matched = remoteCategories.firstOrNull {
                it.type == local.type && it.name.trim().equals(local.name.trim(), ignoreCase = true)
            } ?: return@forEach
            if (matched.id == local.id) {
                return@forEach
            }
            transactionDao.migrateCategory(local.id, matched.id)
            categoryDao.deleteByIds(listOf(local.id))
        }
    }

    /** 账户 id 变化后，改写本地交易对该账户的引用（含转账的对方账户） */
    private suspend fun remapAccount(oldId: Long, newId: Long) {
        if (oldId == newId) {
            return
        }
        transactionDao.migrateAccount(oldId, newId)
        transactionDao.migrateToAccount(oldId, newId)
    }

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

        // 预算额度跨设备 / 家庭成员全靠这里带回（同步推送协议里没有预算字段）
        if (data.budgets.isNotEmpty() || data.deletedBudgetIds.isNotEmpty()) {
            budgetRepository.mergeFromServer(bookId, data.budgets, data.deletedBudgetIds)
        }

        // 固定支出同理：规则本身由 REST 写入，这里只把别的设备 / 家庭成员改过的规则拉回来
        if (data.recurringExpenses.isNotEmpty() || data.deletedRecurringExpenseIds.isNotEmpty()) {
            budgetRepository.mergeRecurringFromServer(
                bookId,
                data.recurringExpenses,
                data.deletedRecurringExpenseIds
            )
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
