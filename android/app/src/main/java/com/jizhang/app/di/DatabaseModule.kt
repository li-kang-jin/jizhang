package com.jizhang.app.di

import android.content.Context
import androidx.room.Room
import com.jizhang.app.data.local.JizhangDatabase
import com.jizhang.app.data.local.dao.AccountDao
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
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTransactionDao(database: JizhangDatabase): TransactionDao = database.transactionDao()

    @Provides
    fun provideCategoryDao(database: JizhangDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideAccountDao(database: JizhangDatabase): AccountDao = database.accountDao()
}
