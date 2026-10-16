package com.jizhang.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jizhang.app.data.local.entity.LocalBudget
import kotlinx.coroutines.flow.Flow

/**
 * 预算表的读写。
 * <p>
 * `pendingDelete` 是「已删除但还没推到服务端」的墓碑：列表 / 聚合类查询一律过滤掉它
 * （用户看到的就是删掉了），推送类查询单独取它。
 */
@Dao
interface BudgetDao {

    @Query(
        "SELECT * FROM local_budget WHERE bookId = :bookId AND period = :period " +
            "AND pendingDelete = 0 ORDER BY categoryId"
    )
    fun observeByPeriod(bookId: Long, period: String): Flow<List<LocalBudget>>

    @Query(
        "SELECT * FROM local_budget WHERE bookId = :bookId AND period = :period " +
            "AND pendingDelete = 0 ORDER BY categoryId"
    )
    suspend fun listByPeriod(bookId: Long, period: String): List<LocalBudget>

    @Query("SELECT * FROM local_budget WHERE bookId = :bookId AND pendingDelete = 0")
    suspend fun listByBook(bookId: Long): List<LocalBudget>

    /** 明细里最常用的：总预算（categoryId = 0） */
    @Query(
        "SELECT * FROM local_budget WHERE bookId = :bookId AND categoryId = 0 " +
            "AND period = :period AND pendingDelete = 0 LIMIT 1"
    )
    suspend fun findTotal(bookId: Long, period: String): LocalBudget?

    /** 含墓碑：保存 / 合并时要能看见墓碑，免得把删掉的预算又建回来 */
    @Query(
        "SELECT * FROM local_budget WHERE bookId = :bookId AND categoryId = :categoryId " +
            "AND period = :period LIMIT 1"
    )
    suspend fun find(bookId: Long, categoryId: Long, period: String): LocalBudget?

    /** 待推送到服务端的额度变更（不含墓碑） */
    @Query(
        "SELECT * FROM local_budget WHERE bookId = :bookId AND pendingSync = 1 " +
            "AND pendingDelete = 0"
    )
    suspend fun listPending(bookId: Long): List<LocalBudget>

    /** 待推送到服务端的删除（墓碑） */
    @Query("SELECT * FROM local_budget WHERE bookId = :bookId AND pendingDelete = 1")
    suspend fun listPendingDelete(bookId: Long): List<LocalBudget>

    @Query("SELECT * FROM local_budget WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): LocalBudget?

    @Query("SELECT COALESCE(MAX(id), 0) FROM local_budget")
    suspend fun maxId(): Long

    /**
     * 按业务键删除。
     * <p>
     * 服务端保存后返回的是服务端 ID，本地旧行（新建时用的是 maxId+1）必须按业务键清掉，
     * 否则同一份预算会在本地留下两条。
     */
    @Query(
        "DELETE FROM local_budget WHERE bookId = :bookId AND categoryId = :categoryId " +
            "AND period = :period"
    )
    suspend fun deleteByKey(bookId: Long, categoryId: Long, period: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocalBudget)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LocalBudget>)

    @Query("DELETE FROM local_budget WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM local_budget WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE local_budget SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    /** 打墓碑：本地立刻不可见，同时不再是「待更新」，等推送去删服务端 */
    @Query("UPDATE local_budget SET pendingDelete = 1, pendingSync = 0 WHERE id = :id")
    suspend fun markPendingDelete(id: Long)

    /** 登录时把本地账本的预算迁到服务端账本 */
    @Query("UPDATE local_budget SET bookId = :newBookId WHERE bookId = :oldBookId")
    suspend fun migrateBook(oldBookId: Long, newBookId: Long)

    /** 分类 id 改写后，预算上的引用也要跟着改，否则会指向不存在的分类 */
    @Query("UPDATE local_budget SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun migrateCategory(oldCategoryId: Long, newCategoryId: Long)

    @Query("DELETE FROM local_budget WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: Long)

    @Query("DELETE FROM local_budget")
    suspend fun clear()
}
