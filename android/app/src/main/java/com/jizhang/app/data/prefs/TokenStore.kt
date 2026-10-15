package com.jizhang.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jizhang.app.data.remote.dto.TokenDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "jizhang_prefs")

/**
 * 登录态与同步游标的本地存储（DataStore）。
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val NICKNAME = stringPreferencesKey("nickname")
        val CURRENT_BOOK_ID = stringPreferencesKey("current_book_id")
        val DEVICE_ID = stringPreferencesKey("device_id")

        /** 同步游标按账本分开存：多账本各自增量拉取，不能共用一个游标 */
        fun lastSyncAt(bookId: Long) = stringPreferencesKey("last_sync_at_$bookId")
    }

    val isLoggedIn: Flow<Boolean> =
        context.dataStore.data.map { !it[Keys.ACCESS_TOKEN].isNullOrBlank() }

    /** 当前账本，0 表示未登录时的本地账本 */
    val currentBookIdFlow: Flow<Long> =
        context.dataStore.data.map { it[Keys.CURRENT_BOOK_ID]?.toLongOrNull() ?: 0L }

    suspend fun currentBookId(): Long = context.dataStore.data.first()[Keys.CURRENT_BOOK_ID]?.toLongOrNull() ?: 0L

    val nicknameFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.NICKNAME] ?: it[Keys.USERNAME] ?: "" }

    val userIdFlow: Flow<Long> =
        context.dataStore.data.map { it[Keys.USER_ID]?.toLongOrNull() ?: 0L }

    suspend fun accessToken(): String? = context.dataStore.data.first()[Keys.ACCESS_TOKEN]

    suspend fun refreshToken(): String? = context.dataStore.data.first()[Keys.REFRESH_TOKEN]

    /** 供 OkHttp 拦截器同步读取 */
    fun accessTokenBlocking(): String? = runBlocking { accessToken() }

    suspend fun saveLogin(token: TokenDto) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = token.accessToken
            prefs[Keys.REFRESH_TOKEN] = token.refreshToken
            prefs[Keys.USER_ID] = token.user.id.toString()
            prefs[Keys.USERNAME] = token.user.username
            prefs[Keys.NICKNAME] = token.user.nickname ?: token.user.username
        }
    }

    suspend fun saveCurrentBookId(bookId: Long) {
        context.dataStore.edit { it[Keys.CURRENT_BOOK_ID] = bookId.toString() }
    }

    suspend fun lastSyncAt(bookId: Long): String? =
        context.dataStore.data.first()[Keys.lastSyncAt(bookId)]

    suspend fun saveLastSyncAt(bookId: Long, value: String) {
        context.dataStore.edit { it[Keys.lastSyncAt(bookId)] = value }
    }

    /** 设备标识，首次使用时生成并固化 */
    suspend fun deviceId(): String {
        val existing = context.dataStore.data.first()[Keys.DEVICE_ID]
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val generated = "android-" + UUID.randomUUID().toString().replace("-", "").take(16)
        context.dataStore.edit { it[Keys.DEVICE_ID] = generated }
        return generated
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
