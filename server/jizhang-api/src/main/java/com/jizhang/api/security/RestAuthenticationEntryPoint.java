package com.jizhang.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhang.common.api.Result;
import com.jizhang.common.api.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未认证时的统一响应。
 * <p>
 * 这里刻意使用标准的 HTTP 401（而不是业务上的 200 + code），原因是移动端需要在
 * OkHttp 的 {@code Authenticator} 里捕获 401 并自动用 refreshToken 换新令牌后重放请求，
 * 而 Authenticator 只对非 2xx 响应生效。
 * <p>
 * 注意：不要设置 {@code WWW-Authenticate} 响应头，否则浏览器会弹出原生登录框。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ResultCode.UNAUTHORIZED)));
    }
}
