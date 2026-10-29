package com.jizhang.service.support;

import com.jizhang.common.api.ResultCode;
import com.jizhang.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流：同一账号 15 分钟内连续失败 5 次即锁定。
 * <p>
 * 单实例内存实现。登录是低频操作，单体部署没必要为此引入共享存储；
 * 将来多实例部署时需要换成 Redis 一类的共享计数，否则每个实例各算各的。
 */
@Component
public class LoginAttemptGuard {

    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MILLIS = 15 * 60 * 1000L;
    private static final int CLEANUP_THRESHOLD = 1024;

    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    /** 已锁定则直接拒绝，避免继续做密码比对 */
    public void ensureAllowed(String key) {
        Deque<Long> window = failures.get(key);
        if (window == null) {
            return;
        }
        synchronized (window) {
            evictExpired(window);
            if (window.size() >= MAX_FAILURES) {
                throw new BizException(ResultCode.FORBIDDEN, "登录失败次数过多，请 15 分钟后再试");
            }
        }
    }

    public void recordFailure(String key) {
        cleanupIfNeeded();
        Deque<Long> window = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            evictExpired(window);
            window.addLast(System.currentTimeMillis());
        }
    }

    public void reset(String key) {
        failures.remove(key);
    }

    private void evictExpired(Deque<Long> window) {
        long cutoff = System.currentTimeMillis() - WINDOW_MILLIS;
        while (!window.isEmpty() && window.peekFirst() < cutoff) {
            window.removeFirst();
        }
    }

    /** 防止被随机用户名刷爆内存：只在超过阈值时清理已过窗口的记录 */
    private void cleanupIfNeeded() {
        if (failures.size() < CLEANUP_THRESHOLD) {
            return;
        }
        failures.entrySet().removeIf(entry -> {
            Deque<Long> window = entry.getValue();
            synchronized (window) {
                evictExpired(window);
                return window.isEmpty();
            }
        });
    }
}
