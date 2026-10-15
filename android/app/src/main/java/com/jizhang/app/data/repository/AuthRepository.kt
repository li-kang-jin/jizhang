package com.jizhang.app.data.repository

import com.jizhang.app.data.local.JizhangDatabase
import com.jizhang.app.data.local.dao.AccountDao
import com.jizhang.app.data.local.dao.CategoryDao
import com.jizhang.app.data.local.dao.TransactionDao
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.remote.ApiService
import com.jizhang.app.data.remote.dto.AccountSaveDto
import com.jizhang.app.data.remote.dto.LoginRequest
import com.jizhang.app.data.remote.dto.RegisterRequest
import com.jizhang.app.data.remote.dto.UserDto
import javax.inject.Inject
import javax.inject.Singleton

/** 登录时本地账本迁移到云端的统计 */
data class MigrationSummary(
    val transactions: Int,
    val createdAccounts: Int
) {
    val hasData: Boolean get() = transactions > 0
}

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val tokenStore: TokenStore,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao
) {

    /**
     * 登录，并把本地账本的数据迁移到服务端账本。
     * <p>
     * 迁移策略：按「名称 + 类型」把本地账户匹配到服务端，匹配不到就在服务端新建，
     * 拿到服务端 ID 后回写本地交易，最后把本地账本的记录整体改挂到服务端账本下。
     * 分类只做匹配不新建 —— 服务端新用户自带 15 个默认分类，与本地的模板一致。
     */
    suspend fun login(username: String, password: String): Result<MigrationSummary> = runCatching {
        val result = api.login(LoginRequest(username, password))
        val token = result.data ?: error(result.message.ifBlank { "登录失败" })

        if (token.user.role == ROLE_ADMIN) {
            error("管理员账号请使用管理后台登录")
        }

        tokenStore.saveLogin(token)

        val books = api.books().data.orEmpty()
        val book = books.firstOrNull() ?: error("该账号还没有账本，请先注册一个新账号")
        tokenStore.saveBookId(book.id)

        migrateLocalData(book.id)
    }

    suspend fun register(username: String, password: String, nickname: String): Result<Unit> =
        runCatching {
            val result = api.register(RegisterRequest(username, password, nickname.ifBlank { null }))
            if (!result.isSuccess) {
                error(result.message.ifBlank { "注册失败" })
            }
        }

    suspend fun me(): Result<UserDto> = runCatching {
        api.me().data ?: error("获取用户信息失败")
    }

    suspend fun logout() {
        tokenStore.clear()
    }

    private suspend fun migrateLocalData(targetBookId: Long): MigrationSummary {
        val localBookId = JizhangDatabase.LOCAL_BOOK_ID
        val localTransactionCount = transactionDao.countByBook(localBookId)
        if (localTransactionCount == 0) {
            return MigrationSummary(0, 0)
        }

        // 1. 账户：同名同类型复用，否则在服务端新建
        val remoteAccounts = api.accounts(targetBookId).data.orEmpty()
        var createdAccounts = 0
        accountDao.listByBook(localBookId).forEach { local ->
            val matched = remoteAccounts.firstOrNull { it.type == local.type && it.name == local.name }
            val remoteId = matched?.id ?: run {
                createdAccounts++
                api.createAccount(
                    AccountSaveDto(
                        bookId = targetBookId,
                        name = local.name,
                        type = local.type,
                        initialBalance = local.initialBalance
                    )
                ).data?.id
            }
            if (remoteId != null) {
                transactionDao.migrateAccount(local.id, remoteId)
                transactionDao.migrateToAccount(local.id, remoteId)
            }
        }

        // 2. 分类：只做匹配，匹配不到则该笔交易显示为「未分类」，不产生无效引用
        val remoteCategories = fetchRemoteCategories(targetBookId)
        categoryDao.listByBook(localBookId).forEach { local ->
            val matched = remoteCategories.firstOrNull { it.type == local.type && it.name == local.name }
            if (matched != null) {
                transactionDao.migrateCategory(local.id, matched.id)
            }
        }

        // 3. 交易改挂到服务端账本（同时会被标记为待同步，随后自动推送）
        transactionDao.migrateBook(localBookId, targetBookId)

        // 4. 本地账本的账户 / 分类交给服务端数据接管
        accountDao.deleteByBook(localBookId)
        categoryDao.deleteByBook(localBookId)

        return MigrationSummary(localTransactionCount, createdAccounts)
    }

    private suspend fun fetchRemoteCategories(bookId: Long) =
        api.categories(bookId, null).data.orEmpty().flatMap { listOf(it) + it.children }

    private companion object {
        const val ROLE_ADMIN = "ADMIN"
    }
}
