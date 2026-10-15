package com.jizhang.app.ui.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class CalcEngineTest {

    /** 从空表达式开始输入 */
    private fun input(vararg chars: Char): List<CalcToken> =
        emptyList<CalcToken>().type(*chars)

    /** 在当前表达式后继续输入数字 */
    private fun List<CalcToken>.type(vararg chars: Char): List<CalcToken> =
        chars.fold(this) { acc, c -> CalcEngine.appendDigit(acc, c) }

    private fun List<CalcToken>.op(symbol: Char): List<CalcToken> =
        CalcEngine.appendOp(this, symbol)

    @Test
    fun `输入数字并换算为分`() {
        val tokens = input('1', '2', '.', '5')

        assertEquals("12.5", CalcEngine.expressionText(tokens))
        assertEquals(1250L, CalcEngine.resultFen(tokens))
    }

    @Test
    fun `前导零只保留一位`() {
        val tokens = input('0', '5')

        assertEquals("5", CalcEngine.expressionText(tokens))
        assertEquals(500L, CalcEngine.resultFen(tokens))
    }

    @Test
    fun `小数最多两位`() {
        val tokens = input('1', '.', '2', '3', '4')

        assertEquals("1.23", CalcEngine.expressionText(tokens))
    }

    @Test
    fun `重复小数点被忽略`() {
        val tokens = input('1', '.', '2', '.', '3')

        assertEquals("1.23", CalcEngine.expressionText(tokens))
    }

    @Test
    fun `整数最多九位`() {
        val tokens = input('1', '2', '3', '4', '5', '6', '7', '8', '9', '0')

        assertEquals("123456789", CalcEngine.expressionText(tokens))
    }

    @Test
    fun `乘除优先于加减`() {
        // 2 + 3 × 4 = 14
        val tokens = input('2')
            .op('+')
            .type('3')
            .op('×')
            .type('4')

        assertEquals(BigDecimal("14"), CalcEngine.evaluate(tokens))
        assertEquals(1400L, CalcEngine.resultFen(tokens))
    }

    @Test
    fun `连续运算从左到右`() {
        // 10 - 2 - 3 = 5
        val tokens = input('1', '0')
            .op('-')
            .type('2')
            .op('-')
            .type('3')

        assertEquals(BigDecimal("5"), CalcEngine.evaluate(tokens))
    }

    @Test
    fun `除法保留两位小数`() {
        val tokens = input('1', '0')
            .op('÷')
            .type('3')

        assertEquals(BigDecimal("3.33"), CalcEngine.evaluate(tokens))
        assertEquals(333L, CalcEngine.resultFen(tokens))
    }

    @Test
    fun `除零无效`() {
        val tokens = input('5')
            .op('÷')
            .type('0')

        assertNull(CalcEngine.evaluate(tokens))
        assertNull(CalcEngine.resultFen(tokens))
    }

    @Test
    fun `运算符可以替换`() {
        val tokens = input('5')
            .op('+')
            .op('×')
            .type('2')

        assertEquals("5 × 2", CalcEngine.expressionText(tokens))
        assertEquals(1000L, CalcEngine.resultFen(tokens))
    }

    @Test
    fun `不允许以运算符开头`() {
        assertEquals(emptyList<CalcToken>(), CalcEngine.appendOp(emptyList(), '+'))
    }

    @Test
    fun `表达式不完整时无结果`() {
        val tokens = input('5').op('+')

        assertNull(CalcEngine.evaluate(tokens))
        assertNull(CalcEngine.resultFen(tokens))
    }

    @Test
    fun `等号把结果作为新输入`() {
        val tokens = input('5')
            .op('+')
            .type('5')
            .let { CalcEngine.applyEquals(it) }

        assertEquals("10", CalcEngine.expressionText(tokens))
        assertEquals(1000L, CalcEngine.resultFen(tokens))
    }

    @Test
    fun `删除逐位回退`() {
        val tokens = input('1', '2', '3')
        val step1 = CalcEngine.backspace(tokens)
        val step2 = CalcEngine.backspace(step1)
        val step3 = CalcEngine.backspace(step2)
        val step4 = CalcEngine.backspace(step3)

        assertEquals("12", CalcEngine.expressionText(step1))
        assertEquals("1", CalcEngine.expressionText(step2))
        assertEquals("", CalcEngine.expressionText(step3))
        assertEquals("", CalcEngine.expressionText(step4))
    }

    @Test
    fun `清空表达式`() {
        val tokens = input('1', '2')

        assertEquals(1, tokens.size)
        assertEquals(emptyList<CalcToken>(), CalcEngine.clear())
    }

    @Test
    fun `非正数金额无效`() {
        val tokens = input('5')
            .op('-')
            .type('5')

        assertEquals(BigDecimal.ZERO, CalcEngine.evaluate(tokens))
        assertNull(CalcEngine.resultFen(tokens))
    }
}
