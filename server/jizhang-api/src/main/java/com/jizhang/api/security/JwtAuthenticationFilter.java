package com.jizhang.api.security;

import cn.hutool.core.util.StrUtil;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.common.context.LoginUser;
import com.jizhang.common.context.UserContext;
import com.jizhang.common.util.JwtUtils;
import com.jizhang.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：解析请求头中的 Bearer Token，写入 SecurityContext 与 {@link UserContext}。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtAuthenticationFilter(JwtUtils jwtUtils, TokenBlacklistService tokenBlacklistService) {
        this.jwtUtils = jwtUtils;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            if (StrUtil.isNotBlank(token)
                    && !tokenBlacklistService.isBlacklisted(token)
                    && !jwtUtils.isRefreshToken(token)) {
                LoginUser loginUser = jwtUtils.parse(token);
                if (loginUser != null) {
                    UserContext.set(loginUser);
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            loginUser, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + loginUser.getRole())));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            // 线程池复用，必须清理，避免上下文串号
            UserContext.clear();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(CommonConstants.TOKEN_HEADER);
        if (StrUtil.isNotBlank(header) && header.startsWith(CommonConstants.TOKEN_PREFIX)) {
            return header.substring(CommonConstants.TOKEN_PREFIX.length()).trim();
        }
        return null;
    }
}
