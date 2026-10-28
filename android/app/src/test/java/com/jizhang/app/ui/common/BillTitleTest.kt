package com.jizhang.app.ui.common

import com.jizhang.app.data.local.entity.LocalTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 账单标题回退顺序的测试：备注 → 标签 → 分类名 → 收支类型。
 * <p>
 * 这个顺序决定「一屏账单能不能看出钱花在哪」：只打标签的账如果退回分类名，
 * 同一个分类下十笔账的标题会一模一样。用户从别的软件迁移过来时，
 * 老账只有备注、新账只有标签，两边必须落在同一套回退规则里。
 */
class BillTitleTest {

    private fun bill(remark: String? = null, tags: String? = null, type: Int = 1) = LocalTransaction(
        id = 1L,
        bookId = 1L,
        type = type,
        amount = 1000,
        accountId = 1L,
        categoryId = 1L,
        tradeTime = 0L,
        remark = remark,
        tags = tags
    )

    @Test
    fun `有备注时备注就是标题`() {
        assertEquals("午饭牛肉面", billTitle(bill(remark = "午饭牛肉面", tags = "外卖"), "餐饮"))
    }

    @Test
    fun `没有备注时用标签拼成标题`() {
        assertEquals("外卖 · 楼下小馆", billTitle(bill(tags = "外卖,楼下小馆"), "餐饮"))
    }

    @Test
    fun `备注是空白串时也让位给标签`() {
        assertEquals("外卖", billTitle(bill(remark = "   ", tags = "外卖"), "餐饮"))
    }

    @Test
    fun `备注和标签都没有时退回分类名`() {
        assertEquals("餐饮", billTitle(bill(), "餐饮"))
    }

    @Test
    fun `分类也没有时退回收支类型`() {
        assertEquals("支出", billTitle(bill(), null))
        assertEquals("收入", billTitle(bill(type = 2), ""))
    }

    @Test
    fun `标签一旦顶上标题就不再重复渲染芯片`() {
        assertTrue(tagsUsedAsTitle(bill(tags = "外卖")))
        // 备注当标题时，标签是额外信息，仍要显示
        assertFalse(tagsUsedAsTitle(bill(remark = "午饭", tags = "外卖")))
        // 没有标签时无所谓重复，行为保持原样
        assertFalse(tagsUsedAsTitle(bill(remark = "午饭")))
    }
}
