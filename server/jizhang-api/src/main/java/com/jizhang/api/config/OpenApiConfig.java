package com.jizhang.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 在线接口文档配置。启动后访问 {@code http://localhost:8080/swagger-ui.html}。
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI jizhangOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("记账系统 API")
                        .version("v1")
                        .description("三端共用的后端接口：Android 原生 App + 管理后台")
                        .contact(new Contact().name("jizhang")))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("填入登录返回的 accessToken")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
