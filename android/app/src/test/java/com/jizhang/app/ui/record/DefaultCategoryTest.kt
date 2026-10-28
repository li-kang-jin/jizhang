package com.jizhang.app.ui.record

import com.jizhang.app.data.local.entity.LocalCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 记账页「默认分类」的纯逻辑测试。
 * <p>
 * 这段规则只在打开记账页的一瞬间生效，错了不会崩、也不会报错，只会让用户每笔多点一次
 * （或者更糟：默认选中「余额调整」，下一笔账被记成校准单），所以用单测把边界钉死。
 */
class DefaultCategoryTest {

    private fun category(id: Long, name: String) = LocalCategory(
        id = id,
        bookId = 1L,
        name = name,
        type = 1
    )

    @Test
    fun `用得最多的分类胜出`() {
        val categories = listOf(category(1, "餐饮"), category(2, "交通"), category(3, "购物"))
        val usage = mapOf(1L to 30L, 2L to 5L, 3L to 12L)

        assertEquals(1L, pickDefaultCategoryId(categories, usage))
    }

    @Test
    fun `次数相同时保持分类管理的顺序`() {
        val categories = listOf(category(7, "交通"), category(8, "餐饮"))
        val usage = mapOf(7L to 4L, 8L to 4L)

        // 手工顺序里「交通」在前，不能被 id 大小或 map 迭代顺序带偏
        assertEquals(7L, pickDefaultCategoryId(categories, usage))
    }

    @Test
    fun `没有任何历史账单时退回第一个分类`() {
        val categories = listOf(category(1, "餐饮"), category(2, "交通"))

        assertEquals(1L, pickDefaultCategoryId(categories, emptyMap()))
        // 用过的分类已经被删掉：计数表里留下的是查不到的 id，不能因此没有默认值
        assertEquals(1L, pickDefaultCategoryId(categories, mapOf(99L to 8L)))
    }

    @Test
    fun `余额调整不参与竞争`() {
        val categories = listOf(category(1, "餐饮"), category(2, "余额调整"))
        val usage = mapOf(1L to 3L, 2L to 50L)

        // 校准单再多也不能被选成默认分类，否则下一笔就被记成一笔校准
        assertEquals(1L, pickDefaultCategoryId(categories, usage, excludedName = "余额调整"))
    }

    @Test
    fun `只剩余额调整一个分类时仍然返回它`() {
        val categories = listOf(category(2, "余额调整"))

        assertEquals(2L, pickDefaultCategoryId(categories, mapOf(2L to 1L), excludedName = "余额调整"))
    }

    @Test
    fun `没有分类时返回 null`() {
        assertNull(pickDefaultCategoryId(emptyList(), emptyMap()))
    }
}
