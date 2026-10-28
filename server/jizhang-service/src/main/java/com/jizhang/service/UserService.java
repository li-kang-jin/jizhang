package com.jizhang.service;

import com.jizhang.service.dto.ChangePasswordRequest;
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

    /**
     * 当前登录用户修改自己的密码（需校验原密码）。
     * <p>
     * 与管理员重置密码配套：重置只会发一个临时密码，用户必须能自己改成记得住的。
     */
    void changePassword(ChangePasswordRequest request);
}
