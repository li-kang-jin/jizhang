package com.jizhang.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jizhang.app.data.local.entity.LocalBudget
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM local_budget WHERE bookId = :bookId AND period = :period ORDER BY categoryId")
    fun observeByPeriod(bookId: Long, period: String): Flow<List<LocalBudget>>

    @Query("SELECT * FROM local_budget WHERE bookId = :bookId AND period = :period ORDER BY categoryId")
    suspend fun listByPeriod(bookId: Long, period: String): List<LocalBudget>

    @Query("SELECT * FROM local_budget WHERE bookId = :bookId")
    suspend fun listByBook(bookId: Long): List<LocalBudget>

    /** 明细里最常用的：总预算（categoryId = 0） */
    @Query("SELECT * FROM local_budget WHERE bookId = :bookId AND categoryId = 0 AND period = :period LIMIT 1")
    suspend fun findTotal(bookId: Long, period: String): LocalBudget?

    @Query(
        "SELECT * FROM local_budget WHERE bookId = :bookId AND categoryId = :categoryId " +
            "AND period = :period LIMIT 1"
    )
    suspend fun find(bookId: Long, categoryId: Long, period: String): LocalBudget?

    @Query("SELECT * FROM local_budget WHERE bookId = :bookId AND pendingSync = 1")
    suspend fun listPending(bookId: Long): List<LocalBudget>

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
