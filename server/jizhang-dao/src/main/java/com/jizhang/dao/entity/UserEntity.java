package com.jizhang.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/**
 * 用户。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class UserEntity extends BaseEntity {

    /** 登录名 */
    private String username;

    /** BCrypt 加密后的密码 */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    private String phone;

    private String email;

    /** USER / ADMIN */
    private String role;

    /** 0=禁用 1=正常 */
    private Integer status;

    /**
     * 1=密码已被管理员重置，登录后应尽快自行修改。
     * <p>
     * 不阻止登录：拦在登录口的话，用户拿着临时密码也进不去改密码，只能再找一次管理员。
     */
    private Integer mustChangePassword;

    private OffsetDateTime lastLoginAt;
}
