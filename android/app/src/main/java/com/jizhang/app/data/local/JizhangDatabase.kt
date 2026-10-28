package com.jizhang.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jizhang.app.data.local.dao.AccountDao
import com.jizhang.app.data.local.dao.BookDao
import com.jizhang.app.data.local.dao.BookMemberDao
import com.jizhang.app.data.local.dao.BudgetDao
import com.jizhang.app.data.local.dao.CategoryDao
import com.jizhang.app.data.local.dao.InvestmentDao
import com.jizhang.app.data.local.dao.RecurringExpenseDao
import com.jizhang.app.data.local.dao.TransactionDao
import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.local.entity.LocalBook
import com.jizhang.app.data.local.entity.LocalBookMember
import com.jizhang.app.data.local.entity.LocalBudget
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.local.entity.LocalInvestPosition
import com.jizhang.app.data.local.entity.LocalInvestValuation
import com.jizhang.app.data.local.entity.LocalRecurringExpense
import com.jizhang.app.data.local.entity.LocalTransaction

@Database(
    entities = [
        LocalTransaction::class,
        LocalCategory::class,
        LocalAccount::class,
        LocalBudget::class,
        LocalBook::class,
        LocalBookMember::class,
        LocalRecurringExpense::class,
        LocalInvestPosition::class,
        LocalInvestValuation::class
    ],
    // v2：local_transaction 增加 pendingDelete（离线删除支持）
    // v3：local_account 改为 initialBalance（余额实时聚合）+ 本地账本模式
    // v4：新增 local_budget（预算）
    // v5：local_transaction 增加 tags（账单导入的标签列）
    // v6：新增 local_book（多账本 / 家庭共享账本）
    // v7：新增 local_recurring_expense（固定支出，只参与预算预留，不自动记账）
    // v8：local_recurring_expense 增加 pendingSync（固定支出参与多端同步）
    // v9：local_account 增加 note / archived（账户备注与归档停用）
    // v10：local_budget / local_recurring_expense 增加 pendingDelete（离线删除的墓碑）
    // v11：local_account 增加 includeInTotal（是否计入总资产，统计页资产口径要用）
    // v12：新增 local_invest_position / local_invest_valuation（投资持仓与估值快照）
    version = 12,
    exportSchema = false
)
abstract class JizhangDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun categoryDao(): CategoryDao

    abstract fun accountDao(): AccountDao

    abstract fun budgetDao(): BudgetDao

    abstract fun recurringExpenseDao(): RecurringExpenseDao

    abstract fun bookDao(): BookDao

    abstract fun bookMemberDao(): BookMemberDao

    abstract fun investmentDao(): InvestmentDao

    companion object {
        const val NAME = "jizhang.db"

        /** 未登录时使用的本地账本 ID */
        const val LOCAL_BOOK_ID = 0L

        /**
         * 3 -> 4：只新增 local_budget 表，不动既有数据。
         * <p>
         * 注意不要依赖 fallbackToDestructiveMigration 来加表 —— 那会清空用户
         * 还没同步上去的本地记账数据。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `local_budget` (" +
                        "`id` INTEGER NOT NULL PRIMARY KEY, " +
                        "`bookId` INTEGER NOT NULL, " +
                        "`categoryId` INTEGER NOT NULL, " +
                        "`period` TEXT NOT NULL, " +
                        "`amount` INTEGER NOT NULL, " +
                        "`remark` TEXT, " +
                        "`pendingSync` INTEGER NOT NULL DEFAULT 1, " +
                        "`clientUpdatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_local_budget_bookId` " +
                        "ON `local_budget` (`bookId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_local_budget_pendingSync` " +
                        "ON `local_budget` (`pendingSync`)"
                )
            }
        }

        /**
         * 4 -> 5：local_transaction 增加可空的 tags 列。
         * <p>
         * 可空新增列不影响既有数据，且用户本地未同步的记账不会被清空。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `local_transaction` ADD COLUMN `tags` TEXT")
            }
        }

        /**
         * 5 -> 6：多账本 / 家庭成员支持。
         * <ul>
         *   <li>新增 local_book 表，并写入一条本地账本记录（id = 0）。
         *       老用户的账单都挂在 bookId = 0 下，这条记录保证升级后切换器里仍有账本可选、
         *       不会出现「账本列表为空」导致看不到已有账单；</li>
         *   <li>新增 local_book_member 表（家庭成员缓存，用于「谁记的」与按成员统计）；</li>
         *   <li>local_transaction 增加 createdBy 列（记账人）。</li>
         * </ul>
         * 全部是新增表 / 可空新增列，不影响既有数据。
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `local_book` (" +
                        "`id` INTEGER NOT NULL PRIMARY KEY, " +
                        "`name` TEXT NOT NULL, " +
                        "`icon` TEXT, " +
                        "`type` INTEGER NOT NULL, " +
                        "`ownerId` INTEGER NOT NULL, " +
                        "`myRole` INTEGER NOT NULL, " +
                        "`memberCount` INTEGER NOT NULL, " +
                        "`personal` INTEGER NOT NULL, " +
                        "`remote` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `local_book` " +
                        "(`id`, `name`, `icon`, `type`, `ownerId`, `myRole`, `memberCount`, " +
                        "`personal`, `remote`, `sortOrder`) " +
                        "VALUES (0, '本地账本', 'book', 0, 0, 2, 1, 1, 0, 0)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `local_book_member` (" +
                        "`bookId` INTEGER NOT NULL, " +
                        "`userId` INTEGER NOT NULL, " +
                        "`username` TEXT NOT NULL, " +
                        "`nickname` TEXT, " +
                        "`alias` TEXT, " +
                        "`role` INTEGER NOT NULL, " +
                        "`owner` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`bookId`, `userId`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_local_book_member_bookId` " +
                        "ON `local_book_member` (`bookId`)"
                )
                db.execSQL("ALTER TABLE `local_transaction` ADD COLUMN `createdBy` INTEGER")
            }
        }

        /**
         * 6 -> 7：新增 local_recurring_expense（固定支出）。
         * <p>
         * 纯新增表，不动既有数据。
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `local_recurring_expense` (" +
                        "`id` INTEGER NOT NULL PRIMARY KEY, " +
                        "`bookId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`categoryId` INTEGER NOT NULL DEFAULT 0, " +
                        "`freq` INTEGER NOT NULL DEFAULT 2, " +
                        "`dayOfWeek` INTEGER NOT NULL DEFAULT 1, " +
                        "`dayOfMonth` INTEGER NOT NULL DEFAULT 1, " +
                        "`monthOfYear` INTEGER NOT NULL DEFAULT 1, " +
                        "`amount` INTEGER NOT NULL, " +
                        "`startDate` INTEGER NOT NULL DEFAULT 0, " +
                        "`endDate` INTEGER, " +
                        "`enabled` INTEGER NOT NULL DEFAULT 1, " +
                        "`remark` TEXT)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_local_recurring_expense_bookId` " +
                        "ON `local_recurring_expense` (`bookId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_local_recurring_expense_enabled` " +
                        "ON `local_recurring_expense` (`enabled`)"
                )
            }
        }

        /**
         * 7 -> 8：local_recurring_expense 增加 pendingSync（固定支出接入多端同步）。
         * <p>
         * 已有记录默认标记为待同步：它们此前只存在于本机，需要补推到服务端，
         * 否则换设备后规则会凭空消失。
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `local_recurring_expense` " +
                        "ADD COLUMN `pendingSync` INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_local_recurring_expense_pendingSync` " +
                        "ON `local_recurring_expense` (`pendingSync`)"
                )
            }
        }

        /**
         * 8 -> 9：local_account 增加 note / archived。
         * <p>
         * 两个都是可空 / 带默认值的列，既有账户自动落到「无备注、未归档」，不影响任何数据。
         * 服务端没有对应字段，因此这两个值只在本机生效（账户同步时不会被覆盖，见 AccountRepository）。
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `local_account` ADD COLUMN `note` TEXT")
                db.execSQL(
                    "ALTER TABLE `local_account` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * 9 -> 10：预算 / 固定支出增加 pendingDelete（离线删除的墓碑）。
         * <p>
         * 都是带默认值的布尔列，既有记录自动落到「未删除」，不影响任何数据。
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `local_budget` ADD COLUMN `pendingDelete` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `local_recurring_expense` " +
                        "ADD COLUMN `pendingDelete` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * 10 -> 11：local_account 增加 includeInTotal（是否计入总资产）。
         * <p>
         * 带默认值的列，既有账户自动落到「计入总资产」——与服务端默认值一致，
         * 不影响任何既有数据；服务端下次同步会把用户真正的选择带下来。
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `local_account` ADD COLUMN `includeInTotal` INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        /**
         * 11 -> 12：新增投资持仓与估值快照两张表。
         * <p>
         * 纯新增表，不动既有数据。投资模块当前**只在本机使用**，不参与同步
         * （`pendingSync` 等列先留着，将来接 SyncManager 时不用再改表）。
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 列定义必须与 LocalInvestPosition 严格一致：Room 在迁移后会比对表结构，
                // 多写一个 DEFAULT 而与实体声明不符，就会在升级那一刻崩溃
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `local_invest_position` (" +
                        "`id` INTEGER NOT NULL PRIMARY KEY, " +
                        "`bookId` INTEGER NOT NULL, " +
                        "`accountId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`code` TEXT, " +
                        "`kind` INTEGER NOT NULL, " +
                        "`costFen` INTEGER NOT NULL, " +
                        "`sharesScaled` INTEGER, " +
                        "`targetPercent` INTEGER NOT NULL, " +
                        "`sort` INTEGER NOT NULL, " +
                        "`note` TEXT, " +
                        "`archived` INTEGER NOT NULL, " +
                        "`pendingSync` INTEGER NOT NULL, " +
                        "`pendingDelete` INTEGER NOT NULL, " +
                        "`clientUpdatedAt` INTEGER NOT NULL, " +
                        "`serverUpdatedAt` INTEGER)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_local_invest_position_bookId` " +
                        "ON `local_invest_position` (`bookId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_local_invest_position_accountId` " +
                        "ON `local_invest_position` (`accountId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `local_invest_valuation` (" +
                        "`id` INTEGER NOT NULL PRIMARY KEY, " +
                        "`bookId` INTEGER NOT NULL, " +
                        "`positionId` INTEGER NOT NULL, " +
                        "`valueFen` INTEGER NOT NULL, " +
                        "`valuedAt` INTEGER NOT NULL, " +
                        "`pendingSync` INTEGER NOT NULL, " +
                        "`pendingDelete` INTEGER NOT NULL, " +
                        "`clientUpdatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_local_invest_valuation_positionId` " +
                        "ON `local_invest_valuation` (`positionId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_local_invest_valuation_bookId` " +
                        "ON `local_invest_valuation` (`bookId`)"
                )
            }
        }
    }
}
