package com.jizhang.app.data.repository

import com.jizhang.app.data.local.JizhangDatabase
import com.jizhang.app.data.local.dao.AccountDao
import com.jizhang.app.data.local.entity.AccountWithBalance
import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.remote.ApiService
import com.jizhang.app.data.remote.dto.AccountSaveDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * 账户管理。
 * <p>
 * 未登录时纯本地读写；已登录时调用服务端接口并把结果回写到本地缓存，
 * 保证账户 ID 与服务端一致（交易推送时要用到）。
 * <p>
 * 例外是 [LocalAccount.note] / [LocalAccount.archived]：服务端没有这两列，
 * 一律只改本地（同步拉取时也会把本地值贴回去，见 Mappers 里的 `toLocal(existing)`）。
 */
@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val tokenStore: TokenStore,
    private val api: ApiService,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) {

    fun observeAccounts(bookId: Long): Flow<List<AccountWithBalance>> =
        accountDao.observeWithBalance(bookId)

    suspend fun listAccounts(bookId: Long): List<AccountWithBalance> =
        accountDao.listWithBalance(bookId)

    suspend fun create(
        bookId: Long,
        name: String,
        type: Int,
        initialBalance: Long,
        icon: String? = null,
        note: String? = null
    ): Result<Unit> = runCatching {
        val sort = accountDao.countByBook(bookId)
        if (isRemoteBook(bookId)) {
            val dto = api.createAccount(
                AccountSaveDto(
                    bookId = bookId,
                    name = name,
                    type = type,
                    initialBalance = initialBalance,
                    icon = icon,
                    sort = sort
                )
            ).data
            if (dto != null) {
                // note / archived 服务端没有，必须自己贴到本地缓存上，
                // 否则「新建时就填的备注」会被服务端返回体覆盖掉
                accountDao.upsert(dto.toLocal().copy(note = note))
                return@runCatching
            }
            // 接口挂了也要让用户建得出账户：退回本地自增 id，联网后再补齐
        }
        accountDao.upsert(
            LocalAccount(
                id = accountDao.maxId() + 1,
                bookId = bookId,
                name = name,
                type = type,
                initialBalance = initialBalance,
                icon = icon,
                sort = sort,
                note = note
            )
        )
    }

    /**
     * 修改账户。
     *
     * @param archived 归档状态：本机字段，只改本地（服务端没有这个概念）
     */
    suspend fun update(
        id: Long,
        bookId: Long,
        name: String,
        type: Int,
        initialBalance: Long,
        icon: String? = null,
        note: String? = null,
        archived: Boolean = false
    ): Result<Unit> = runCatching {
        val existing = accountDao.findById(id) ?: error("账户不存在")

        // 1) 先落本地：改完立刻生效，网络失败也不回滚
        accountDao.upsert(
            existing.copy(
                name = name,
                type = type,
                initialBalance = initialBalance,
                icon = icon,
                note = note,
                archived = archived
            )
        )

        /*
         * 2) 能推就推，推不动就保持本地版本。
         * 之前这里直接 `?: error("修改账户失败")`：本机登录着、但账户是导入生成的本地自增 id
         * （服务端根本没有这条），一保存就 404，用户看到「修改账户失败」——账户其实改得了。
         */
        if (isRemoteBook(bookId)) {
            val dto = api.updateAccount(
                id,
                AccountSaveDto(
                    bookId = bookId,
                    name = name,
                    type = type,
                    initialBalance = initialBalance,
                    icon = icon,
                    sort = existing.sort
                )
            ).data
            if (dto != null) {
                accountDao.upsert(
                    dto.toLocal().copy(note = note, archived = archived, sort = existing.sort)
                )
            }
        }
    }

    /** 归档 / 取消归档：纯本地状态，不动服务端 */
    suspend fun setArchived(id: Long, archived: Boolean): Result<Unit> = runCatching {
        val existing = accountDao.findById(id) ?: error("账户不存在")
        accountDao.upsert(existing.copy(archived = archived))
    }

    /**
     * 重排账户顺序（账户管理页拖拽 / 上下移）。
     *
     * @param orderedIds **整本账本**的账户顺序，不是某一组：sort 列是全局序号，
     *                   只提交组内顺序会和其它组的序号打架
     */
    suspend fun reorder(orderedIds: List<Long>): Result<Unit> = runCatching {
        orderedIds.forEachIndexed { index, id -> accountDao.updateSort(id, index) }
    }

    /**
     * 余额校准：把账户余额对到 [targetBalance]（分），差额记成一笔「余额调整」账单。
     * <p>
     * 为什么记账单而不是直接改 initialBalance：改初始余额是「静默改写历史」，用户既看不见也撤不回；
     * 记一笔调整账单则留在流水里可追溯（分类固定为「余额调整」，想筛掉也筛得掉）。
     * 差额为 0 时什么都不做。
     *
     * @return 实际调整的金额（分，带正负）；0 表示本来就是对得上的
     */
    suspend fun adjustBalance(bookId: Long, accountId: Long, targetBalance: Long): Result<Long> =
        runCatching {
            val current = accountDao.listWithBalance(bookId).firstOrNull { it.id == accountId }
                ?: error("账户不存在")
            val diff = targetBalance - current.balance
            if (diff == 0L) {
                return@runCatching 0L
            }
            // 差额为正说明钱变多了 → 记「收入」；为负 → 记「支出」
            val type = if (diff > 0) 2 else 1
            val category = categoryRepository
                .findOrCreate(bookId, ADJUST_CATEGORY_NAME, type, icon = "category")
                .getOrThrow()
            transactionRepository.addLocal(
                bookId = bookId,
                type = type,
                amount = abs(diff),
                accountId = accountId,
                categoryId = category.id,
                tradeTime = System.currentTimeMillis(),
                remark = "余额校准",
                tags = null
            )
            diff
        }

    /**
     * 删除账户。
     * <p>
     * 账户下还有账单就先拦下来（与服务端同一套规则）：删掉账户行会让那些账单失去依据，
     * 本地看着没事，一旦同步就会被服务端整批拒绝（「账户不存在」），表现成「怎么点同步都失败」。
     * 想删就先把那些账改到别的账户，或者用「归档」停用它（归档只是不再出现在选择里，历史账单照旧）。
     */
    suspend fun delete(id: Long): Result<Unit> = runCatching {
        val used = transactionRepository.countByAccount(id)
        if (used > 0) {
            error("这个账户下还有 $used 笔账单：先把它们改到别的账户，或者改成「归档」停用")
        }
        // 本地账本 / 导入生成的账户在服务端不存在，直接删本地，否则会报「账户不存在」
        val bookId = accountDao.findById(id)?.bookId
        if (bookId != null && isRemoteBook(bookId)) {
            val result = api.deleteAccount(id)
            if (!result.isSuccess) {
                error(result.message.ifBlank { "删除账户失败" })
            }
        }
        accountDao.deleteById(id)
    }

    private suspend fun isLoggedIn(): Boolean = !tokenStore.accessToken().isNullOrBlank()

    /**
     * 这个账户所在的账本是否跟服务端同步。
     * <p>
     * 只有「服务端账本 + 已登录」才值得调接口：本地账本（[JizhangDatabase.LOCAL_BOOK_ID]）
     * 和导入时按 CSV 自动建出来的账户（本地自增 id）在服务端都没有对应记录，
     * 调过去只会 404。
     */
    private suspend fun isRemoteBook(bookId: Long): Boolean =
        bookId != JizhangDatabase.LOCAL_BOOK_ID && isLoggedIn()

    companion object {
        /** 余额校准生成的调整账单统一挂在这个分类下，便于筛选 / 排除 */
        const val ADJUST_CATEGORY_NAME = "余额调整"
    }
}
