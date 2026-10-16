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
import com.jizhang.app.data.local.dao.RecurringExpenseDao
import com.jizhang.app.data.local.dao.TransactionDao
import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.local.entity.LocalBook
import com.jizhang.app.data.local.entity.LocalBookMember
import com.jizhang.app.data.local.entity.LocalBudget
import com.jizhang.app.data.local.entity.LocalCategory
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
        LocalRecurringExpense::class
    ],
    // v2：local_transaction 增加 pendingDelete（离线删除支持）
    // v3：local_account 改为 initialBalance（余额实时聚合）+ 本地账本模式
    // v4：新增 local_budget（预算）
    // v5：local_transaction 增加 tags（账单导入的标签列）
    // v6：新增 local_book（多账本 / 家庭共享账本）
    // v7：新增 local_recurring_expense（固定支出，只参与预算预留，不自动记账）
    // v8：local_recurring_expense 增加 pendingSync（固定支出参与多端同步）
    version = 8,
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
    }
}
