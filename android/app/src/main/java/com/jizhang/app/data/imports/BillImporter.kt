package com.jizhang.app.data.imports

import com.jizhang.app.data.local.dao.AccountDao
import com.jizhang.app.data.local.dao.CategoryDao
import com.jizhang.app.data.local.dao.TransactionDao
import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.local.entity.LocalTransaction
import com.jizhang.app.data.local.newClientId
import javax.inject.Inject
import javax.inject.Singleton

/** 一次导入的结果统计 */
data class ImportSummary(
    /** 成功写入的账单条数 */
    val imported: Int = 0,
    /** 与已有账单重复而跳过的条数 */
    val duplicates: Int = 0,
    /** 缺少账户 / 对方账户等关键信息而无法导入的条数 */
    val invalid: Int = 0,
    /** 自动新建的账户数 */
    val createdAccounts: Int = 0,
    /** 自动新建的分类数 */
    val createdCategories: Int = 0
) {
    val total: Int get() = imported + duplicates + invalid
}

/**
 * 账单导入器：把 [ParsedBill] 落成本地交易。
 *
 * 设计要点：
 * 1. **账户 / 分类按名称匹配，缺失则自动新建**，否则用户导入后全是「未分类 / 无账户」的脏数据；
 * 2. 写入本地队列（`pendingSync = true`），复用既有的离线同步链路；
 * 3. 以「类型 + 时间 + 金额 + 账户 + 备注」作为去重键，重复导入同一份文件不会翻倍。
 */
@Singleton
class BillImporter @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao
) {

    suspend fun import(bookId: Long, bills: List<ParsedBill>): ImportSummary {
        if (bills.isEmpty()) return ImportSummary()

        val existingCategories = categoryDao.listByBook(bookId)
        val accountCache = accountDao.listByBook(bookId)
            .associateByTo(mutableMapOf()) { it.name.trim().lowercase() }
        val categoryCache = existingCategories
            .associateByTo(mutableMapOf()) { categoryKey(it.type, it.name) }

        var nextAccountId = accountDao.maxId()
        var nextAccountSort = accountCache.size
        var nextCategoryId = categoryDao.maxId()
        var nextCategorySort = existingCategories.size

        val newAccounts = mutableListOf<LocalAccount>()
        val newCategories = mutableListOf<LocalCategory>()
        val transactions = mutableListOf<LocalTransaction>()

        // 去重集合同时包含「库里已有」与「本次已生成」的账单
        val seen = transactionDao.listByBook(bookId)
            .mapTo(mutableSetOf()) { it.dedupeKey() }

        var duplicates = 0
        var invalid = 0

        bills.forEach { bill ->
            val accountName = bill.accountName?.trim().orEmpty()
            if (accountName.isEmpty()) {
                invalid++
                return@forEach
            }
            val account = accountCache[accountName.lowercase()] ?: LocalAccount(
                id = ++nextAccountId,
                bookId = bookId,
                name = accountName,
                type = guessAccountType(accountName),
                initialBalance = 0L,
                sort = nextAccountSort++
            ).also {
                newAccounts += it
                accountCache[accountName.lowercase()] = it
            }

            val toAccount = if (bill.type == TRANSFER) {
                val toName = bill.toAccountName?.trim().orEmpty()
                if (toName.isEmpty()) {
                    invalid++
                    return@forEach
                }
                accountCache[toName.lowercase()] ?: LocalAccount(
                    id = ++nextAccountId,
                    bookId = bookId,
                    name = toName,
                    type = guessAccountType(toName),
                    initialBalance = 0L,
                    sort = nextAccountSort++
                ).also {
                    newAccounts += it
                    accountCache[toName.lowercase()] = it
                }
            } else {
                null
            }

            val category = resolveCategory(
                bookId, bill, categoryCache, newCategories
            ) { ++nextCategoryId to nextCategorySort++ }

            val remark = bill.remarkWithAddress()
            if (!seen.add(dedupeKey(bill.type, bill.tradeTime, bill.amount, account.id, remark))) {
                duplicates++
                return@forEach
            }

            transactions += LocalTransaction(
                id = newClientId(),
                bookId = bookId,
                type = bill.type,
                amount = bill.amount,
                accountId = account.id,
                toAccountId = toAccount?.id,
                categoryId = category?.id,
                tradeTime = bill.tradeTime,
                remark = remark,
                tags = bill.tags.joinToString(",").ifBlank { null },
                pendingSync = true,
                pendingDelete = false
            )
        }

        if (newAccounts.isNotEmpty()) accountDao.upsertAll(newAccounts)
        if (newCategories.isNotEmpty()) categoryDao.upsertAll(newCategories)
        transactions.chunked(BATCH_SIZE).forEach { transactionDao.upsertAll(it) }

        return ImportSummary(
            imported = transactions.size,
            duplicates = duplicates,
            invalid = invalid,
            createdAccounts = newAccounts.size,
            createdCategories = newCategories.size
        )
    }

    private fun resolveCategory(
        bookId: Long,
        bill: ParsedBill,
        cache: MutableMap<String, LocalCategory>,
        created: MutableList<LocalCategory>,
        nextId: () -> Pair<Long, Int>
    ): LocalCategory? {
        // 转账没有业务分类
        if (bill.type == TRANSFER) return null
        val name = bill.categoryName?.trim().orEmpty()
        if (name.isEmpty()) return null
        val key = categoryKey(bill.type, name)
        return cache[key] ?: run {
            val (id, sort) = nextId()
            LocalCategory(
                id = id,
                bookId = bookId,
                parentId = 0L,
                name = name,
                type = bill.type,
                sort = sort
            ).also {
                created += it
                cache[key] = it
            }
        }
    }

    companion object {
        private const val TRANSFER = 3
        private const val BATCH_SIZE = 500

        private fun categoryKey(type: Int, name: String): String =
            "$type·${name.trim().lowercase()}"

        private fun dedupeKey(
            type: Int,
            tradeTime: Long,
            amount: Long,
            accountId: Long,
            remark: String?
        ): String = "$type|$tradeTime|$amount|$accountId|${remark.orEmpty()}"

        private fun LocalTransaction.dedupeKey(): String =
            dedupeKey(type, tradeTime, amount, accountId, remark)

        /** 备注与地址合并展示，避免导入时丢字段 */
        private fun ParsedBill.remarkWithAddress(): String? =
            listOfNotNull(
                remark?.trim()?.ifEmpty { null },
                address?.trim()?.ifEmpty { null }
            ).joinToString(" · ").ifBlank { null }

        /**
         * 按账户名推断账户类型（与 [com.jizhang.app.ui.common.accountTypeLabel] 的取值一致）。
         * 顺序敏感：先「微信 / 支付宝」这类明确品牌，再落到「卡 / 基金」等形态。
         */
        private fun guessAccountType(name: String): Int = when {
            name.contains("微信") -> 5
            name.contains("支付宝") || name.contains("花呗") -> 4
            name.contains("信用卡") -> 3
            name.contains("基金") || name.contains("理财") ||
                name.contains("投资") || name.contains("证券") ||
                name.contains("股票") -> 6
            name.contains("银行") || name.contains("储蓄") || name.contains("借记") -> 2
            name.contains("现金") || name.contains("钱包") -> 1
            else -> 7
        }
    }
}
