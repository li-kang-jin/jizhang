package com.jizhang.service;

/**
 * 已登出令牌黑名单。
 */
public interface TokenBlacklistService {

    /**
     * 将令牌加入黑名单。
     *
     * @param token      原始令牌
     * @param ttlSeconds 剩余有效期（秒），过期后自动失效
     */
    void blacklist(String token, long ttlSeconds);

    /** 令牌是否已失效 */
    boolean isBlacklisted(String token);
}
