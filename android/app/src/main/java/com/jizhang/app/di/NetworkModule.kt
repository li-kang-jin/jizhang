package com.jizhang.app.di

import com.jizhang.app.BuildConfig
import com.jizhang.app.data.prefs.SettingsStore
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.remote.ApiService
import com.jizhang.app.data.remote.KotlinxJsonConverterFactory
import com.jizhang.app.data.remote.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        // 服务端把 Long 类型的 ID 序列化为字符串，需要宽松模式才能解析回 Long
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        tokenStore: TokenStore,
        tokenAuthenticator: TokenAuthenticator,
        settingsStore: SettingsStore
    ): OkHttpClient {
        // 离线模式的唯一出口闸门：所有网络请求在这里直接失败，做到「打开开关 = 完全不联网」。
        // 抛 IOException 而不是伪造响应：仓库层本来就按「网络失败」处理（待同步数据留在本地，
        // 下次再推）；若返回 401/503 会误触发令牌续期，甚至把登录态清掉。
        val offlineInterceptor = Interceptor { chain ->
            if (settingsStore.offlineModeBlocking()) {
                throw IOException("离线模式已开启，本次请求已跳过")
            }
            chain.proceed(chain.request())
        }

        val authInterceptor = Interceptor { chain ->
            val builder = chain.request().newBuilder()
            tokenStore.accessTokenBlocking()?.takeIf { it.isNotBlank() }?.let {
                builder.addHeader("Authorization", "Bearer $it")
            }
            chain.proceed(builder.build())
        }

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(offlineInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            // 收到 HTTP 401 时自动用 refreshToken 换新令牌并重放请求
            .authenticator(tokenAuthenticator)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(KotlinxJsonConverterFactory(json))
            .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService =
        retrofit.create(ApiService::class.java)
}
