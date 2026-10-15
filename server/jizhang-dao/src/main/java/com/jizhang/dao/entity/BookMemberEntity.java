package com.jizhang.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/**
 * 账本成员（支持共享账本）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_book_member")
public class BookMemberEntity extends BaseEntity {

    private Long bookId;

    private Long userId;

    /** 0=只读 1=可写 2=管理员 */
    private Integer role;

    /** 家庭内称呼（如「爸爸」），为空时前端展示昵称 */
    private String alias;

    /** 加入时间 */
    private OffsetDateTime joinedAt;
}
