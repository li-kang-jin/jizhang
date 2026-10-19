package com.jizhang.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 本地账本。
 *
 * 「账本即团队」：个人账本（[type] = 0）成员只有自己，家庭账本（[type] = 1）成员为全家。
 * 全 App 的记账 / 统计 / 预算都作用于「当前账本」，因此这里必须是一张表而不是单个 bookId 字段。
 *
 * [id] = 0 是未登录时的本地账本（[remote] = false），登录后服务端账本的 id 为雪花 ID。
 */
@Entity(tableName = "local_book")
data class LocalBook(
    @PrimaryKey val id: Long,
    val name: String,
    val icon: String? = null,
    /** 0=个人（仅自己可见） 1=家庭 2=共享（1/2 都是「成员互相可见」的多人账本，服务端同一套逻辑） */
    val type: Int = 0,
    val ownerId: Long = 0,
    /** 当前用户在本账本中的角色 0=只读 1=可写 2=管理员（所有者） */
    val myRole: Int = 2,
    val memberCount: Int = 1,
    /** 是否为「我的个人账本」：本地迁移数据的唯一合法去处 */
    val personal: Boolean = false,
    /** 是否为服务端账本 */
    val remote: Boolean = false,
    /** 展示排序：个人账本在前 */
    val sortOrder: Int = 0
) {
    /** 是否可以记账（只读成员不行） */
    val writable: Boolean get() = myRole >= 1

    /** 是否为家庭 / 共享账本 */
    val shared: Boolean get() = type != 0
}

/**
 * 账本成员的本地缓存。
 *
 * 用途有两个：家庭账本的流水里展示「谁记的」，以及统计页按成员汇总。
 * 两者都基于本地数据计算，因此成员名单必须落库，不能只在管理页临时拉取。
 */
@Entity(
    tableName = "local_book_member",
    primaryKeys = ["bookId", "userId"],
    indices = [Index("bookId")]
)
data class LocalBookMember(
    val bookId: Long,
    val userId: Long,
    val username: String = "",
    val nickname: String? = null,
    /** 家庭内称呼，如「爸爸」 */
    val alias: String? = null,
    /** 0=只读 1=可写 2=管理员 */
    val role: Int = 1,
    val owner: Boolean = false
) {
    /** 展示名：优先家庭内称呼，其次昵称，最后登录名 */
    val label: String
        get() = alias?.takeIf { it.isNotBlank() }
            ?: nickname?.takeIf { it.isNotBlank() }
            ?: username
}

