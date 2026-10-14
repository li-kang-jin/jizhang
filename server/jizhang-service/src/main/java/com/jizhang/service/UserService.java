package com.jizhang.service;

import com.jizhang.service.dto.LoginRequest;
import com.jizhang.service.dto.RegisterRequest;
import com.jizhang.service.vo.TokenVO;
import com.jizhang.service.vo.UserVO;

/**
 * 用户与认证服务。
 */
public interface UserService {

    /** 注册，并自动创建默认账本、账户与分类 */
    UserVO register(RegisterRequest request);

    /** 登录，返回令牌 */
    TokenVO login(LoginRequest request);

    /** 用 refreshToken 换取新的令牌对 */
    TokenVO refresh(String refreshToken);

    /** 登出，将 accessToken 加入黑名单 */
    void logout(String accessToken);

    /** 当前登录用户信息 */
    UserVO currentUser();
}
