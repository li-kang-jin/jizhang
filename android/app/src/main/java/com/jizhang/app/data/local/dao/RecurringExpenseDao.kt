package com.jizhang.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jizhang.app.data.local.entity.LocalRecurringExpense
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringExpenseDao {

    @Query("SELECT * FROM local_recurring_expense WHERE bookId = :bookId ORDER BY startDate, id")
    fun observeByBook(bookId: Long): Flow<List<LocalRecurringExpense>>

    @Query("SELECT * FROM local_recurring_expense WHERE bookId = :bookId ORDER BY startDate, id")
    suspend fun listByBook(bookId: Long): List<LocalRecurringExpense>

    /** 只取生效中的：预算计算用得上，已停用的不参与预留 */
    @Query("SELECT * FROM local_recurring_expense WHERE bookId = :bookId AND enabled = 1")
    suspend fun listEnabled(bookId: Long): List<LocalRecurringExpense>

    @Query("SELECT * FROM local_recurring_expense WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): LocalRecurringExpense?

    @Query("SELECT COALESCE(MAX(id), 0) FROM local_recurring_expense")
    suspend fun maxId(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocalRecurringExpense)

    @Query("DELETE FROM local_recurring_expense WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM local_recurring_expense WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** 待同步到服务端的记录（离线期间改的、登录时从本地账本迁过来的） */
    @Query("SELECT * FROM local_recurring_expense WHERE bookId = :bookId AND pendingSync = 1")
    suspend fun listPending(bookId: Long): List<LocalRecurringExpense>

    @Query("UPDATE local_recurring_expense SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    /**
     * 早期记录的 id 是本地自增的小数字（maxId + 1），同一条规则在两台设备上会撞。
     * 推送前用 [com.jizhang.app.data.local.newClientId] 换成全局唯一 ID。
     */
    @Query("SELECT * FROM local_recurring_expense WHERE id < :limit")
    suspend fun listByIdBelow(limit: Long): List<LocalRecurringExpense>

    @Query("UPDATE local_recurring_expense SET id = :newId WHERE id = :oldId")
    suspend fun renameId(oldId: Long, newId: Long)

    /** 登录时把本地账本的固定支出迁到服务端账本 */
    @Query("UPDATE local_recurring_expense SET bookId = :newBookId WHERE bookId = :oldBookId")
    suspend fun migrateBook(oldBookId: Long, newBookId: Long)

    /** 分类 id 改写后，固定支出上的引用也要跟着改 */
    @Query("UPDATE local_recurring_expense SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun migrateCategory(oldCategoryId: Long, newCategoryId: Long)

    @Query("DELETE FROM local_recurring_expense")
    suspend fun clear()
}
