package com.jizhang.app.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * 极简的 kotlinx.serialization 转换器工厂。
 *
 * 之所以自行实现：官方 retrofit2-kotlinx-serialization-converter 在不同版本间
 * 包名与 API 有差异（0.8.0 为 `retrofit2.converter...`，1.0.0 变为
 * `com.jakewharton.retrofit2.converter...` 且不再提供 asConverterFactory），
 * 自行实现可避免版本耦合，逻辑本身也很简单。
 */
class KotlinxJsonConverterFactory(private val json: Json) : Converter.Factory() {

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *> {
        val deserializer = serializerOf(type)
        return Converter<ResponseBody, Any> { body ->
            json.decodeFromString(deserializer, body.string())
        }
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody> {
        val serializer = serializerOf(type)
        return Converter<Any, RequestBody> { value ->
            json.encodeToString(serializer, value).toRequestBody(JSON_MEDIA_TYPE)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun serializerOf(type: Type): KSerializer<Any> = serializer(type) as KSerializer<Any>

    companion object {
        val JSON_MEDIA_TYPE: MediaType = "application/json; charset=UTF-8".toMediaType()
    }
}
