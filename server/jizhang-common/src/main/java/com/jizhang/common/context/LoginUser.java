package com.jizhang.common.context;

import lombok.Data;

import java.io.Serializable;

/**
 * 当前登录用户信息（轻量，不含敏感字段）。
 */
@Data
public class LoginUser implements Serializable {

    private Long userId;

    private String username;

    /** USER / ADMIN */
    private String role;
}
