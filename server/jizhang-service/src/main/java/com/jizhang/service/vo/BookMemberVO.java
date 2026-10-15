package com.jizhang.service.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class BookMemberVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String username;

    private String nickname;

    private String avatar;

    /** 0=只读 1=可写 2=管理员 */
    private Integer role;

    /** 是否为账本所有者 */
    private Boolean owner;

    /** 家庭内称呼（如「爸爸」），为空时前端展示昵称 */
    private String alias;

    private OffsetDateTime joinedAt;

    /** 展示名：优先 alias，其次 nickname，最后 username */
    private String displayName;
}
