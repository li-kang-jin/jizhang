package com.jizhang.app.data.repository

import com.jizhang.app.data.local.dao.AccountDao
import com.jizhang.app.data.local.dao.CategoryDao
import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.local.entity.LocalCategory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地账本的默认数据。
 * <p>
 * 未登录也能记账：首次进入时若本地没有任何账户 / 分类，就按模板创建一套，
 * 保证「记一笔」永远有可选项，不会出现「没有账户可选」的死胡同。
 */
@Singleton
class LocalDataInitializer @Inject constructor(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao
) {

    private data class AccountTemplate(val name: String, val type: Int, val icon: String)

    private data class CategoryTemplate(
        val name: String,
        val type: Int,
        val icon: String,
        val color: String
    )

    /** 确保该账本下有可用的账户与分类 */
    suspend fun ensureLocalData(bookId: Long) {
        ensureAccounts(bookId)
        ensureCategories(bookId)
    }

    private suspend fun ensureAccounts(bookId: Long) {
        if (accountDao.countByBook(bookId) > 0) {
            return
        }
        var id = accountDao.maxId()
        DEFAULT_ACCOUNTS.forEachIndexed { index, template ->
            id += 1
            accountDao.upsert(
                LocalAccount(
                    id = id,
                    bookId = bookId,
                    name = template.name,
                    type = template.type,
                    initialBalance = 0L,
                    icon = template.icon,
                    sort = index
                )
            )
        }
    }

    private suspend fun ensureCategories(bookId: Long) {
        if (categoryDao.countByBook(bookId) > 0) {
            return
        }
        var id = categoryDao.maxId()
        var sort = 0
        (EXPENSE_CATEGORIES + INCOME_CATEGORIES).forEach { template ->
            id += 1
            categoryDao.upsert(
                LocalCategory(
                    id = id,
                    bookId = bookId,
                    parentId = 0L,
                    name = template.name,
                    type = template.type,
                    icon = template.icon,
                    color = template.color,
                    sort = sort++
                )
            )
        }
    }

    companion object {
        /** 账户类型：1=现金 2=储蓄卡 3=信用卡 4=支付宝 5=微信 6=投资 7=其他 */
        private val DEFAULT_ACCOUNTS = listOf(
            AccountTemplate("现金", 1, "wallet"),
            AccountTemplate("支付宝", 4, "wallet"),
            AccountTemplate("微信", 5, "wallet")
        )

        private val EXPENSE_CATEGORIES = listOf(
            CategoryTemplate("餐饮", 1, "restaurant", "#FF7B8A"),
            CategoryTemplate("交通", 1, "bus", "#5CA8FF"),
            CategoryTemplate("购物", 1, "shopping", "#C08CFF"),
            CategoryTemplate("居住", 1, "home", "#4FD1B5"),
            CategoryTemplate("娱乐", 1, "game", "#FFC24B"),
            CategoryTemplate("医疗", 1, "medical", "#FF8A8A"),
            CategoryTemplate("教育", 1, "book", "#7D9BFF"),
            CategoryTemplate("通讯", 1, "phone", "#4FC3F7"),
            CategoryTemplate("人情", 1, "gift", "#FF8FC7"),
            CategoryTemplate("其他", 1, "more", "#B0B7C3")
        )

        private val INCOME_CATEGORIES = listOf(
            CategoryTemplate("工资", 2, "salary", "#5FD3A0"),
            CategoryTemplate("奖金", 2, "award", "#FFB65C"),
            CategoryTemplate("投资", 2, "trend", "#4DD0E1"),
            CategoryTemplate("红包", 2, "gift", "#FF8A8A"),
            CategoryTemplate("其他", 2, "more", "#B0B7C3")
        )
    }
}
