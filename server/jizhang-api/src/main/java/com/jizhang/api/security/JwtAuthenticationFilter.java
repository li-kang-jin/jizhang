package com.jizhang.api.security;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhang.common.api.Result;
import com.jizhang.common.api.ResultCode;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.common.context.LoginUser;
import com.jizhang.common.context.UserContext;
import com.jizhang.common.util.JwtUtils;
import com.jizhang.dao.entity.UserEntity;
import com.jizhang.dao.mapper.UserMapper;
import com.jizhang.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * JWT 认证过滤器：解析请求头中的 Bearer Token，写入 SecurityContext 与 {@link UserContext}。
 * <p>
 * 每次请求都会查一次用户状态与令牌版本。这对无状态 JWT 是「反直觉」的，但换来的是
 * 「禁用账号 / 改密 / 重置密码」立即生效——后台按钮写的是什么，实际就是什么。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtUtils jwtUtils, TokenBlacklistService tokenBlacklistService,
                                   UserMapper userMapper, ObjectMapper objectMapper) {
        this.jwtUtils = jwtUtils;
        this.tokenBlacklistService = tokenBlacklistService;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
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
                    UserEntity user = userMapper.selectById(loginUser.getUserId());
                    if (isTokenValid(user, loginUser)) {
                        if (requiresPasswordChange(request, user)) {
                            writePasswordChangeRequired(response);
                            return;
                        }
                        UserContext.set(loginUser);
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                loginUser, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + loginUser.getRole())));
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            // 线程池复用，必须清理，避免上下文串号
            UserContext.clear();
        }
    }

    /** 账号存在、未被禁用、且令牌版本与库中一致才算有效 */
    private boolean isTokenValid(UserEntity user, LoginUser loginUser) {
        if (user == null || !Objects.equals(user.getStatus(), CommonConstants.STATUS_ENABLED)) {
            return false;
        }
        int currentVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        return currentVersion == loginUser.getTokenVersion();
    }

    /**
     * 管理员仍在使用初始密码时，除登录/改密外的管理接口一律拒绝。
     * 只约束后台：App 端的 mustChangePassword 仍按「提示但不拦登录」处理，避免旧客户端无法进入改密入口。
     */
    private boolean requiresPasswordChange(HttpServletRequest request, UserEntity user) {
        if (!Objects.equals(user.getMustChangePassword(), 1)) {
            return false;
        }
        String adminPrefix = CommonConstants.API_PREFIX + "/admin/";
        String uri = request.getRequestURI();
        return uri.startsWith(adminPrefix) && !uri.startsWith(adminPrefix + "auth/");
    }

    private void writePasswordChangeRequired(HttpServletResponse response) throws IOException {
        // 与业务异常保持一致：HTTP 200 + 业务码，前端统一在响应拦截器里处理
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.fail(ResultCode.PASSWORD_CHANGE_REQUIRED)));
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(CommonConstants.TOKEN_HEADER);
        if (StrUtil.isNotBlank(header) && header.startsWith(CommonConstants.TOKEN_PREFIX)) {
            return header.substring(CommonConstants.TOKEN_PREFIX.length()).trim();
        }
        return null;
    }
}
