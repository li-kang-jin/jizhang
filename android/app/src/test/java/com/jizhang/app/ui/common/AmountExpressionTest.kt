package com.jizhang.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 金额算式的守门测试。
 * <p>
 * 这个函数直接决定用户填的成本会被记成多少钱：多算一位、少算一位都不会报错，
 * 只会让盈亏和净资产长期错下去，所以边界（尾随运算符、除零、非法字符）必须钉住。
 */
class AmountExpressionTest {

    @Test
    fun `追加投资可以直接相加`() {
        assertEquals(1750.0, evaluateAmount("1500+250")!!, 1e-9)
    }

    @Test
    fun `连续的加减按从左到右求值`() {
        assertEquals(1650.0, evaluateAmount("1500+250-100")!!, 1e-9)
    }

    @Test
    fun `乘除优先级高于加减`() {
        assertEquals(12.0, evaluateAmount("2+2*5")!!, 1e-9)
        assertEquals(5.0, evaluateAmount("10/2")!!, 1e-9)
    }

    @Test
    fun `支持小数与千分位`() {
        assertEquals(1750.5, evaluateAmount("1,500.5+250")!!, 1e-9)
        assertEquals(0.3, evaluateAmount("0.1+0.2")!!, 1e-9)
    }

    @Test
    fun `支持一元正负号`() {
        assertEquals(-250.0, evaluateAmount("-250")!!, 1e-9)
        assertEquals(1250.0, evaluateAmount("1500+-250")!!, 1e-9)
    }

    @Test
    fun `纯数字原样返回`() {
        assertEquals(1500.0, evaluateAmount("1500")!!, 1e-9)
        assertEquals(1500.0, evaluateAmount(" 1500 ")!!, 1e-9)
    }

    @Test
    fun `尾随运算符与非法输入返回 null`() {
        // 用户正在输入「1500+」的中间态：此时不能当成 1500，否则预览会闪出一个错数
        assertNull(evaluateAmount("1500+"))
        assertNull(evaluateAmount("abc"))
        assertNull(evaluateAmount("1500+abc"))
        assertNull(evaluateAmount(""))
        assertNull(evaluateAmount("   "))
        assertNull(evaluateAmount(null))
    }

    @Test
    fun `除零返回 null 而不是无穷大`() {
        assertNull(evaluateAmount("10/0"))
    }

    @Test
    fun `只有含运算符时才给预览`() {
        assertNotNull(amountExpressionPreview("1500+250"))
        assertEquals("= ¥1,750.00", amountExpressionPreview("1500+250"))
        assertNull(amountExpressionPreview("1500"))
        assertNull(amountExpressionPreview("1500+"))
    }
}
