package com.jizhang.service.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

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
}
