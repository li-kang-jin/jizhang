package com.jizhang.app.data.remote

import com.jizhang.app.BuildConfig
import com.jizhang.app.data.prefs.SettingsStore
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.remote.dto.RefreshTokenRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 令牌自动续期。
 *
 * 后端统一响应体是「HTTP 200 + 业务码」，但「未认证（令牌过期）」这种情况刻意返回标准
 * HTTP 401 —— 就是为了让这里能捕获到、自动用 refreshToken 换新令牌并重放原请求，
 * 避免用户每两小时被踢回登录页。
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val json: Json,
    private val settingsStore: SettingsStore
) : Authenticator {

    /** 独立的 Retrofit（不带本 Authenticator），避免刷新失败时无限递归 */
    private val refreshApi: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(KotlinxJsonConverterFactory(json))
            .build()
            .create(ApiService::class.java)
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        // 离线模式：不续期、更不能清登录态。
        // 这里的 refreshApi 是独立 Retrofit（绕过主 client 的离线拦截器），所以必须单独挡住；
        // 下面每条失败分支都会 clear() 登录态，离线时误清一次就得重新登录。
        if (settingsStore.offlineModeBlocking()) {
            return null
        }
        // 原请求本来就没带令牌（例如登录接口），不处理
        if (response.request.header(HEADER) == null) {
            return null
        }
        // 重试过一次仍然 401，说明刷新也救不回来，清理登录态让上层跳登录页
        if (responseCount(response) > 1) {
            runBlocking { tokenStore.clear() }
            return null
        }

        val refreshToken = runBlocking { tokenStore.refreshToken() }
        if (refreshToken.isNullOrBlank()) {
            runBlocking { tokenStore.clear() }
            return null
        }

        val newToken = runBlocking {
            runCatching { refreshApi.refreshToken(RefreshTokenRequest(refreshToken)) }
                .getOrNull()
                ?.takeIf { it.isSuccess }
                ?.data
        }

        if (newToken == null) {
            runBlocking { tokenStore.clear() }
            return null
        }

        runBlocking { tokenStore.saveLogin(newToken) }

        return response.request.newBuilder()
            .header(HEADER, "Bearer ${newToken.accessToken}")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val HEADER = "Authorization"
    }
}
