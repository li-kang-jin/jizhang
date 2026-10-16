package com.jizhang.app.data.repository

import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.local.entity.LocalBook
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.local.entity.LocalTransaction
import com.jizhang.app.data.remote.dto.AccountDto
import com.jizhang.app.data.remote.dto.BookDto
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
    tags = tags.joinToString(",").ifBlank { null },
    createdBy = createdBy,
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
    tags = tags.toTagList(),
    source = 1,
    // 同步冲突判定以本地修改时间为准
    updatedAt = millisToIso(clientUpdatedAt)
)

/** 本地逗号分隔的标签 → 标签列表 */
fun String?.toTagList(): List<String> =
    this?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

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
    initialBalance = initialBalance,
    icon = icon,
    sort = sort,
    includeInTotal = includeInTotal
)

/**
 * 服务端账户 → 本地账户，并保留只存在于本地的字段（备注 / 归档）。
 *
 * 服务端没有 note / archived 两列，同步拉取时若直接用 DTO 覆盖本地，
 * 用户写的备注与「已归档」会在每次同步后被抹掉。
 *
 * `includeInTotal` 相反：它是服务端字段，必须由服务端版本覆盖本地，
 * 否则管理后台改过的「不计入总资产」永远同步不下来。
 *
 * @param existing 本地已有的同 id 账户（没有就是服务端新增的）
 */
fun AccountDto.toLocal(existing: LocalAccount?): LocalAccount =
    toLocal().copy(note = existing?.note, archived = existing?.archived ?: false)

/**
 * 服务端账本 → 本地账本。
 *
 * @param myUserId 当前登录用户 ID，用于判定「我的个人账本」（个人账本 = type 0 且 owner 是我）
 * @param sortOrder 列表展示顺序
 */
fun BookDto.toLocal(myUserId: Long, sortOrder: Int = 0): LocalBook = LocalBook(
    id = id,
    name = name,
    icon = icon,
    type = type,
    ownerId = ownerId ?: 0L,
    myRole = myRole ?: 1,
    memberCount = memberCount ?: 1,
    personal = type == 0 && ownerId != null && ownerId == myUserId,
    remote = true,
    sortOrder = sortOrder
)
