package com.jizhang.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.jizhang.service.TokenBlacklistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于本地内存的令牌黑名单实现。
 * <p>
 * 特点：零外部依赖，开箱即用；缺点是单机生效、进程重启后丢失。
 * <p>
 * 生产环境（多实例部署）请替换为 Redis 实现：
 * <pre>
 *   SET blacklist:token:{sha256} 1 EX {ttlSeconds}
 * </pre>
 * 替换方式：新增一个带 {@code @Primary} 的 Redis 实现类即可，无需改动调用方。
 */
@Slf4j
@Component
public class InMemoryTokenBlacklistService implements TokenBlacklistService {

    private final Map<String, Long> store = new ConcurrentHashMap<>();

    /** 懒清理阈值，超过该数量时触发一次过期清理 */
    private static final int CLEAN_THRESHOLD = 1024;

    @Override
    public void blacklist(String token, long ttlSeconds) {
        if (token == null || ttlSeconds <= 0) {
            return;
        }
        if (store.size() > CLEAN_THRESHOLD) {
            cleanExpired();
        }
        store.put(hash(token), System.currentTimeMillis() + ttlSeconds * 1000);
    }

    @Override
    public boolean isBlacklisted(String token) {
        if (token == null) {
            return false;
        }
        String key = hash(token);
        Long expireAt = store.get(key);
        if (expireAt == null) {
            return false;
        }
        if (expireAt < System.currentTimeMillis()) {
            store.remove(key);
            return false;
        }
        return true;
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < now) {
                it.remove();
            }
        }
        log.debug("令牌黑名单已清理，剩余 {} 条", store.size());
    }

    /** 只存哈希，避免明文令牌驻留内存 */
    private String hash(String token) {
        return DigestUtil.sha256Hex(token);
    }
}
