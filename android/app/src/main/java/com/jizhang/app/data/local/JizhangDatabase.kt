package com.jizhang.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jizhang.app.data.local.dao.AccountDao
import com.jizhang.app.data.local.dao.CategoryDao
import com.jizhang.app.data.local.dao.TransactionDao
import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.local.entity.LocalTransaction

@Database(
    entities = [LocalTransaction::class, LocalCategory::class, LocalAccount::class],
    // v2：local_transaction 增加 pendingDelete 字段（离线删除支持）
    version = 2,
    exportSchema = false
)
abstract class JizhangDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun categoryDao(): CategoryDao

    abstract fun accountDao(): AccountDao

    companion object {
        const val NAME = "jizhang.db"
    }
}
