package com.jizhang.app.data.repository

import com.jizhang.app.data.local.JizhangDatabase
import com.jizhang.app.data.local.dao.CategoryDao
import com.jizhang.app.data.local.dao.TransactionDao
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.remote.ApiService
import com.jizhang.app.data.remote.dto.CategorySaveDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 分类管理。
 *
 * 与账户一致：未登录（本地账本）纯本地读写；已登录时调服务端接口并把结果回写到本地缓存，
 * 保证分类 ID 与服务端一致。
 *
 * 为什么必须走 REST 而不能走同步队列：同步协议只会推交易（`SyncPushRequest` 里没有分类），
 * 本地新建的分类没有别的通道能到达服务端。
 *
 * 删除规则和服务端保持一致（`CategoryServiceImpl.delete`）：分类被交易引用时拒绝删除。
 * 这里先在本地查一次引用，能在断网 / 离线时也给出准确提示，不必等服务端报 4002。
 */
@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val tokenStore: TokenStore,
    private val api: ApiService
) {

    fun observeCategories(bookId: Long): Flow<List<LocalCategory>> = categoryDao.observeByBook(bookId)

    suspend fun listCategories(bookId: Long): List<LocalCategory> = categoryDao.listByBook(bookId)

    /** 新建分类，返回落库后的本地分类（记账页要自动选中它） */
    suspend fun create(
        bookId: Long,
        name: String,
        type: Int,
        icon: String?
    ): Result<LocalCategory> = runCatching {
        val sort = categoryDao.listByBookAndType(bookId, type).size
        if (useRemote(bookId)) {
            val dto = api.createCategory(
                CategorySaveDto(
                    bookId = bookId,
                    parentId = 0L,
                    name = name,
                    type = type,
                    icon = icon,
                    sort = sort
                )
            ).data ?: error("创建分类失败")
            val local = dto.toLocal()
            categoryDao.upsert(local)
            local
        } else {
            val local = LocalCategory(
                id = categoryDao.maxId() + 1,
                bookId = bookId,
                parentId = 0L,
                name = name,
                type = type,
                icon = icon,
                sort = sort
            )
            categoryDao.upsert(local)
            local
        }
    }

    /**
     * 按名称找分类，没有就新建一个。
     * <p>
     * 给「余额校准」这类系统动作准备一个稳定分类用：既不该让用户先手工建好，
     * 也不能每次校准都新建一个重名分类。
     */
    suspend fun findOrCreate(
        bookId: Long,
        name: String,
        type: Int,
        icon: String? = null
    ): Result<LocalCategory> = runCatching {
        categoryDao.listByBookAndType(bookId, type).firstOrNull { it.name == name }
            ?: create(bookId, name, type, icon).getOrThrow()
    }

    suspend fun update(
        id: Long,
        bookId: Long,
        name: String,
        type: Int,
        icon: String?
    ): Result<Unit> = runCatching {
        val existing = categoryDao.listByBook(bookId).firstOrNull { it.id == id }
            ?: error("分类不存在")

        // 支出/收入改向会让已记的账归到另一侧统计里，有账单就不允许改
        if (type != existing.type && transactionDao.countByCategory(id) > 0) {
            error("该分类下已有账单，不能修改收支类型")
        }

        if (useRemote(bookId)) {
            val dto = api.updateCategory(
                id,
                CategorySaveDto(
                    bookId = bookId,
                    parentId = existing.parentId,
                    name = name,
                    type = type,
                    icon = icon,
                    // 颜色本版没有编辑入口，避免把服务端已有的颜色冲掉
                    color = existing.color
                )
            ).data ?: error("修改分类失败")
            categoryDao.upsert(dto.toLocal())
        } else {
            categoryDao.upsert(existing.copy(name = name, type = type, icon = icon))
        }
    }

    suspend fun delete(id: Long, bookId: Long): Result<Unit> = runCatching {
        val used = transactionDao.countByCategory(id)
        if (used > 0) {
            error("该分类下还有 $used 笔账单，先把这些账单改到别的分类再删除")
        }
        if (useRemote(bookId)) {
            val result = api.deleteCategory(id)
            if (!result.isSuccess) {
                error(result.message.ifBlank { "删除分类失败" })
            }
        }
        categoryDao.deleteByIds(listOf(id))
    }

    /**
     * 是否需要走服务端。
     * <p>
     * 本地账本（未登录时创建的那个）没有服务端副本，即使后续登录了也一样，
     * 与 [LedgerRepository.reconcileLedger] 的判定保持一致。
     */
    private suspend fun useRemote(bookId: Long): Boolean =
        bookId != JizhangDatabase.LOCAL_BOOK_ID && !tokenStore.accessToken().isNullOrBlank()
}
