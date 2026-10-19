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

    /** 全部分类（支出 + 收入），导出账单时用来把 categoryId 翻成名字并还原父子层级 */
    suspend fun allCategories(bookId: Long): List<LocalCategory> = categoryDao.listByBook(bookId)

    /**
     * 记账页可选的账户。
     *
     * @param includeArchived 编辑已有账单时必须带上归档账户：那笔账原本记在归档账户上，
     *                        过滤掉会让它在保存时被悄悄改成别的账户
     */
    suspend fun cachedAccounts(bookId: Long, includeArchived: Boolean = false): List<LocalAccount> =
        if (includeArchived) {
            accountDao.listByBook(bookId)
        } else {
            // 全都归档了（或一个都没有）时退回全量：否则记账页会「没有账户可选」，看起来像坏了
            accountDao.listActiveByBook(bookId).ifEmpty { accountDao.listByBook(bookId) }
        }

    /** 全部分类（支出 + 收入），账单页筛选用 */
    fun observeCategories(bookId: Long): Flow<List<LocalCategory>> = categoryDao.observeByBook(bookId)

    /**
     * 账户列表的变化流（只含未归档账户）。
     * <p>
     * 必须用 Flow：在「账户管理 / 分类管理」里新建后，账单页的筛选项要立刻能看到，
     * 一次性读取会因为「同一账本不重复绑定」的早退而停留在旧数据。
     * 归档账户不再出现在筛选里 —— 用户已经不用它了，留着只会让下拉变长。
     */
    fun observeAccounts(bookId: Long): Flow<List<LocalAccount>> =
        accountDao.observeActiveByBook(bookId)

    /**
     * 含归档账户的变化流。
     * <p>
     * 账单列表要给每一笔标出「从哪个账户出的」，那笔账可能记在后来被归档的账户上 ——
     * 用 [observeAccounts]（只有未归档）会把它显示成「没有账户」，所以这里取全量。
     */
    fun observeAllAccounts(bookId: Long): Flow<List<LocalAccount>> =
        accountDao.observeByBook(bookId)

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
        // 服务端 id → 本地那一行：整表替换时靠它把 note / archived 这些本机字段贴回去
        val localExtras = mutableMapOf<Long, LocalAccount>()
        var created = 0

        localAccounts.forEach { account ->
            val matched = remoteAccounts.firstOrNull {
                it.type == account.type && it.name.trim().equals(account.name.trim(), ignoreCase = true)
            }
            if (matched != null) {
                localExtras[matched.id] = account
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
            localExtras[createdAccount.id] = account
            remapAccount(account.id, createdAccount.id)
        }

        // 本地账户缓存整体换成服务端数据，保证 id 与服务端一致（拉取时才不会错位）
        val keptRows = localAccounts.filter { it.id in keptLocalIds }
        accountDao.deleteByBook(bookId)
        accountDao.upsertAll(remoteAccounts.map { it.toLocal(localExtras[it.id]) } + keptRows)
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
     * 账户引用自愈：把「账单挂着的账户已经不存在」的记录改指到兜底账户。
     * <p>
     * 两种来源：别的设备删了账户、本机同步时跟着删了本地行；或者本机删账户时，
     * 那些还没推上去的账单服务端看不见。悬空引用的账单会让同步推送被服务端整批拒绝
     * （「账户不存在」），表现就是「怎么点同步都失败」。
     * <p>
     * 处理办法：建一个兜底账户「已删除的账户」接收它们 —— 账单数据不丢，
     * 用户在账单页能把它们改到正确的账户上。每次拉取之后都会跑一遍，
     * 所以别处再删账户也不会把同步卡死。
     *
     * @return 被改写的账户 id 个数（0 表示没有悬空引用）
     */
    suspend fun repairDanglingAccountRefs(bookId: Long): Int {
        // 并发同步（进应用时的全量同步 + 账单页自己的同步）可能各建了一个兜底账户，先合并
        mergeFallbackAccounts(bookId)

        val dangling = (
            transactionDao.listDanglingAccountIds(bookId) +
                transactionDao.listDanglingToAccountIds(bookId)
            ).filterNotNull().distinct()
        if (dangling.isEmpty()) {
            return 0
        }

        val fallbackId = ensureFallbackAccount(bookId)
        dangling.forEach { brokenId ->
            if (brokenId == fallbackId) {
                return@forEach
            }
            // 只改引用、不动 pendingSync：待推送的账单自然会把新账户带上服务端，
            // 已经同步过的历史账单就留在本地（重推反而会和服务端版本打架）
            remapAccount(brokenId, fallbackId)
        }
        return dangling.size
    }

    /**
     * 取兜底账户，没有就建一个。
     * <p>
     * 服务端账本要在服务端也建出来：只建本地的话，改指过去的账单推送时依然「账户不存在」。
     * 建失败（离线 / 权限）就先用本地 id，下次同步 [reconcileAccounts] 会把它补到服务端并改回 id。
     */
    private suspend fun ensureFallbackAccount(bookId: Long): Long {
        accountDao.listByBook(bookId)
            .firstOrNull { it.name == DANGLING_ACCOUNT_NAME }
            ?.let { return it.id }

        val sort = accountDao.countByBook(bookId)
        if (bookId != JizhangDatabase.LOCAL_BOOK_ID && !tokenStore.accessToken().isNullOrBlank()) {
            val created = runCatching {
                api.createAccount(
                    AccountSaveDto(
                        bookId = bookId,
                        name = DANGLING_ACCOUNT_NAME,
                        type = 7,
                        initialBalance = 0L,
                        icon = null,
                        sort = sort
                    )
                ).data
            }.getOrNull()
            if (created != null) {
                accountDao.upsert(created.toLocal())
                return created.id
            }
        }

        val localId = accountDao.maxId() + 1
        accountDao.upsert(
            LocalAccount(
                id = localId,
                bookId = bookId,
                name = DANGLING_ACCOUNT_NAME,
                type = 7,
                sort = sort
            )
        )
        return localId
    }

    /**
     * 合并重复的兜底账户：并发修复曾各建一个，留着会让用户看到好几个「已删除的账户」。
     * <p>
     * 留挂着账单的那个（其余都是空的），先把引用改指过去再删掉。
     * 服务端那份删不掉时只留本地不删 —— 宁可多一个空账户，也不能让本地交易又挂到不存在的账户上。
     */
    private suspend fun mergeFallbackAccounts(bookId: Long) {
        val fallbacks = accountDao.listByBook(bookId).filter { it.name == DANGLING_ACCOUNT_NAME }
        if (fallbacks.size <= 1) {
            return
        }

        val keep = fallbacks.firstOrNull { transactionDao.countByAccount(it.id) > 0 } ?: fallbacks.first()
        val canReachServer = bookId != JizhangDatabase.LOCAL_BOOK_ID &&
            !tokenStore.accessToken().isNullOrBlank()
        fallbacks.filter { it.id != keep.id }.forEach { extra ->
            remapAccount(extra.id, keep.id)
            val goneOnServer = if (canReachServer) {
                runCatching { api.deleteAccount(extra.id).isSuccess }.getOrDefault(false)
            } else {
                true
            }
            if (goneOnServer) {
                accountDao.deleteById(extra.id)
            }
        }
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
            // note / archived 是本机字段，服务端不认识：按 id 把本地值贴回去，
            // 否则用户的备注和归档状态每次同步都会被服务端数据抹掉
            val existing = accountDao.listByBook(bookId).associateBy { it.id }
            accountDao.upsertAll(data.accounts.map { it.toLocal(existing[it.id]) })
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

        // 账户被删掉之后，本地可能留下「账单挂着一个不存在的账户」的悬空引用：
        // 推送时服务端会整批拒绝（账户不存在），表现成「怎么点同步都失败」。
        // 每轮拉取后自愈一次，顺手合并并发修复留下的重复兜底账户
        repairDanglingAccountRefs(bookId)

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

    companion object {
        /**
         * 兜底账户名。
         * <p>
         * 悬空引用的账单会挂到它名下 —— 名字已经无从得知（账户行被删了），
         * 只能靠用户自己认出「这笔应该记在哪个账户」再改回去。
         */
        const val DANGLING_ACCOUNT_NAME = "已删除的账户"
    }
}
