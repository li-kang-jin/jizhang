package com.jizhang.app.data.remote.dto

import com.jizhang.app.data.local.entity.LocalRecurringExpense
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/**
 * 固定支出规则（服务端 → 客户端）。
 * <p>
 * 只有规则本身，不含「本期预留了多少」：预留金额依赖本机时区与本地流水，
 * 由 [com.jizhang.app.data.repository.RecurringExpenses] 实时算，不能走接口。
 */
@Serializable
data class RecurringExpenseDto(
    val id: Long,
    val name: String = "",
    /** 0 表示未指定分类 */
    val categoryId: Long = 0,
    /** 频率：1=每周 2=每月 3=每年 */
    val freq: Int = LocalRecurringExpense.FREQ_MONTHLY,
    /** 每周用：1=周一 … 7=周日 */
    val dayOfWeek: Int = 1,
    /** 每月用：1-31；当月天数不足时取当月最后一天 */
    val dayOfMonth: Int = 1,
    /** 每年用：1-12 */
    val monthOfYear: Int = 1,
    val amount: Long = 0,
    /** 生效首日，yyyy-MM-dd */
    val startDate: String? = null,
    /** 失效日（含），yyyy-MM-dd；null 表示长期有效 */
    val endDate: String? = null,
    val enabled: Boolean = true,
    val remark: String? = null
) {
    fun toLocal(bookId: Long): LocalRecurringExpense = LocalRecurringExpense(
        id = id,
        bookId = bookId,
        name = name,
        categoryId = categoryId,
        freq = freq,
        dayOfWeek = dayOfWeek,
        dayOfMonth = dayOfMonth,
        monthOfYear = monthOfYear,
        amount = amount,
        startDate = startDate.toDateMillis() ?: 0L,
        endDate = endDate.toDateMillis(),
        enabled = enabled,
        remark = remark,
        // 服务端存的是权威版本，拉到本地后不再需要补推
        pendingSync = false
    )
}

/**
 * 保存固定支出（客户端 → 服务端）。
 * <p>
 * id 由客户端生成（毫秒时间戳左移 20 位 + 20 位随机数），服务端按 id 幂等写入：
 * 同名固定支出允许存在多条，没有可用的业务键，只能靠客户端 ID 保证多端不互相覆盖。
 */
@Serializable
data class RecurringExpenseSaveDto(
    val id: Long,
    val bookId: Long,
    val name: String,
    val categoryId: Long = 0,
    val freq: Int = LocalRecurringExpense.FREQ_MONTHLY,
    val dayOfWeek: Int = 1,
    val dayOfMonth: Int = 1,
    val monthOfYear: Int = 1,
    val amount: Long,
    /** 生效首日，yyyy-MM-dd */
    val startDate: String,
    val endDate: String? = null,
    val enabled: Boolean = true,
    val remark: String? = null
) {
    companion object {
        fun from(item: LocalRecurringExpense): RecurringExpenseSaveDto = RecurringExpenseSaveDto(
            id = item.id,
            bookId = item.bookId,
            name = item.name,
            categoryId = item.categoryId,
            freq = item.freq,
            dayOfWeek = item.dayOfWeek,
            dayOfMonth = item.dayOfMonth,
            monthOfYear = item.monthOfYear,
            amount = item.amount,
            startDate = item.startDate.toDateString(),
            endDate = item.endDate?.toDateString(),
            enabled = item.enabled,
            remark = item.remark
        )
    }
}

/** epoch millis（当地 0 点）→ yyyy-MM-dd */
private fun Long.toDateString(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMAT)

/** yyyy-MM-dd → epoch millis（当地 0 点）；解析失败按 null 处理，不让一条坏数据拖垮整次同步 */
private fun String?.toDateMillis(): Long? {
    if (this.isNullOrBlank()) {
        return null
    }
    return runCatching {
        LocalDate.parse(this.trim(), DATE_FORMAT)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}
