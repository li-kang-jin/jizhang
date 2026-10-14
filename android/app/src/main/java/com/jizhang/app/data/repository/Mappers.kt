package com.jizhang.app.data.repository

import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.local.entity.LocalTransaction
import com.jizhang.app.data.remote.dto.AccountDto
import com.jizhang.app.data.remote.dto.CategoryDto
import com.jizhang.app.data.remote.dto.TransactionDto
import com.jizhang.app.data.remote.dto.TransactionSaveDto
import java.time.Instant
import java.time.OffsetDateTime

/** ISO 8601 UTC 字符串 → epoch millis */
fun parseIsoToMillis(value: String?): Long =
    value?.let {
        runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
    } ?: System.currentTimeMillis()

/** epoch millis → ISO 8601 UTC 字符串 */
fun millisToIso(value: Long): String = Instant.ofEpochMilli(value).toString()

fun TransactionDto.toLocal(): LocalTransaction = LocalTransaction(
    id = id,
    bookId = bookId,
    type = type,
    amount = amount,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    tradeTime = parseIsoToMillis(tradeTime),
    remark = remark,
    pendingSync = false,
    serverUpdatedAt = updatedAt
)

fun LocalTransaction.toSaveDto(): TransactionSaveDto = TransactionSaveDto(
    id = id,
    bookId = bookId,
    type = type,
    amount = amount,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    tradeTime = millisToIso(tradeTime),
    remark = remark,
    source = 1,
    // 同步冲突判定以本地修改时间为准
    updatedAt = millisToIso(clientUpdatedAt)
)

fun CategoryDto.toLocal(): LocalCategory = LocalCategory(
    id = id,
    bookId = bookId,
    parentId = parentId,
    name = name,
    type = type,
    icon = icon,
    color = color,
    sort = sort
)

fun AccountDto.toLocal(): LocalAccount = LocalAccount(
    id = id,
    bookId = bookId,
    name = name,
    type = type,
    balance = balance,
    icon = icon,
    sort = sort
)
