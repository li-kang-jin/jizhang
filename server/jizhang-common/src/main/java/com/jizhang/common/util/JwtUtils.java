package com.jizhang.common.util;

import com.jizhang.common.context.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 生成与解析。密钥、有效期来自配置项 jizhang.jwt.*。
 */
@Slf4j
@Component
public class JwtUtils {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey secretKey;

    @Getter
    private final long accessExpireSeconds;

    @Getter
    private final long refreshExpireSeconds;

    public JwtUtils(@Value("${jizhang.jwt.secret}") String secret,
                    @Value("${jizhang.jwt.access-expire-seconds:7200}") long accessExpireSeconds,
                    @Value("${jizhang.jwt.refresh-expire-seconds:604800}") long refreshExpireSeconds) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("jizhang.jwt.secret 长度不足 32 字节，HS256 要求至少 256 bit");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessExpireSeconds = accessExpireSeconds;
        this.refreshExpireSeconds = refreshExpireSeconds;
    }

    public String generateAccessToken(LoginUser user) {
        return build(user, accessExpireSeconds, TYPE_ACCESS);
    }

    public String generateRefreshToken(LoginUser user) {
        return build(user, refreshExpireSeconds, TYPE_REFRESH);
    }

    private String build(LoginUser user, long expireSeconds, String type) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_ROLE, user.getRole())
                .claim(CLAIM_TYPE, type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireSeconds * 1000))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析 Token。
     *
     * @return 解析成功返回用户信息；Token 无效/过期返回 null
     */
    public LoginUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            LoginUser user = new LoginUser();
            user.setUserId(Long.valueOf(claims.getSubject()));
            user.setUsername(claims.get(CLAIM_USERNAME, String.class));
            user.setRole(claims.get(CLAIM_ROLE, String.class));
            return user;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /** 是否为刷新令牌 */
    public boolean isRefreshToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build()
                    .parseSignedClaims(token).getPayload();
            return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** 获取 Token 剩余有效秒数，已过期返回 0 */
    public long getRemainingSeconds(String token) {
        try {
            Date expiration = Jwts.parser().verifyWith(secretKey).build()
                    .parseSignedClaims(token).getPayload().getExpiration();
            long remaining = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            return Math.max(remaining, 0);
        } catch (JwtException | IllegalArgumentException e) {
            return 0;
        }
    }
}
