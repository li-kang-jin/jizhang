package com.jizhang.app.ui.category

import com.jizhang.app.data.local.entity.LocalCategory
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 合并目标的排序测试。
 * <p>
 * 导入之后分类列表里混着「正经分类」和一堆只记过几笔的细分项，用户点「合并」时
 * 一眼就要能看到该并到哪去。排序错了不会报错，只会让人在十几个选项里多找几秒，
 * 或者在错误的目标上点下去——后者代价更大，所以顺序值得钉住。
 */
class MergeCandidatesTest {

    private fun category(id: Long, name: String) =
        LocalCategory(id = id, bookId = 1L, name = name, type = 1)

    private fun usage(count: Int, total: Long) = CategoryViewModel.Usage(count = count, totalFen = total)

    @Test
    fun `自己不出现在候选里`() {
        val categories = listOf(category(1, "餐饮"), category(2, "外卖"))

        val result = mergeCandidates(categories, emptyMap(), excludeId = 2)

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `笔数多的排在前面`() {
        val categories = listOf(category(1, "餐饮"), category(2, "外卖"), category(3, "夜宵"))
        val usage = mapOf(1L to usage(30, 500_000), 2L to usage(2, 3_000), 3L to usage(8, 60_000))

        val result = mergeCandidates(categories, usage, excludeId = 99)

        assertEquals(listOf(1L, 3L, 2L), result.map { it.id })
    }

    @Test
    fun `笔数相同时按金额排`() {
        val categories = listOf(category(1, "补差"), category(2, "房租"))
        // 都是 1 笔，但房租 3000 元、补差 3 元：房租才是该当目标的正经分类
        val usage = mapOf(1L to usage(1, 300), 2L to usage(1, 300_000))

        val result = mergeCandidates(categories, usage, excludeId = 99)

        assertEquals(listOf(2L, 1L), result.map { it.id })
    }

    @Test
    fun `没有用量的分类排在最后但仍可选`() {
        val categories = listOf(category(1, "空分类"), category(2, "餐饮"))
        val usage = mapOf(2L to usage(5, 10_000))

        val result = mergeCandidates(categories, usage, excludeId = 99)

        assertEquals(listOf(2L, 1L), result.map { it.id })
        assertEquals(1L, result.last().id)
    }
}
