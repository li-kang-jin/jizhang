package com.jizhang.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jizhang.app.data.local.entity.LocalInvestPosition
import com.jizhang.app.data.local.entity.LocalInvestValuation
import com.jizhang.app.data.local.entity.PositionWithValue
import kotlinx.coroutines.flow.Flow

/**
 * 投资持仓 / 估值的数据访问。
 * <p>
 * 市值取「最新一条估值快照」，和余额一样由 SQL 实时聚合，不冗余存储。
 * 子查询里按 `valuedAt DESC, id DESC` 排序：同一天更新过两次估值时，
 * 后写入的那条（id 更大）才算数，否则一天内改两次会读到旧值。
 */
@Dao
interface InvestmentDao {

    @Query(POSITION_SELECT + "WHERE p.bookId = :bookId AND p.archived = 0 ORDER BY p.sort, p.id")
    fun observeWithValue(bookId: Long): Flow<List<PositionWithValue>>

    @Query(POSITION_SELECT + "WHERE p.bookId = :bookId AND p.archived = 0 ORDER BY p.sort, p.id")
    suspend fun listWithValue(bookId: Long): List<PositionWithValue>

    @Query("SELECT * FROM local_invest_position WHERE id = :id")
    suspend fun findById(id: Long): LocalInvestPosition?

    /** 某账户下的在仓品种：算「未分配资金」与删除账户时的引用检查用 */
    @Query(
        "SELECT * FROM local_invest_position " +
            "WHERE accountId = :accountId AND archived = 0 ORDER BY sort, id"
    )
    suspend fun listByAccount(accountId: Long): List<LocalInvestPosition>

    @Query("SELECT COUNT(*) FROM local_invest_position WHERE accountId = :accountId")
    suspend fun countByAccount(accountId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: LocalInvestPosition)

    @Query("DELETE FROM local_invest_position WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 归档而不是删除：历史估值要留着，否则曲线会凭空少一段 */
    @Query("UPDATE local_invest_position SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("SELECT COALESCE(MAX(sort), 0) FROM local_invest_position WHERE bookId = :bookId")
    suspend fun maxSort(bookId: Long): Int

    @Query(
        "SELECT * FROM local_invest_valuation " +
            "WHERE positionId = :positionId AND pendingDelete = 0 " +
            "ORDER BY valuedAt DESC, id DESC"
    )
    fun observeValuations(positionId: Long): Flow<List<LocalInvestValuation>>

    @Query(
        "SELECT * FROM local_invest_valuation " +
            "WHERE positionId = :positionId AND pendingDelete = 0 " +
            "ORDER BY valuedAt DESC, id DESC LIMIT 1"
    )
    suspend fun latestValuation(positionId: Long): LocalInvestValuation?

    /** 全账本最近一次估值时间：界面用它提示「上次估值 N 天前」 */
    @Query(
        "SELECT MAX(valuedAt) FROM local_invest_valuation " +
            "WHERE bookId = :bookId AND pendingDelete = 0"
    )
    fun observeLastValuedAt(bookId: Long): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValuation(valuation: LocalInvestValuation)

    @Query("DELETE FROM local_invest_valuation WHERE positionId = :positionId")
    suspend fun deleteValuationsOf(positionId: Long)

    companion object {
        /**
         * 持仓 + 最新市值。
         * <p>
         * 两个子查询各取一次最新快照（市值与时间必须来自同一行），
         * 避免出现「市值是新的、时间是旧的」这种自相矛盾的展示。
         */
        const val POSITION_SELECT =
            "SELECT p.id AS id, p.bookId AS bookId, p.accountId AS accountId, p.name AS name, " +
                "p.code AS code, p.kind AS kind, p.costFen AS costFen, " +
                "p.sharesScaled AS sharesScaled, p.targetPercent AS targetPercent, " +
                "p.sort AS sort, p.note AS note, p.archived AS archived, " +
                "(SELECT v.valueFen FROM local_invest_valuation v " +
                "   WHERE v.positionId = p.id AND v.pendingDelete = 0 " +
                "   ORDER BY v.valuedAt DESC, v.id DESC LIMIT 1) AS valueFen, " +
                "(SELECT v.valuedAt FROM local_invest_valuation v " +
                "   WHERE v.positionId = p.id AND v.pendingDelete = 0 " +
                "   ORDER BY v.valuedAt DESC, v.id DESC LIMIT 1) AS valuedAt " +
                "FROM local_invest_position p "
    }
}
