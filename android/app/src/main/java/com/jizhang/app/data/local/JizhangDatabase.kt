package com.jizhang.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jizhang.app.data.local.dao.AccountDao
import com.jizhang.app.data.local.dao.BudgetDao
import com.jizhang.app.data.local.dao.CategoryDao
import com.jizhang.app.data.local.dao.TransactionDao
import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.local.entity.LocalBudget
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.local.entity.LocalTransaction

@Database(
    entities = [
        LocalTransaction::class,
        LocalCategory::class,
        LocalAccount::class,
        LocalBudget::class
    ],
    // v2：local_transaction 增加 pendingDelete（离线删除支持）
    // v3：local_account 改为 initialBalance（余额实时聚合）+ 本地账本模式
    // v4：新增 local_budget（预算）
    // v5：local_transaction 增加 tags（账单导入的标签列）
    version = 5,
    exportSchema = false
)
abstract class JizhangDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun categoryDao(): CategoryDao

    abstract fun accountDao(): AccountDao

    abstract fun budgetDao(): BudgetDao

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
    }
}
