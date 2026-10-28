package com.jizhang.service.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.jizhang.dao.entity.UserEntity;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户视图对象（不含密码等敏感字段）。
 * <p>
 * 所有 ID 序列化为字符串，避免 JS / Kotlin 端 Long 精度丢失。
 */
@Data
public class UserVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    private String phone;

    private String email;

    private String role;

    private Integer status;

    /** 注册时间 */
    private OffsetDateTime createdAt;

    /** 最后登录时间 */
    private OffsetDateTime lastLoginAt;

    /** 密码是否被管理员重置过（1=建议尽快修改），客户端据此提示用户 */
    private Integer mustChangePassword;

    public static UserVO from(UserEntity entity) {
        if (entity == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(entity.getId());
        vo.setUsername(entity.getUsername());
        vo.setNickname(entity.getNickname());
        vo.setAvatar(entity.getAvatar());
        vo.setPhone(entity.getPhone());
        vo.setEmail(entity.getEmail());
        vo.setRole(entity.getRole());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setLastLoginAt(entity.getLastLoginAt());
        vo.setMustChangePassword(entity.getMustChangePassword());
        return vo;
    }
}
