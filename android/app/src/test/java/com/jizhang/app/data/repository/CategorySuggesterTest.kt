package com.jizhang.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「备注 / 标签 → 分类」规则版的测试。
 * <p>
 * 这套规则的失败方式是「静默猜错」：界面不会报错，只是把一个错误的分类摆在用户手边，
 * 点一下就记错了账。所以每种匹配关系、每个「宁可不猜」的边界都要在这里写清楚。
 */
class CategorySuggesterTest {

    private val allowed = setOf(1L, 2L, 3L)

    private fun sample(
        remark: String? = null,
        tags: String? = null,
        categoryId: Long,
        time: Long = 0L
    ) = NoteSample(remark = remark, tags = tags, categoryId = categoryId, tradeTime = time)

    @Test
    fun `备注完全相同直接命中`() {
        val guess = CategorySuggester.guess(
            note = "午饭牛肉面",
            samples = listOf(sample(remark = "午饭牛肉面", categoryId = 1L)),
            allowedCategoryIds = allowed
        )

        assertEquals(1L, guess?.categoryId)
        assertEquals(GuessSource.REMARK, guess?.source)
        assertTrue("完全相同应当标记为精确命中", guess?.exact == true)
    }

    @Test
    fun `空格标点大小写差异不算不同`() {
        val guess = CategorySuggester.guess(
            note = "Lunch 午饭，牛肉面！",
            samples = listOf(sample(remark = "lunch午饭牛肉面", categoryId = 1L)),
            allowedCategoryIds = allowed
        )

        assertEquals(1L, guess?.categoryId)
        assertTrue(guess?.exact == true)
    }

    @Test
    fun `当前备注包含历史片段也能命中`() {
        val guess = CategorySuggester.guess(
            note = "午饭牛肉面加蛋",
            samples = listOf(sample(remark = "牛肉面", categoryId = 2L)),
            allowedCategoryIds = allowed
        )

        assertEquals(2L, guess?.categoryId)
        assertTrue("包含关系不应算精确命中", guess?.exact == false)
    }

    @Test
    fun `当前备注被历史备注包含也能命中`() {
        val guess = CategorySuggester.guess(
            note = "咖啡",
            samples = listOf(sample(remark = "公司楼下咖啡", categoryId = 3L)),
            allowedCategoryIds = allowed
        )

        assertEquals(3L, guess?.categoryId)
    }

    @Test
    fun `更具体的命中优先于更笼统的`() {
        val guess = CategorySuggester.guess(
            note = "午饭牛肉面",
            samples = listOf(
                sample(remark = "午饭", categoryId = 1L),
                sample(remark = "牛肉面", categoryId = 2L)
            ),
            allowedCategoryIds = allowed
        )

        // 「牛肉面」比「午饭」长，更能说明这笔记的是什么
        assertEquals(2L, guess?.categoryId)
    }

    @Test
    fun `同一个说法记过不同分类时取更近的一次`() {
        val guess = CategorySuggester.guess(
            note = "咖啡",
            samples = listOf(
                sample(remark = "咖啡", categoryId = 1L, time = 100L),
                sample(remark = "咖啡", categoryId = 3L, time = 200L)
            ),
            allowedCategoryIds = allowed
        )

        assertEquals(3L, guess?.categoryId)
    }

    /* ---------------- 标签 ---------------- */

    @Test
    fun `点标签也能猜出分类`() {
        // 只打过标签、从不写备注的账本（这类用户很多）必须同样能命中
        val guess = CategorySuggester.guess(
            note = "打车",
            samples = listOf(sample(tags = "打车,加班", categoryId = 2L)),
            allowedCategoryIds = allowed
        )

        assertEquals(2L, guess?.categoryId)
        assertEquals(GuessSource.TAG, guess?.source)
        assertEquals("打车", guess?.matchedText)
    }

    @Test
    fun `标签字面完全相同时比备注更可信`() {
        // 标签是从候选里挑的受控词表，备注是自由文本：同样四个字，前者更硬
        val guess = CategorySuggester.guess(
            note = "外卖",
            samples = listOf(
                sample(remark = "外卖", categoryId = 1L, time = 300L),
                sample(tags = "外卖", categoryId = 2L, time = 100L)
            ),
            allowedCategoryIds = allowed
        )

        assertEquals(2L, guess?.categoryId)
        assertEquals(GuessSource.TAG, guess?.source)
    }

    @Test
    fun `单字标签不参与匹配`() {
        // 「餐」这种单字标签一旦按包含去匹配，记什么都可能被猜成餐饮
        val guess = CategorySuggester.guess(
            note = "餐饮支出",
            samples = listOf(sample(tags = "餐", categoryId = 1L)),
            allowedCategoryIds = allowed
        )

        assertNull(guess)
    }

    @Test
    fun `备注与标签都为空的历史不参与`() {
        assertNull(
            CategorySuggester.guess(
                note = "午饭",
                samples = listOf(sample(categoryId = 1L)),
                allowedCategoryIds = allowed
            )
        )
    }

    /* ---------------- 宁可不猜 ---------------- */

    @Test
    fun `单字不猜`() {
        // 「水」这种单字几乎能命中一切，猜错比不猜更烦
        assertNull(
            CategorySuggester.guess(
                note = "水",
                samples = listOf(sample(remark = "水", categoryId = 1L), sample(remark = "水果", categoryId = 2L)),
                allowedCategoryIds = allowed
            )
        )
    }

    @Test
    fun `不在当前收支类型里的分类不参与`() {
        // 切到「收入」时，历史里全是支出分类：一个都不该被推荐
        val guess = CategorySuggester.guess(
            note = "工资",
            samples = listOf(sample(tags = "工资", categoryId = 9L)),
            allowedCategoryIds = setOf(1L, 2L, 3L)
        )

        assertNull(guess)
    }

    @Test
    fun `没有历史时不猜`() {
        assertNull(
            CategorySuggester.guess(
                note = "午饭牛肉面",
                samples = emptyList(),
                allowedCategoryIds = allowed
            )
        )
    }
}
