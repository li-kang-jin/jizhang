package com.jizhang.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jizhang.app.data.local.entity.LocalBookMember
import kotlinx.coroutines.flow.Flow

@Dao
interface BookMemberDao {

    @Query("SELECT * FROM local_book_member WHERE bookId = :bookId ORDER BY owner DESC, role DESC, userId")
    fun observeByBook(bookId: Long): Flow<List<LocalBookMember>>

    @Query("SELECT * FROM local_book_member WHERE bookId = :bookId ORDER BY owner DESC, role DESC, userId")
    suspend fun listByBook(bookId: Long): List<LocalBookMember>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LocalBookMember>)

    @Query("DELETE FROM local_book_member WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: Long)

    @Query("DELETE FROM local_book_member WHERE bookId IN (:bookIds)")
    suspend fun deleteByBooks(bookIds: List<Long>)

    @Query("DELETE FROM local_book_member")
    suspend fun clear()
}
