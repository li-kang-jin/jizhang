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

/**
 * 区间总览：收支 + 笔数 + 转账 + 记账天数。
 * <p>
 * 一次查询拿全，省得为了「日均、笔数、转账」再跑好几趟库；转账单独列出来，
 * 不混进收支口径（转账只是钱在账户之间挪，不是花掉）。
 */
data class RangeSummaryRow(
    val income: Long,
    val expense: Long,
    val incomeCount: Long,
    val expenseCount: Long,
    val transferCount: Long,
    val transferAmount: Long,
    /** 有记账的天数（不含只有转账的天） */
    val activeDays: Long
)

/** 按账户聚合的收支（不含转账：转账在转出/转入两边都不是收支） */
data class AccountAmountRow(
    val accountId: Long,
    val income: Long,
    val expense: Long,
    val txCount: Long
)

/**
 * 一笔账单用到的账户（转账时要看两边）：记账页只铺三个常用账户，靠它统计频次。
 * <p>
 * 只取统计需要的三列，不把整行账单捞出来 —— 最近 N 笔可能是几百行，没必要。
 */
data class AccountUseRow(
    /** 账户方（转出方） */
    val accountId: Long,
    /** 转入方，只有转账有 */
    val toAccountId: Long?,
    /** 1=支出 2=收入 3=转账 */
    val type: Int
)

/**
 * 分类的用量：笔数与金额合计。
 * <p>
 * 记账页只用 [cnt]（挑默认分类），分类管理页两个都用：金额更能说明「这个分类值不值得留」，
 * 一笔 3000 的房租和一笔记 3 元的补差分类，笔数一样时看金额才知道该合并哪个。
 */
data class CategoryUsageRow(
    val categoryId: Long,
    val cnt: Long,
    val total: Long
)

/**
 * 一条带备注或标签的历史账单（只取猜分类需要的几列），记账页据此把备注 / 标签映射回分类。
 * <p>
 * 两列都可空：只要有一边有内容就够用（用户可能只打标签、从不写备注）。
 */
data class NoteRow(
    val remark: String?,
    val tags: String?,
    val categoryId: Long,
    val tradeTime: Long
)

/** 按星期聚合（0=周日 … 6=周六），不含转账 */
data class WeekdayAmountRow(
    val weekday: Int,
    val expense: Long,
    val txCount: Long
)

/** 标签原文 + 金额：标签是逗号分隔的单列，拆分成多标签统计放在内存里做 */
data class TagAmountRow(
    val tags: String,
    /** 1=支出 2=收入 */
    val type: Int,
    val amount: Long
)

/** 区间内首笔 / 末笔的记账时刻 */
data class TimeBoundRow(
    val firstTime: Long?,
    val lastTime: Long?
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

    /**
     * 最近若干条账单的标签原文（逗号分隔），供记账页提取「常用标签」。
     * <p>
     * 只取有标签的行：绝大多数账单没有标签，限定在最近 N 条里才能保证候选有代表性。
     */
    @Query(
        "SELECT tags FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0 " +
            "AND tags IS NOT NULL AND tags != '' ORDER BY tradeTime DESC LIMIT :limit"
    )
    suspend fun recentTags(bookId: Long, limit: Int): List<String>

    /**
     * 某个分类下最近若干条账单的标签原文，记账页用它算「这个分类常用的标签」。
     * <p>
     * 标签与分类在库里没有关联表，只有同一行的 `categoryId` + `tags` 两列；这里直接按分类
     * 过滤历史账单，等价于「上次记餐饮时打过哪些标签」。
     */
    @Query(
        "SELECT tags FROM local_transaction WHERE bookId = :bookId AND categoryId = :categoryId " +
            "AND pendingDelete = 0 AND tags IS NOT NULL AND tags != '' " +
            "ORDER BY tradeTime DESC LIMIT :limit"
    )
    suspend fun recentTagsByCategory(bookId: Long, categoryId: Long, limit: Int): List<String>

    /**
     * 最近若干笔账单用到的账户，记账页据此算「常用账户」。
     * <p>
     * 只取最近一段（按记账时间倒序）而不是全历史：半年前用得多的卡现在早就不用了，
     * 全历史计数会让它长期霸着三个芯片的位置。转出方与转入方两列原样返回，
     * 由调用方决定都算一次使用（否则常收款的账户永远排不上）；聚合与排序都交给 Kotlin。
     */
    @Query(
        "SELECT accountId, toAccountId, type FROM local_transaction " +
            "WHERE bookId = :bookId AND pendingDelete = 0 " +
            "ORDER BY tradeTime DESC, id DESC LIMIT :limit"
    )
    suspend fun recentAccountUses(bookId: Long, limit: Int): List<AccountUseRow>

    /**
     * 每个分类的用量（笔数 + 金额合计，按收支类型分开）。
     * <p>
     * 记账页只用笔数挑默认分类，分类管理页两个都要（见 [CategoryUsageRow]）。
     * 只聚合、不排序：选谁当默认由调用方按自己的列表顺序决胜，SQL 里排序反而会
     * 让「次数相同时保持分类管理里的手工顺序」这条规则失效。
     */
    @Query(
        "SELECT categoryId, COUNT(*) AS cnt, COALESCE(SUM(amount), 0) AS total " +
            "FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0 AND type = :type " +
            "AND categoryId IS NOT NULL GROUP BY categoryId"
    )
    suspend fun categoryUsage(bookId: Long, type: Int): List<CategoryUsageRow>

    /**
     * 把某个分类下的账单整体改到另一个分类（分类合并用）。
     * <p>
     * 必须重新打上 `pendingSync`：合并是一次真实的账单修改，登录状态下要靠常规同步推到服务端。
     * `clientUpdatedAt` 一起更新，否则服务端按「谁更新」判断冲突时会把这批旧时间戳当成过期数据。
     * 只动未删除的账单：已标记删除的行不参与统计，也不需要再推一次。
     */
    @Query(
        "UPDATE local_transaction SET categoryId = :toCategoryId, pendingSync = 1, " +
            "clientUpdatedAt = :now " +
            "WHERE bookId = :bookId AND categoryId = :fromCategoryId AND pendingDelete = 0"
    )
    suspend fun reassignCategory(
        bookId: Long,
        fromCategoryId: Long,
        toCategoryId: Long,
        now: Long
    ): Int

    /**
     * 最近若干条带备注或标签的账单，记账页用它做「备注 / 标签 → 分类」的猜测。
     * <p>
     * 只看备注、标签与它们当时落在哪个分类，「金额」之类的列一概不读。
     * 备注和标签有一边非空即可：不少用户只打标签、从不写备注，按备注单边过滤会让这批人永远猜不出来。
     */
    @Query(
        "SELECT remark, tags, categoryId, tradeTime FROM local_transaction " +
            "WHERE bookId = :bookId AND pendingDelete = 0 AND categoryId IS NOT NULL " +
            "AND ((remark IS NOT NULL AND remark != '') OR (tags IS NOT NULL AND tags != '')) " +
            "ORDER BY tradeTime DESC, id DESC LIMIT :limit"
    )
    suspend fun recentNotes(bookId: Long, limit: Int): List<NoteRow>

    /**
     * 最近记过的几笔账（最新在前），供记账页「再来一笔」照抄。
     * <p>
     * 与 [recentTags] 同理：同一笔账经常反复发生（早餐、打车），直接照抄比重新填一遍快得多。
     */
    @Query(
        "SELECT * FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0 " +
            "ORDER BY tradeTime DESC, id DESC LIMIT :limit"
    )
    suspend fun recentTransactions(bookId: Long, limit: Int): List<LocalTransaction>

    /**
     * 某个账户的流水（转出或转入都算），账户详情页用。
     * <p>
     * 转账的转入方也是「这个账户发生的事」，所以 toAccountId 命中的记录同样要列出来。
     */
    @Query(
        "SELECT * FROM local_transaction WHERE pendingDelete = 0 " +
            "AND (accountId = :accountId OR toAccountId = :accountId) " +
            "ORDER BY tradeTime DESC, id DESC LIMIT :limit"
    )
    suspend fun listByAccount(accountId: Long, limit: Int): List<LocalTransaction>

    /** 某账户在区间内的收支汇总（不含转账：转账在转出/转入两边都不是收支） */
    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 2 THEN amount ELSE 0 END), 0) AS income, " +
            "COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) AS expense " +
            "FROM local_transaction WHERE pendingDelete = 0 AND type != 3 AND accountId = :accountId " +
            "AND tradeTime >= :start AND tradeTime < :end"
    )
    suspend fun sumByAccount(accountId: Long, start: Long, end: Long): SumAmountRow

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

    /** 某分类下还有多少笔账单（删除 / 改类型前查引用，与服务端同一套规则） */
    @Query("SELECT COUNT(*) FROM local_transaction WHERE categoryId = :categoryId AND pendingDelete = 0")
    suspend fun countByCategory(categoryId: Long): Int

    /** 某账户名下还有多少笔账单（转出 / 转入都算），删除账户前查引用，与服务端同一套规则 */
    @Query(
        "SELECT COUNT(*) FROM local_transaction WHERE pendingDelete = 0 " +
            "AND (accountId = :accountId OR toAccountId = :accountId)"
    )
    suspend fun countByAccount(accountId: Long): Int

    @Query("SELECT * FROM local_transaction WHERE id = :id")
    suspend fun findById(id: Long): LocalTransaction?

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

    /**
     * 找「付费账户已经不存在」的账单，按账户 id 去重。
     * <p>
     * 账户被删掉（别的设备删了之后本机跟着删、或本机删除时还有账单没推上去）时，
     * 挂在它上面的账单会留着一条已经对不上号的外键。推送时服务端整批拒绝
     * （账户不存在），表现为「怎么点同步都失败」。
     */
    @Query(
        "SELECT DISTINCT t.accountId FROM local_transaction t " +
            "LEFT JOIN local_account a ON a.id = t.accountId " +
            "WHERE t.bookId = :bookId AND t.pendingDelete = 0 AND a.id IS NULL"
    )
    suspend fun listDanglingAccountIds(bookId: Long): List<Long>

    /** 同上，但查的是转账的「转入账户」那一侧 */
    @Query(
        "SELECT DISTINCT t.toAccountId FROM local_transaction t " +
            "LEFT JOIN local_account a ON a.id = t.toAccountId " +
            "WHERE t.bookId = :bookId AND t.pendingDelete = 0 AND t.toAccountId IS NOT NULL AND a.id IS NULL"
    )
    suspend fun listDanglingToAccountIds(bookId: Long): List<Long>

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

    /**
     * 指定区间收支汇总，但排除某个分类。
     * <p>
     * 预算统计必须剔掉「余额校准」生成的调整单：它只是把账户余额对到真实值，
     * 不是消费；算进预算会让当月直接爆掉（例如把白条从 +1500 校准到 -3000 会记 4500 支出）。
     */
    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 2 THEN amount ELSE 0 END), 0) AS income, " +
            "COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) AS expense " +
            "FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0 " +
            "AND tradeTime >= :start AND tradeTime < :end " +
            "AND (categoryId IS NULL OR categoryId != :excludeCategoryId)"
    )
    suspend fun sumBetweenExcluding(
        bookId: Long,
        start: Long,
        end: Long,
        excludeCategoryId: Long
    ): SumAmountRow

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

    /** 按日聚合，排除某个分类（预算预测的样本要剔掉「余额调整」这种一次性校准单） */
    @Query(
        "SELECT strftime('%Y-%m-%d', tradeTime / 1000, 'unixepoch', 'localtime') AS period, " +
            "COALESCE(SUM(CASE WHEN type = 2 THEN amount ELSE 0 END), 0) AS income, " +
            "COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) AS expense " +
            "FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0 " +
            "AND tradeTime >= :start AND tradeTime < :end " +
            "AND (categoryId IS NULL OR categoryId != :excludeCategoryId) " +
            "GROUP BY period ORDER BY period"
    )
    suspend fun sumByDayExcluding(
        bookId: Long,
        start: Long,
        end: Long,
        excludeCategoryId: Long
    ): List<PeriodAmountRow>

    /** 按记账人聚合收支（家庭账本「谁花了多少」）；笔数不含转账 */
    @Query(
        "SELECT createdBy AS createdBy, " +
            "COALESCE(SUM(CASE WHEN type = 2 THEN amount ELSE 0 END), 0) AS income, " +
            "COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) AS expense, " +
            "COALESCE(SUM(CASE WHEN type != 3 THEN 1 ELSE 0 END), 0) AS txCount " +
            "FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0 " +
            "AND tradeTime >= :start AND tradeTime < :end " +
            "GROUP BY createdBy ORDER BY expense DESC"
    )
    suspend fun sumByMember(bookId: Long, start: Long, end: Long): List<MemberAmountRow>

    /* ---------------- 统计页增强（日 / 月 / 年 + 多维度） ---------------- */

    /** 区间总览：收支 / 笔数 / 转账 / 记账天数，一次查全 */
    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 2 THEN amount ELSE 0 END), 0) AS income, " +
            "COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) AS expense, " +
            "COALESCE(SUM(CASE WHEN type = 2 THEN 1 ELSE 0 END), 0) AS incomeCount, " +
            "COALESCE(SUM(CASE WHEN type = 1 THEN 1 ELSE 0 END), 0) AS expenseCount, " +
            "COALESCE(SUM(CASE WHEN type = 3 THEN 1 ELSE 0 END), 0) AS transferCount, " +
            "COALESCE(SUM(CASE WHEN type = 3 THEN amount ELSE 0 END), 0) AS transferAmount, " +
            "COUNT(DISTINCT CASE WHEN type != 3 THEN " +
            "  strftime('%Y-%m-%d', tradeTime / 1000, 'unixepoch', 'localtime') END) AS activeDays " +
            "FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0 " +
            "AND tradeTime >= :start AND tradeTime < :end"
    )
    suspend fun rangeSummary(bookId: Long, start: Long, end: Long): RangeSummaryRow

    /**
     * 区间内的账单明细（日历模块按月一次性取回来，点某天在内存里分组）。
     * <p>
     * 过滤条件与上面的聚合查询完全一致（同一个账本、同一套软删除规则），
     * 这样「格子里显示 38 元」和「点开看到的几条明细」不会打架。
     */
    @Query(
        "SELECT * FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0 " +
            "AND tradeTime >= :start AND tradeTime < :end ORDER BY tradeTime DESC, id DESC"
    )
    suspend fun listBetween(bookId: Long, start: Long, end: Long): List<LocalTransaction>

    /** 最早一笔账单的时刻；日历用它当下界，用户不必往 1970 年滑 */
    @Query(
        "SELECT MIN(tradeTime) FROM local_transaction " +
            "WHERE bookId = :bookId AND pendingDelete = 0"
    )
    suspend fun minTradeTime(bookId: Long): Long?

    /**
     * 区间内某个分类的笔数（左闭右开，按账本隔离）。
     * <p>
     * 与上面那个按分类查引用的 [countByCategory] 分开命名：财富页用它判断区间里有没有
     * 「余额调整」这类一次性校准，好在算收益时剔掉——一笔人工校准不是市场涨跌。
     */
    @Query(
        "SELECT COUNT(*) FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0 " +
            "AND tradeTime >= :start AND tradeTime < :end AND categoryId = :categoryId"
    )
    suspend fun countByCategoryInRange(bookId: Long, start: Long, end: Long, categoryId: Long): Int

    /** 按自然月聚合（年视图的 12 根柱），period 为 yyyy-MM */
    @Query(
        "SELECT strftime('%Y-%m', tradeTime / 1000, 'unixepoch', 'localtime') AS period, " +
            "COALESCE(SUM(CASE WHEN type = 2 THEN amount ELSE 0 END), 0) AS income, " +
            "COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) AS expense " +
            "FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0 " +
            "AND tradeTime >= :start AND tradeTime < :end GROUP BY period ORDER BY period"
    )
    suspend fun sumByMonth(bookId: Long, start: Long, end: Long): List<PeriodAmountRow>

    /**
     * 按自然月聚合，排除某个分类。
     * <p>
     * 财富规划用近 24 个月的样本估年支出，必须剔掉「余额调整」：
     * 一次 5000 元的余额校准会把年支出拉高一大截，FI 数字跟着虚高。
     */
    @Query(
        "SELECT strftime('%Y-%m', tradeTime / 1000, 'unixepoch', 'localtime') AS period, " +
            "COALESCE(SUM(CASE WHEN type = 2 THEN amount ELSE 0 END), 0) AS income, " +
            "COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) AS expense " +
            "FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0 " +
            "AND tradeTime >= :start AND tradeTime < :end " +
            "AND (categoryId IS NULL OR categoryId != :excludeCategoryId) " +
            "GROUP BY period ORDER BY period"
    )
    suspend fun sumByMonthExcluding(
        bookId: Long,
        start: Long,
        end: Long,
        excludeCategoryId: Long
    ): List<PeriodAmountRow>

    /** 按小时聚合（日视图的 24 根柱），period 为 00-23 */
    @Query(
        "SELECT strftime('%H', tradeTime / 1000, 'unixepoch', 'localtime') AS period, " +
            "COALESCE(SUM(CASE WHEN type = 2 THEN amount ELSE 0 END), 0) AS income, " +
            "COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) AS expense " +
            "FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0 " +
            "AND tradeTime >= :start AND tradeTime < :end GROUP BY period ORDER BY period"
    )
    suspend fun sumByHour(bookId: Long, start: Long, end: Long): List<PeriodAmountRow>

    /** 按星期几聚合（记账习惯：哪天最爱花钱） */
    @Query(
        "SELECT CAST(strftime('%w', tradeTime / 1000, 'unixepoch', 'localtime') AS INTEGER) AS weekday, " +
            "COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) AS expense, " +
            "COUNT(*) AS txCount " +
            "FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0 AND type != 3 " +
            "AND tradeTime >= :start AND tradeTime < :end GROUP BY weekday ORDER BY weekday"
    )
    suspend fun sumByWeekday(bookId: Long, start: Long, end: Long): List<WeekdayAmountRow>

    /** 按账户聚合的收支排行（不含转账） */
    @Query(
        "SELECT accountId AS accountId, " +
            "COALESCE(SUM(CASE WHEN type = 2 THEN amount ELSE 0 END), 0) AS income, " +
            "COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) AS expense, " +
            "COUNT(*) AS txCount " +
            "FROM local_transaction WHERE bookId = :bookId AND pendingDelete = 0 AND type != 3 " +
            "AND tradeTime >= :start AND tradeTime < :end GROUP BY accountId"
    )
    suspend fun sumByAccountGroup(bookId: Long, start: Long, end: Long): List<AccountAmountRow>

    /** 区间内带标签的账单（标签原文 + 金额）；逗号串的拆分统计在内存里做 */
    @Query(
        "SELECT tags AS tags, type AS type, amount AS amount FROM local_transaction " +
            "WHERE bookId = :bookId AND pendingDelete = 0 AND type != 3 " +
            "AND tags IS NOT NULL AND tags != '' " +
            "AND tradeTime >= :start AND tradeTime < :end"
    )
    suspend fun listTagAmounts(bookId: Long, start: Long, end: Long): List<TagAmountRow>

    /** 区间内首笔 / 末笔记账时刻 */
    @Query(
        "SELECT MIN(tradeTime) AS firstTime, MAX(tradeTime) AS lastTime FROM local_transaction " +
            "WHERE bookId = :bookId AND pendingDelete = 0 AND type != 3 " +
            "AND tradeTime >= :start AND tradeTime < :end"
    )
    suspend fun timeBounds(bookId: Long, start: Long, end: Long): TimeBoundRow

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

    /** 分类管理页：按账本实时监听（新建 / 删除后列表自动刷新） */
    @Query("SELECT * FROM local_category WHERE bookId = :bookId ORDER BY sort, id")
    fun observeByBook(bookId: Long): Flow<List<LocalCategory>>

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
        /** 带余额的账户投影，后面按「是否排除归档」拼 WHERE */
        const val BALANCE_SELECT =
            "SELECT a.id AS id, a.bookId AS bookId, a.name AS name, a.type AS type, " +
                "a.initialBalance AS initialBalance, a.icon AS icon, a.sort AS sort, " +
                "a.note AS note, a.archived AS archived, a.includeInTotal AS includeInTotal, " +
                "(COALESCE(a.initialBalance, 0) " +
                " + COALESCE((SELECT SUM(CASE WHEN t.type = 2 THEN t.amount ELSE -t.amount END) " +
                "   FROM local_transaction t WHERE t.accountId = a.id AND t.pendingDelete = 0), 0) " +
                " + COALESCE((SELECT SUM(t.amount) FROM local_transaction t " +
                "   WHERE t.toAccountId = a.id AND t.type = 3 AND t.pendingDelete = 0), 0)" +
                ") AS balance " +
                "FROM local_account a "

        const val BALANCE_SQL = BALANCE_SELECT + "WHERE a.bookId = :bookId ORDER BY a.sort, a.id"

        /**
         * 与 [BALANCE_SELECT] 同样的余额算法，但只统计 [:before] 之前的流水：
         * 统计页的「期末净资产」要的是某个时间点的存量，而不是此刻。
         */
        const val BALANCE_AT_SELECT =
            "SELECT a.id AS id, a.bookId AS bookId, a.name AS name, a.type AS type, " +
                "a.initialBalance AS initialBalance, a.icon AS icon, a.sort AS sort, " +
                "a.note AS note, a.archived AS archived, a.includeInTotal AS includeInTotal, " +
                "(COALESCE(a.initialBalance, 0) " +
                " + COALESCE((SELECT SUM(CASE WHEN t.type = 2 THEN t.amount ELSE -t.amount END) " +
                "   FROM local_transaction t WHERE t.accountId = a.id AND t.pendingDelete = 0 " +
                "   AND t.tradeTime < :before), 0) " +
                " + COALESCE((SELECT SUM(t.amount) FROM local_transaction t " +
                "   WHERE t.toAccountId = a.id AND t.type = 3 AND t.pendingDelete = 0 " +
                "   AND t.tradeTime < :before), 0)" +
                ") AS balance " +
                "FROM local_account a "
    }

    @Query(BALANCE_SQL)
    fun observeWithBalance(bookId: Long): Flow<List<AccountWithBalance>>

    @Query(BALANCE_SQL)
    suspend fun listWithBalance(bookId: Long): List<AccountWithBalance>

    /**
     * [:before] 时刻的账户余额快照，统计页用它算期末净资产。
     * <p>
     * 注意：账户没有创建时间，`initialBalance` 一律按「自始存在」计入，历史快照无法还原开户时点。
     */
    @Query(BALANCE_AT_SELECT + "WHERE a.bookId = :bookId ORDER BY a.sort, a.id")
    suspend fun listWithBalanceBefore(bookId: Long, before: Long): List<AccountWithBalance>

    /** 未归档账户（记账页选择、账单页筛选用）：归档过的账户不再参与新记账 */
    @Query(
        "SELECT * FROM local_account WHERE bookId = :bookId AND archived = 0 ORDER BY sort, id"
    )
    suspend fun listActiveByBook(bookId: Long): List<LocalAccount>

    @Query(
        "SELECT * FROM local_account WHERE bookId = :bookId AND archived = 0 ORDER BY sort, id"
    )
    fun observeActiveByBook(bookId: Long): Flow<List<LocalAccount>>

    @Query("SELECT * FROM local_account WHERE bookId = :bookId ORDER BY sort, id")
    suspend fun listByBook(bookId: Long): List<LocalAccount>

    /** 账单页筛选项、记账页账户选择用：账户变化后要能实时反映 */
    @Query("SELECT * FROM local_account WHERE bookId = :bookId ORDER BY sort, id")
    fun observeByBook(bookId: Long): Flow<List<LocalAccount>>

    /** 调整账户排序（账户管理页拖拽/上下移用） */
    @Query("UPDATE local_account SET sort = :sort WHERE id = :id")
    suspend fun updateSort(id: Long, sort: Int)

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
