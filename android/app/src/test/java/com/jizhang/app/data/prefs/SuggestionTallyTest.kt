package com.jizhang.app.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「备注猜分类」统计口径的测试。
 * <p>
 * 设置页把这个数字摆在用户面前当作「猜得准不准」，口径错一位就是误导：
 * 尤其是分母 —— 提示过但用户什么都没点的那些，既不算采纳也不算改选，必须剔除。
 */
class SuggestionTallyTest {

    @Test
    fun `一条数据都没有时不给采纳率`() {
        val tally = SuggestionTally()

        assertEquals(0, tally.shown)
        assertEquals(0, tally.answered)
        // null 而不是 0：0 会被读成「一次都没猜对」
        assertNull(tally.acceptance)
    }

    @Test
    fun `只提示过但没人做选择时不给采纳率`() {
        val tally = SuggestionTally(shown = 9)

        assertEquals(0, tally.answered)
        assertNull(tally.acceptance)
    }

    @Test
    fun `采纳率的分母只算做过选择的次数`() {
        val tally = SuggestionTally(shown = 30, accepted = 3, overridden = 1)

        assertEquals(4, tally.answered)
        assertEquals(0.75, tally.acceptance!!, 1e-9)
    }

    @Test
    fun `全部改选时采纳率为零而不是空`() {
        val tally = SuggestionTally(shown = 6, accepted = 0, overridden = 2)

        assertEquals(0.0, tally.acceptance!!, 1e-9)
    }
}
