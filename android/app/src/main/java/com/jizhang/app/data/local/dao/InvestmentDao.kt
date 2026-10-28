package com.jizhang.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jizhang.app.data.local.entity.AccountMarketValue
import com.jizhang.app.data.local.entity.LocalInvestFlow
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

    // pendingDelete = 1 是「已删除、等服务端确认」的墓碑：界面上必须立刻消失
    @Query(
        POSITION_SELECT + "WHERE p.bookId = :bookId AND p.archived = 0 AND p.pendingDelete = 0 " +
            "ORDER BY p.sort, p.id"
    )
    fun observeWithValue(bookId: Long): Flow<List<PositionWithValue>>

    @Query(
        POSITION_SELECT + "WHERE p.bookId = :bookId AND p.archived = 0 AND p.pendingDelete = 0 " +
            "ORDER BY p.sort, p.id"
    )
    suspend fun listWithValue(bookId: Long): List<PositionWithValue>

    @Query("SELECT * FROM local_invest_position WHERE id = :id")
    suspend fun findById(id: Long): LocalInvestPosition?

    /** 某账户下的在仓品种：算「未分配资金」与删除账户时的引用检查用 */
    @Query(
        "SELECT * FROM local_invest_position " +
            "WHERE accountId = :accountId AND archived = 0 AND pendingDelete = 0 ORDER BY sort, id"
    )
    suspend fun listByAccount(accountId: Long): List<LocalInvestPosition>

    /** 墓碑不算「挂着品种」，否则删掉的持仓会让账户永远删不掉 */
    @Query(
        "SELECT COUNT(*) FROM local_invest_position " +
            "WHERE accountId = :accountId AND pendingDelete = 0"
    )
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

    /**
     * 按账户汇总的持仓市值：统计页把投资账户的余额换成市值要用。
     * <p>
     * 未估过值的品种按**成本**计（期初 + 转账累计），不能按 0 计 ——
     * 否则新建品种之后净资产会凭空掉一截。
     */
    @Query(
        "SELECT p.accountId AS accountId, SUM(" +
            "COALESCE((SELECT v.valueFen FROM local_invest_valuation v " +
            "   WHERE v.positionId = p.id AND v.pendingDelete = 0 " +
            "   ORDER BY v.valuedAt DESC, v.id DESC LIMIT 1), " +
            "(p.costFen + $FLOW_NET))) AS valueFen " +
            "FROM local_invest_position p " +
            "WHERE p.bookId = :bookId AND p.archived = 0 GROUP BY p.accountId"
    )
    suspend fun accountMarketValues(bookId: Long): List<AccountMarketValue>

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

    /* ==================== 成本流水（转账挂品种） ==================== */

    @Query("SELECT * FROM local_invest_flow WHERE transactionId = :transactionId LIMIT 1")
    suspend fun findFlowByTransaction(transactionId: Long): LocalInvestFlow?

    @Query("SELECT * FROM local_invest_flow WHERE positionId = :positionId ORDER BY occurredAt")
    suspend fun listFlows(positionId: Long): List<LocalInvestFlow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFlow(flow: LocalInvestFlow)

    @Query("DELETE FROM local_invest_flow WHERE transactionId = :transactionId")
    suspend fun deleteFlowByTransaction(transactionId: Long)

    @Query("DELETE FROM local_invest_flow WHERE positionId = :positionId")
    suspend fun deleteFlowsOf(positionId: Long)

    /** 清空账本的账单时一并清掉成本流水，否则会留下指向已删账单的孤儿 */
    @Query("DELETE FROM local_invest_flow WHERE bookId = :bookId")
    suspend fun deleteFlowsOfBook(bookId: Long)

    /** 挂在某笔转账上的所有品种（理论上最多一条，唯一索引兜住重复） */
    @Query("SELECT positionId FROM local_invest_flow WHERE transactionId = :transactionId LIMIT 1")
    suspend fun positionIdOfTransaction(transactionId: Long): Long?

    /* ==================== 同步（待推 / 合并） ==================== */

    @Query(
        "SELECT * FROM local_invest_position " +
            "WHERE bookId = :bookId AND pendingSync = 1 AND pendingDelete = 0"
    )
    suspend fun listPending(bookId: Long): List<LocalInvestPosition>

    @Query("SELECT * FROM local_invest_position WHERE bookId = :bookId AND pendingDelete = 1")
    suspend fun listPendingDelete(bookId: Long): List<LocalInvestPosition>

    /** 标记为待推：成本流水变了而持仓本身没变时，用这个把它顶上去（推送按持仓全量覆盖流水） */
    @Query("UPDATE local_invest_position SET pendingSync = 1 WHERE id = :id")
    suspend fun markPendingSync(id: Long)

    @Query("UPDATE local_invest_position SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("UPDATE local_invest_position SET pendingDelete = 1 WHERE id = :id")
    suspend fun markPendingDelete(id: Long)

    @Query("DELETE FROM local_invest_position WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query(
        "SELECT * FROM local_invest_valuation " +
            "WHERE bookId = :bookId AND pendingSync = 1 AND pendingDelete = 0"
    )
    suspend fun listPendingValuations(bookId: Long): List<LocalInvestValuation>

    @Query("UPDATE local_invest_valuation SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun markValuationsSynced(ids: List<Long>)

    @Query("DELETE FROM local_invest_valuation WHERE id IN (:ids)")
    suspend fun deleteValuationsByIds(ids: List<Long>)

    @Query("DELETE FROM local_invest_valuation WHERE positionId IN (:ids)")
    suspend fun deleteValuationsByPositionIds(ids: List<Long>)

    @Query("DELETE FROM local_invest_flow WHERE positionId IN (:ids)")
    suspend fun deleteFlowsByPositionIds(ids: List<Long>)

    /**
     * 合并服务端流水时用 IGNORE 而不是 REPLACE。
     * <p>
     * `transactionId` 上有唯一索引：本机刚挂上的流水（还没推上去）如果被服务端的旧记录
     * REPLACE 掉，成本会当场回到修改前；IGNORE 让本地的先手赢，等推送时服务端再被覆盖。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFlowIgnore(flow: LocalInvestFlow)

    companion object {
        /**
         * 成本流水的净额表达式：买入记正、卖出记负。
         * <p>
         * 单独抽出来是因为它在 [POSITION_SELECT] 里出现两次（总成本与自动成本各一次），
         * 抄错一次就会让「总成本 ≠ 期初 + 自动」。
         */
        private const val FLOW_NET =
            "COALESCE((SELECT SUM(CASE WHEN f.side = 1 THEN f.amountFen ELSE -f.amountFen END) " +
                "FROM local_invest_flow f WHERE f.positionId = p.id), 0)"

        /**
         * 持仓 + 最新市值 + 总成本。
         * <p>
         * 两个子查询各取一次最新快照（市值与时间必须来自同一行），
         * 避免出现「市值是新的、时间是旧的」这种自相矛盾的展示。
         * <p>
         * `costFen` 是**总成本**（期初 + 转账累计），页面上的「成本 / 盈亏」都用它；
         * 编辑弹窗回填 `manualCostFen`，否则用户改一次名字就会把自动累计的部分写成静态值。
         */
        const val POSITION_SELECT =
            "SELECT p.id AS id, p.bookId AS bookId, p.accountId AS accountId, p.name AS name, " +
                "p.code AS code, p.kind AS kind, " +
                "(p.costFen + $FLOW_NET) AS costFen, " +
                "p.costFen AS manualCostFen, " +
                "$FLOW_NET AS flowCostFen, " +
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
