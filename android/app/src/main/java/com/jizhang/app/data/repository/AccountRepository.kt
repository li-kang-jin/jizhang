package com.jizhang.app.data.repository

import com.jizhang.app.data.local.dao.AccountDao
import com.jizhang.app.data.local.entity.AccountWithBalance
import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.remote.ApiService
import com.jizhang.app.data.remote.dto.AccountSaveDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 账户管理。
 * <p>
 * 未登录时纯本地读写；已登录时调用服务端接口并把结果回写到本地缓存，
 * 保证账户 ID 与服务端一致（交易推送时要用到）。
 */
@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val tokenStore: TokenStore,
    private val api: ApiService
) {

    fun observeAccounts(bookId: Long): Flow<List<AccountWithBalance>> =
        accountDao.observeWithBalance(bookId)

    suspend fun listAccounts(bookId: Long): List<AccountWithBalance> =
        accountDao.listWithBalance(bookId)

    suspend fun create(
        bookId: Long,
        name: String,
        type: Int,
        initialBalance: Long
    ): Result<Unit> = runCatching {
        if (isLoggedIn()) {
            val dto = api.createAccount(
                AccountSaveDto(
                    bookId = bookId,
                    name = name,
                    type = type,
                    initialBalance = initialBalance,
                    sort = accountDao.countByBook(bookId)
                )
            ).data ?: error("创建账户失败")
            accountDao.upsert(dto.toLocal())
        } else {
            accountDao.upsert(
                LocalAccount(
                    id = accountDao.maxId() + 1,
                    bookId = bookId,
                    name = name,
                    type = type,
                    initialBalance = initialBalance,
                    sort = accountDao.countByBook(bookId)
                )
            )
        }
    }

    suspend fun update(
        id: Long,
        bookId: Long,
        name: String,
        type: Int,
        initialBalance: Long
    ): Result<Unit> = runCatching {
        if (isLoggedIn()) {
            val dto = api.updateAccount(
                id,
                AccountSaveDto(bookId = bookId, name = name, type = type, initialBalance = initialBalance)
            ).data ?: error("修改账户失败")
            accountDao.upsert(dto.toLocal())
        } else {
            val existing = accountDao.findById(id) ?: error("账户不存在")
            accountDao.upsert(
                existing.copy(name = name, type = type, initialBalance = initialBalance)
            )
        }
    }

    suspend fun delete(id: Long): Result<Unit> = runCatching {
        if (isLoggedIn()) {
            val result = api.deleteAccount(id)
            if (!result.isSuccess) {
                error(result.message.ifBlank { "删除账户失败" })
            }
        }
        accountDao.deleteById(id)
    }

    private suspend fun isLoggedIn(): Boolean = !tokenStore.accessToken().isNullOrBlank()
}
