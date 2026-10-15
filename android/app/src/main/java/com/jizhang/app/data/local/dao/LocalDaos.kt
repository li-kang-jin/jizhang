package com.jizhang.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jizhang.app.data.local.entity.AccountWithBalance
import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.local.entity.LocalTransaction
import kotlinx.coroutines.flow.Flow

/** 分类聚合结果 */
data class CategoryAmountRow(
    val categoryId: Long?,
    val total: Long
)

/** 按时间段聚合的收支结果，period 为 yyyy-MM-dd */
data class PeriodAmountRow(
    val period: String,
    val income: Long,
    val expense: Long
)

/** 区间收支汇总 */
data class SumAmountRow(
    val income: Long,
    val expense: Long
)

/** 按记账人聚合的收支结果 */
data class MemberAmountRow(
    val createdBy: Long?,
    val income: Long,
    val expense: Long,
    val txCount: Long
)

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

    @Query("SELECT COUNT(*) FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0")
    suspend fun countByBook(bookId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocalTransaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LocalTransaction>)

    /** 标记为「待删除」：UI 立刻不可见，删除动作等待同步 */
    @Query("UPDATE local_transaction SET pendingDelete = 1, pendingSync = 0 WHERE id IN (:ids)")
    suspend fun markPendingDelete(ids: List<Long>)

    /** 标记整本账本的账单为「待删除」（导入脏数据清理用） */
    @Query(
        "UPDATE local_transaction SET pendingDelete = 1, pendingSync = 0 " +
            "WHERE bookId = :bookId AND pendingDelete = 0"
    )
    suspend fun markAllPendingDelete(bookId: Long): Int

    /** 物理删除（仅在删除动作确认同步成功、或清理时使用） */
    @Query("DELETE FROM local_transaction WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** 物理删除整本账本的账单（仅本地账本清理用：没有服务端副本，不必留墓碑行） */
    @Query("DELETE FROM local_transaction WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: Long)

    @Query("UPDATE local_transaction SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    /** 把本地账本的记录整体迁移到服务端账本（登录时使用） */
    @Query("UPDATE local_transaction SET bookId = :newBookId WHERE bookId = :oldBookId")
    suspend fun migrateBook(oldBookId: Long, newBookId: Long)

    @Query("UPDATE local_transaction SET accountId = :newAccountId WHERE accountId = :oldAccountId")
    suspend fun migrateAccount(oldAccountId: Long, newAccountId: Long)

    @Query("UPDATE local_transaction SET toAccountId = :newAccountId WHERE toAccountId = :oldAccountId")
    suspend fun migrateToAccount(oldAccountId: Long, newAccountId: Long)

    @Query("UPDATE local_transaction SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun migrateCategory(oldCategoryId: Long, newCategoryId: Long)

    /* ---------------- 本地聚合（离线统计） ---------------- */

    /** 指定区间收支汇总 */
    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 2 THEN amount ELSE 0 END), 0) AS income, " +
            "COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) AS expense " +
            "FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0 " +
            "AND tradeTime >= :start AND tradeTime < :end"
    )
    suspend fun sumBetween(bookId: Long, start: Long, end: Long): SumAmountRow

    /** 分类占比 */
    @Query(
        "SELECT categoryId AS categoryId, SUM(amount) AS total FROM local_transaction " +
            "WHERE bookId = :bookId AND pendingDelete = 0 AND type = :type " +
            "AND tradeTime >= :start AND tradeTime < :end GROUP BY categoryId ORDER BY total DESC"
    )
    suspend fun sumByCategory(bookId: Long, type: Int, start: Long, end: Long): List<CategoryAmountRow>

    /** 按日聚合的收支趋势 */
    @Query(
        "SELECT strftime('%Y-%m-%d', tradeTime / 1000, 'unixepoch', 'localtime') AS period, " +
            "COALESCE(SUM(CASE WHEN type = 2 THEN amount ELSE 0 END), 0) AS income, " +
            "COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) AS expense " +
            "FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0 " +
            "AND tradeTime >= :start AND tradeTime < :end GROUP BY period ORDER BY period"
    )
    suspend fun sumByDay(bookId: Long, start: Long, end: Long): List<PeriodAmountRow>

    /** 按记账人聚合收支（家庭账本「谁花了多少」） */
    @Query(
        "SELECT createdBy AS createdBy, " +
            "COALESCE(SUM(CASE WHEN type = 2 THEN amount ELSE 0 END), 0) AS income, " +
            "COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) AS expense, " +
            "COUNT(*) AS txCount " +
            "FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0 " +
            "AND tradeTime >= :start AND tradeTime < :end " +
            "GROUP BY createdBy ORDER BY expense DESC"
    )
    suspend fun sumByMember(bookId: Long, start: Long, end: Long): List<MemberAmountRow>

    /** 账户作为主账户的净流入（支出/转账为负，收入为正） */
    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 2 THEN amount ELSE -amount END), 0) " +
            "FROM local_transaction WHERE accountId = :accountId AND pendingDelete = 0"
    )
    suspend fun sumOutflowDelta(accountId: Long): Long

    /** 账户作为转账接收方的净流入 */
    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM local_transaction " +
            "WHERE toAccountId = :accountId AND type = 3 AND pendingDelete = 0"
    )
    suspend fun sumInflowDelta(accountId: Long): Long

    @Query("DELETE FROM local_transaction")
    suspend fun clear()
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM local_category WHERE bookId = :bookId ORDER BY sort, id")
    suspend fun listByBook(bookId: Long): List<LocalCategory>

    @Query("SELECT * FROM local_category WHERE bookId = :bookId AND type = :type ORDER BY sort, id")
    suspend fun listByBookAndType(bookId: Long, type: Int): List<LocalCategory>

    @Query("SELECT COUNT(*) FROM local_category WHERE bookId = :bookId")
    suspend fun countByBook(bookId: Long): Int

    @Query("SELECT COALESCE(MAX(id), 0) FROM local_category")
    suspend fun maxId(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LocalCategory>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocalCategory)

    @Query("DELETE FROM local_category WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM local_category WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: Long)

    @Query("DELETE FROM local_category")
    suspend fun clear()
}

@Dao
interface AccountDao {

    /** 余额计算：初始余额 + 本账户流出净额 + 作为转入方的净额 */
    private companion object {
        const val BALANCE_SQL =
            "SELECT a.id AS id, a.bookId AS bookId, a.name AS name, a.type AS type, " +
                "a.initialBalance AS initialBalance, a.icon AS icon, a.sort AS sort, " +
                "(COALESCE(a.initialBalance, 0) " +
                " + COALESCE((SELECT SUM(CASE WHEN t.type = 2 THEN t.amount ELSE -t.amount END) " +
                "   FROM local_transaction t WHERE t.accountId = a.id AND t.pendingDelete = 0), 0) " +
                " + COALESCE((SELECT SUM(t.amount) FROM local_transaction t " +
                "   WHERE t.toAccountId = a.id AND t.type = 3 AND t.pendingDelete = 0), 0)" +
                ") AS balance " +
                "FROM local_account a WHERE a.bookId = :bookId ORDER BY a.sort, a.id"
    }

    @Query(BALANCE_SQL)
    fun observeWithBalance(bookId: Long): Flow<List<AccountWithBalance>>

    @Query(BALANCE_SQL)
    suspend fun listWithBalance(bookId: Long): List<AccountWithBalance>

    @Query("SELECT * FROM local_account WHERE bookId = :bookId ORDER BY sort, id")
    suspend fun listByBook(bookId: Long): List<LocalAccount>

    @Query("SELECT * FROM local_account WHERE id = :id")
    suspend fun findById(id: Long): LocalAccount?

    @Query("SELECT COUNT(*) FROM local_account WHERE bookId = :bookId")
    suspend fun countByBook(bookId: Long): Int

    @Query("SELECT COALESCE(MAX(id), 0) FROM local_account")
    suspend fun maxId(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocalAccount)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LocalAccount>)

    @Query("DELETE FROM local_account WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM local_account WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM local_account WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: Long)

    @Query("DELETE FROM local_account")
    suspend fun clear()
}
