package com.jizhang.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jizhang.app.data.local.entity.LocalBook
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    /** 账本切换器数据源：个人账本置顶，其余按 sortOrder */
    @Query("SELECT * FROM local_book ORDER BY personal DESC, sortOrder, id")
    fun observeAll(): Flow<List<LocalBook>>

    @Query("SELECT * FROM local_book ORDER BY personal DESC, sortOrder, id")
    suspend fun listAll(): List<LocalBook>

    @Query("SELECT * FROM local_book WHERE id = :id")
    suspend fun findById(id: Long): LocalBook?

    @Query("SELECT COUNT(*) FROM local_book WHERE id = :id")
    suspend fun exists(id: Long): Int

    /** 已登录且可同步的账本（排除本地账本） */
    @Query("SELECT * FROM local_book WHERE remote = 1 ORDER BY sortOrder, id")
    suspend fun listRemote(): List<LocalBook>

    /** 我的个人账本（本地迁移数据的唯一去处） */
    @Query("SELECT * FROM local_book WHERE personal = 1 AND remote = 1 LIMIT 1")
    suspend fun findPersonalBook(): LocalBook?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: LocalBook)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(books: List<LocalBook>)

    @Query("DELETE FROM local_book WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** 退出登录 / 重新拉取账本列表时清掉服务端账本，保留本地账本 */
    @Query("DELETE FROM local_book WHERE remote = 1")
    suspend fun clearRemote()
}
