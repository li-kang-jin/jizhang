package com.jizhang.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * 后端统一响应体。
 *
 * 注意：服务端把 Long 类型的 ID 序列化为字符串（避免 JS 精度丢失），
 * 因此 Json 需要开启 isLenient 才能把 "123" 解析为 Long。
 */
@Serializable
data class ApiResult<T>(
    val code: Int,
    val message: String = "",
    val data: T? = null,
    val traceId: String? = null
) {
    val isSuccess: Boolean get() = code == 0
}

@Serializable
data class PageResult<T>(
    val records: List<T> = emptyList(),
    val total: Long = 0,
    val page: Long = 1,
    val size: Long = 20
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val nickname: String? = null
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class UserDto(
    val id: Long,
    val username: String,
    val nickname: String? = null,
    val avatar: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val role: String? = null,
    val status: Int? = null
)

@Serializable
data class TokenDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long = 0,
    val user: UserDto
)
