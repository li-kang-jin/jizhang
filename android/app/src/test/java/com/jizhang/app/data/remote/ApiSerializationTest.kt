package com.jizhang.app.data.remote

import com.jizhang.app.data.remote.dto.ApiResult
import com.jizhang.app.data.remote.dto.CategoryReportDto
import com.jizhang.app.data.remote.dto.PageResult
import com.jizhang.app.data.remote.dto.SyncPushRequest
import com.jizhang.app.data.remote.dto.TransactionDto
import com.jizhang.app.data.remote.dto.TransactionSaveDto
import com.jizhang.app.data.remote.dto.UserDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 网络层序列化测试。
 *
 * 重点验证一个容易踩坑的地方：服务端把 Long 类型的 ID 序列化成字符串
 * （例如 "id": "2110230681534525442"），客户端必须开启 Json.isLenient 才能解析。
 */
class ApiSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `服务端用字符串返回的 Long 主键应能正确解析`() {
        val raw = """
            {
              "code": 0,
              "message": "success",
              "traceId": "a1b2c3d4",
              "data": {
                "id": "2110230681534525442",
                "username": "demo115302",
                "nickname": "演示账号",
                "role": "USER",
                "status": 1,
                "createdAt": "2026-10-14T03:53:03.341237Z"
              }
            }
        """.trimIndent()

        val result = json.decodeFromString<ApiResult<UserDto>>(raw)
        val data = result.data

        assertEquals(0, result.code)
        assertEquals(2110230681534525442L, data?.id)
        assertEquals("demo115302", data?.username)
    }

    @Test
    fun `data 为空时不应抛出异常`() {
        val raw = """{"code":401,"message":"未登录或登录已过期","traceId":"x"}"""

        val result = json.decodeFromString<ApiResult<UserDto>>(raw)

        assertEquals(401, result.code)
        assertNull(result.data)
    }

    @Test
    fun `分页响应应能解析且保留总数`() {
        val raw = """
            {
              "code": 0,
              "message": "success",
              "data": {
                "records": [
                  {
                    "id": "2110214000795938817",
                    "bookId": "2110213996203175937",
                    "type": 1,
                    "amount": 3500,
                    "accountId": "2110213996203175938",
                    "categoryId": "2110213996203175939",
                    "tradeTime": "2026-10-14T03:39:58Z",
                    "remark": "午餐",
                    "images": [],
                    "tags": ["工作餐"],
                    "source": 1
                  }
                ],
                "total": 1,
                "page": 1,
                "size": 20
              }
            }
        """.trimIndent()

        val result = json.decodeFromString<ApiResult<PageResult<TransactionDto>>>(raw)
        val page = result.data

        assertEquals(1L, page?.total)
        assertEquals(1, page?.records?.size)
        assertEquals(3500L, page?.records?.first()?.amount)
        assertEquals("午餐", page?.records?.first()?.remark)
        assertEquals(listOf("工作餐"), page?.records?.first()?.tags)
    }

    @Test
    fun `分类报表的空分类应能解析`() {
        val raw = """
            {
              "code": 0,
              "message": "success",
              "data": {
                "total": 8000,
                "items": [
                  {
                    "categoryId": "2110213996203175939",
                    "categoryName": "交通",
                    "color": "#42A5F5",
                    "amount": 5000,
                    "ratio": 0.625
                  }
                ]
              }
            }
        """.trimIndent()

        val result = json.decodeFromString<ApiResult<CategoryReportDto>>(raw)
        val report = result.data

        assertEquals(8000L, report?.total)
        assertEquals("交通", report?.items?.first()?.categoryName)
        assertNull(report?.items?.first()?.icon)
    }

    @Test
    fun `推送请求应序列化为服务端可识别的结构`() {
        val request = SyncPushRequest(
            deviceId = "android-test",
            bookId = 2110213996203175937L,
            transactions = listOf(
                TransactionSaveDto(
                    id = 9990000000000001L,
                    bookId = 2110213996203175937L,
                    type = 1,
                    amount = 1234,
                    accountId = 2110213996203175938L,
                    categoryId = 2110213996203175939L,
                    tradeTime = "2026-10-14T04:46:16Z",
                    remark = "离线记账",
                    updatedAt = "2026-10-14T04:46:16Z"
                )
            )
        )

        val encoded = json.encodeToString(SyncPushRequest.serializer(), request)

        // 客户端主键按数字上报，服务端以它做幂等写入
        assertTrue(encoded.contains("\"id\":9990000000000001"))
        assertTrue(encoded.contains("\"amount\":1234"))
        assertTrue(encoded.contains("离线记账"))
    }
}
