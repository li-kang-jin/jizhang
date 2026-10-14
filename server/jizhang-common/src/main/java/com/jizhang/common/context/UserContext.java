package com.jizhang.common.context;

import com.jizhang.common.api.ResultCode;
import com.jizhang.common.exception.BizException;

/**
 * 基于 ThreadLocal 的当前登录用户上下文。
 * 由认证过滤器写入，请求结束后必须调用 {@link #clear()} 释放，避免线程池复用导致串号。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 获取当前用户，未登录时抛 401 */
    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long requireUserId() {
        return require().getUserId();
    }

    public static Long getUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
