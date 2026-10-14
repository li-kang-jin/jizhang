package com.jizhang.app.data.repository

import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.remote.ApiService
import com.jizhang.app.data.remote.dto.LoginRequest
import com.jizhang.app.data.remote.dto.RegisterRequest
import com.jizhang.app.data.remote.dto.UserDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val tokenStore: TokenStore
) {

    /** 登录并缓存令牌，同时选定默认账本（取第一个） */
    suspend fun login(username: String, password: String): Result<Unit> = runCatching {
        val result = api.login(LoginRequest(username, password))
        val token = result.data ?: error(result.message.ifBlank { "登录失败" })

        // 管理员账号没有默认账本，在 App 端登录只会卡在「未获取到账本」，直接拒绝并给出提示
        if (token.user.role == ROLE_ADMIN) {
            error("管理员账号请使用管理后台登录")
        }

        tokenStore.saveLogin(token)

        val books = api.books().data.orEmpty()
        books.firstOrNull()?.let { tokenStore.saveBookId(it.id) }
    }

    suspend fun register(username: String, password: String, nickname: String): Result<Unit> =
        runCatching {
            val result = api.register(RegisterRequest(username, password, nickname.ifBlank { null }))
            if (!result.isSuccess) {
                error(result.message.ifBlank { "注册失败" })
            }
        }

    suspend fun me(): Result<UserDto> = runCatching {
        api.me().data ?: error("获取用户信息失败")
    }

    suspend fun logout() {
        tokenStore.clear()
    }

    private companion object {
        const val ROLE_ADMIN = "ADMIN"
    }
}
