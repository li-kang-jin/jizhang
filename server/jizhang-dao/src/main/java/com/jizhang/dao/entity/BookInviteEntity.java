package com.jizhang.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/**
 * 账本邀请码。
 * <p>
 * 家庭 / 共享账本通过邀请码拉人：所有者在账本里生成一个 code，
 * 家人输入 code 即成为 {@code t_book_member} 的一员。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_book_invite")
public class BookInviteEntity extends BaseEntity {

    private Long bookId;

    /** 邀请码，全局唯一 */
    private String code;

    /** 加入后的角色 0=只读 1=可写 2=管理员 */
    private Integer role;

    /** 过期时间，为空表示永不过期 */
    private OffsetDateTime expireAt;

    /** 最大使用次数，0 表示不限 */
    private Integer maxUses;

    private Integer usedCount;

    /** 0=已作废 1=有效 */
    private Integer status;
}
