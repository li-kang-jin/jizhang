package com.jizhang.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 本地交易记录。这是「离线记账」的核心表：
 * 新增的交易先落本地（pendingSync = true），再由同步任务推送到服务端。
 */
@Entity(
    tableName = "local_transaction",
    indices = [Index("bookId"), Index("pendingSync"), Index("pendingDelete")]
)
data class LocalTransaction(
    @PrimaryKey val id: Long,
    val bookId: Long,
    /** 1=支出 2=收入 3=转账 */
    val type: Int,
    /** 金额（分） */
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    /** 交易时间（epoch millis） */
    val tradeTime: Long,
    val remark: String? = null,
    /** 标签，多个用逗号分隔（账单导入的「标签」列落在这里） */
    val tags: String? = null,
    /**
     * 记账人用户 ID。
     * <p>
     * 家庭账本里用来显示「这笔记的是谁记的」，以及统计页按成员汇总。
     * 服务端从同步接口下发；本地离线记账时为 null（同步后回填）。
     */
    val createdBy: Long? = null,
    /** 是否待同步到服务端 */
    val pendingSync: Boolean = true,
    /**
     * 是否已在本地删除、等待把删除动作同步到服务端。
     * <p>
     * 删除不能直接抹掉本地记录：那样断网时会丢失删除意图，
     * 服务端数据仍在，下次拉取又会把记录带回来。
     */
    val pendingDelete: Boolean = false,
    /** 本地最后修改时间（epoch millis），用于同步冲突判定 */
    val clientUpdatedAt: Long = System.currentTimeMillis(),
    /** 服务端返回的更新时间（ISO 8601），未同步时为 null */
    val serverUpdatedAt: String? = null
)

@Entity(tableName = "local_category", indices = [Index("bookId")])
data class LocalCategory(
    @PrimaryKey val id: Long,
    val bookId: Long,
    val parentId: Long = 0,
    val name: String,
    /** 1=支出 2=收入 */
    val type: Int,
    val icon: String? = null,
    val color: String? = null,
    val sort: Int = 0
)

/**
 * 本地账户。
 * <p>
 * 不存冗余的「当前余额」字段：余额由交易实时聚合得出，
 * 避免记账/删除/迁移等路径都要维护余额一致性。
 */
@Entity(tableName = "local_account", indices = [Index("bookId")])
data class LocalAccount(
    @PrimaryKey val id: Long,
    val bookId: Long,
    val name: String,
    /** 1=现金 2=储蓄卡 3=信用卡 4=支付宝 5=微信 6=投资 7=其他 */
    val type: Int,
    /** 初始余额（分） */
    val initialBalance: Long = 0,
    val icon: String? = null,
    val sort: Int = 0,
    /**
     * 备注（卡号后四位、额度提醒之类）。
     * <p>仅本地字段，服务端没有对应列，不同步。
     */
    val note: String? = null,
    /**
     * 已归档（停用）。
     * <p>
     * 与「删除」的区别：归档后不再出现在记账页的账户选择与账单页筛选里，但历史账单、
     * 余额统计照旧（钱不会凭空消失）。注销的卡、不再用的账户应该归档而不是删除。
     * 同样仅本地字段。
     */
    val archived: Boolean = false
)

/**
 * 本地预算。
 * <p>
 * 与交易一样走「离线优先」：先写本地并标记待同步，再由同步任务推给服务端。
 * categoryId 为 0 表示账本总预算。
 */
@Entity(
    tableName = "local_budget",
    indices = [Index("bookId"), Index("pendingSync")]
)
data class LocalBudget(
    @PrimaryKey val id: Long,
    val bookId: Long,
    /** 0 = 账本总预算 */
    val categoryId: Long = 0L,
    /** 预算周期，yyyy-MM */
    val period: String,
    /** 预算金额（分） */
    val amount: Long,
    val remark: String? = null,
    /** 是否待同步到服务端 */
    val pendingSync: Boolean = true,
    /**
     * 已删除但还没推送到服务端（墓碑）。
     * <p>
     * 断网时删掉一条已经同步过的预算，如果直接物理删除，下次同步拉取会把它「复活」；
     * 所以本地先打上墓碑（界面立刻不可见），由 [com.jizhang.app.data.repository.BudgetRepository.pushPending]
     * 删完服务端再物理删掉。
     */
    val pendingDelete: Boolean = false,
    /** 本地最后修改时间（epoch millis） */
    val clientUpdatedAt: Long = System.currentTimeMillis()
)

/**
 * 固定支出（房租 / 房贷 / 会员费这类按期必须支出的钱）。
 * <p>
 * 第一版只做「预算内预留」：**不自动生成账单**，只参与预算计算 ——
 * 把本期还没到期的固定支出从「每天可花」里扣掉，避免月中交完房租就被判定为超支。
 * 因为不动账单数据，所以没有「自动生成重复记账」和「用户已手动记过」这类需要去重的问题。
 * <p>
 * 与交易、预算一样走「离线优先」：先写本地并标记待同步，再由同步任务推给服务端。
 * 服务端只存规则本身，「本期预留了多少」始终由本机按本地时区 + 本地流水实时计算。
 */
@Entity(
    tableName = "local_recurring_expense",
    indices = [Index("bookId"), Index("enabled"), Index("pendingSync")]
)
data class LocalRecurringExpense(
    @PrimaryKey val id: Long,
    val bookId: Long,
    val name: String,
    /** 计入哪个分类的预算（同时也是「这是哪类固定开销」的归类） */
    val categoryId: Long = 0L,
    /** 频率：1=每周 2=每月 3=每年 */
    val freq: Int = FREQ_MONTHLY,
    /** 每周用：1=周一 … 7=周日 */
    val dayOfWeek: Int = 1,
    /** 每月用：1-31；当月天数不足时取当月最后一天（例如 31 号在小月按 30 号） */
    val dayOfMonth: Int = 1,
    /** 每年用：1-12 */
    val monthOfYear: Int = 1,
    /** 金额（分） */
    val amount: Long,
    /** 生效首日（含），epoch millis 的当地 0 点 */
    val startDate: Long = 0L,
    /** 失效日（含），null 表示长期有效 */
    val endDate: Long? = null,
    val enabled: Boolean = true,
    val remark: String? = null,
    /** 是否待同步到服务端 */
    val pendingSync: Boolean = true,
    /** 已删除但还没推送到服务端（墓碑），语义同 [LocalBudget.pendingDelete] */
    val pendingDelete: Boolean = false
) {
    companion object {
        const val FREQ_WEEKLY = 1
        const val FREQ_MONTHLY = 2
        const val FREQ_YEARLY = 3
    }
}

/**
 * 账户 + 实时余额的查询结果（Room 只读投影，非数据库表）。
 * <p>
 * 余额 = 初始余额 + 该账户交易的净流入，由 SQL 实时聚合，不落库。
 */
data class AccountWithBalance(
    val id: Long,
    val bookId: Long,
    val name: String,
    val type: Int,
    val initialBalance: Long,
    val icon: String?,
    val sort: Int,
    val balance: Long,
    val note: String? = null,
    val archived: Boolean = false
)
