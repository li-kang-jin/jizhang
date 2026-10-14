package com.jizhang.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.local.entity.LocalTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    /** UI 展示用：排除已标记删除的记录 */
    @Query(
        "SELECT * FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0 " +
            "ORDER BY tradeTime DESC, id DESC"
    )
    fun observeByBook(bookId: Long): Flow<List<LocalTransaction>>

    @Query(
        "SELECT * FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0 " +
            "ORDER BY tradeTime DESC, id DESC"
    )
    suspend fun listByBook(bookId: Long): List<LocalTransaction>

    /** 待推送的新增 / 修改（不含待删除） */
    @Query(
        "SELECT * FROM local_transaction WHERE bookId = :bookId AND pendingSync = 1 AND pendingDelete = 0"
    )
    suspend fun listPending(bookId: Long): List<LocalTransaction>

    /** 待推送到服务端的删除动作 */
    @Query("SELECT id FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 1")
    suspend fun listPendingDeleteIds(bookId: Long): List<Long>

    /** 所有已标记删除的记录（拉取时用于避免被服务端数据覆盖） */
    @Query("SELECT id FROM local_transaction WHERE pendingDelete = 1")
    suspend fun listAllPendingDeleteIds(): List<Long>

    @Query(
        "SELECT COUNT(*) FROM local_transaction " +
            "WHERE bookId = :bookId AND pendingSync = 1 AND pendingDelete = 0"
    )
    fun observePendingCount(bookId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocalTransaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LocalTransaction>)

    /** 标记为「待删除」：UI 立刻不可见，删除动作等待同步 */
    @Query("UPDATE local_transaction SET pendingDelete = 1, pendingSync = 0 WHERE id IN (:ids)")
    suspend fun markPendingDelete(ids: List<Long>)

    /** 物理删除（仅在删除动作确认同步成功、或清理时使用） */
    @Query("DELETE FROM local_transaction WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE local_transaction SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) " +
            "FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0 " +
            "AND tradeTime >= :start AND tradeTime < :end"
    )
    suspend fun sumExpense(bookId: Long, start: Long, end: Long): Long

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 2 THEN amount ELSE 0 END), 0) " +
            "FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0 " +
            "AND tradeTime >= :start AND tradeTime < :end"
    )
    suspend fun sumIncome(bookId: Long, start: Long, end: Long): Long

    @Query("DELETE FROM local_transaction")
    suspend fun clear()
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM local_category WHERE bookId = :bookId ORDER BY sort, id")
    suspend fun listByBook(bookId: Long): List<LocalCategory>

    @Query("SELECT * FROM local_category WHERE bookId = :bookId AND type = :type ORDER BY sort, id")
    suspend fun listByBookAndType(bookId: Long, type: Int): List<LocalCategory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LocalCategory>)

    @Query("DELETE FROM local_category WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM local_category")
    suspend fun clear()
}

@Dao
interface AccountDao {

    @Query("SELECT * FROM local_account WHERE bookId = :bookId ORDER BY sort, id")
    suspend fun listByBook(bookId: Long): List<LocalAccount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LocalAccount>)

    @Query("DELETE FROM local_account WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM local_account")
    suspend fun clear()
}
