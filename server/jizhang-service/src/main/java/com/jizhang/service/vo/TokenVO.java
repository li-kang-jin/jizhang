package com.jizhang.service.vo;

import lombok.Data;

/**
 * 登录 / 刷新令牌返回体。
 */
@Data
public class TokenVO {

    private String accessToken;

    private String refreshToken;

    /** accessToken 有效期（秒） */
    private Long expiresIn;

    private UserVO user;
}
