package com.jizhang.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class BookDto(
    val id: Long,
    val name: String,
    val icon: String? = null,
    val currency: String = "CNY",
    /** 0=个人（仅自己可见） 1=家庭/共享 */
    val type: Int = 0,
    val ownerId: Long? = null,
    val remark: String? = null,
    val myRole: Int? = null,
    val memberCount: Int? = null,
    /** 是否为「我的个人账本」 */
    val personal: Boolean? = null
)

/** 新建 / 修改账本 */
@Serializable
data class BookSaveDto(
    val name: String,
    val currency: String = "CNY",
    val type: Int = 0,
    val icon: String? = null,
    val remark: String? = null
)

/** 账本成员 */
@Serializable
data class BookMemberDto(
    val userId: Long,
    val username: String = "",
    val nickname: String? = null,
    val avatar: String? = null,
    /** 0=只读 1=可写 2=管理员 */
    val role: Int = 1,
    val owner: Boolean = false,
    /** 家庭内称呼，如「爸爸」 */
    val alias: String? = null,
    val joinedAt: String? = null,
    /** 展示名：优先 alias，其次昵称 */
    val displayName: String? = null
) {
    val label: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: nickname?.takeIf { it.isNotBlank() }
            ?: username
}

/** 修改成员角色 / 称呼 */
@Serializable
data class MemberUpdateDto(
    val role: Int? = null,
    val alias: String? = null
)

/** 邀请码 */
@Serializable
data class BookInviteDto(
    val code: String,
    val role: Int = 1,
    val expireAt: String? = null,
    /** 0 表示不限次数 */
    val maxUses: Int = 1,
    val usedCount: Int = 0,
    val createdAt: String? = null
)

/** 生成邀请码 */
@Serializable
data class InviteCreateDto(
    val role: Int = 1,
    /** 0 表示永不过期 */
    val expireDays: Int = 7,
    /** 0 表示不限次数 */
    val maxUses: Int = 1
)

/** 凭邀请码加入账本 */
@Serializable
data class JoinBookDto(
    val code: String
)

/** 家庭账本中单个成员的收支统计 */
@Serializable
data class MemberStatDto(
    val userId: Long? = null,
    val username: String? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val alias: String? = null,
    val displayName: String? = null,
    val income: Long = 0,
    val expense: Long = 0,
    val balance: Long = 0,
    val count: Long = 0
) {
    val label: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: nickname?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }
            ?: "未知成员"
}

@Serializable
data class MemberReportDto(
    val totalIncome: Long = 0,
    val totalExpense: Long = 0,
    val items: List<MemberStatDto> = emptyList()
)

@Serializable
data class AccountDto(
    val id: Long,
    val bookId: Long,
    val name: String,
    val type: Int,
    val balance: Long = 0,
    val initialBalance: Long = 0,
    val currency: String = "CNY",
    val icon: String? = null,
    val sort: Int = 0,
    val includeInTotal: Int = 1
)

/** 新增 / 修改账户的请求体 */
@Serializable
data class AccountSaveDto(
    val bookId: Long,
    val name: String,
    val type: Int,
    val initialBalance: Long = 0,
    val currency: String = "CNY",
    val icon: String? = null,
    val sort: Int = 0,
    val includeInTotal: Int = 1
)

@Serializable
data class CategoryDto(
    val id: Long,
    val bookId: Long = 0,
    val parentId: Long = 0,
    val name: String,
    val type: Int,
    val icon: String? = null,
    val color: String? = null,
    val sort: Int = 0,
    val system: Boolean = false,
    val children: List<CategoryDto> = emptyList()
)
