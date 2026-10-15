package com.jizhang.app.di

import android.content.Context
import androidx.room.Room
import com.jizhang.app.data.local.JizhangDatabase
import com.jizhang.app.data.local.dao.AccountDao
import com.jizhang.app.data.local.dao.BookDao
import com.jizhang.app.data.local.dao.BookMemberDao
import com.jizhang.app.data.local.dao.BudgetDao
import com.jizhang.app.data.local.dao.CategoryDao
import com.jizhang.app.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): JizhangDatabase =
        Room.databaseBuilder(context, JizhangDatabase::class.java, JizhangDatabase.NAME)
            // 已定义迁移的版本走迁移路径；仅当出现未定义迁移的版本差时才重建库
            .addMigrations(
                JizhangDatabase.MIGRATION_3_4,
                JizhangDatabase.MIGRATION_4_5,
                JizhangDatabase.MIGRATION_5_6
            )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTransactionDao(database: JizhangDatabase): TransactionDao = database.transactionDao()

    @Provides
    fun provideCategoryDao(database: JizhangDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideAccountDao(database: JizhangDatabase): AccountDao = database.accountDao()

    @Provides
    fun provideBudgetDao(database: JizhangDatabase): BudgetDao = database.budgetDao()

    @Provides
    fun provideBookDao(database: JizhangDatabase): BookDao = database.bookDao()

    @Provides
    fun provideBookMemberDao(database: JizhangDatabase): BookMemberDao = database.bookMemberDao()
}
