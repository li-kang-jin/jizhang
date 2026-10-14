package com.jizhang.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 记账系统后端启动类。
 * <p>
 * 扫描范围提升到 {@code com.jizhang}，以便加载 common / dao / service 模块中的组件。
 */
@SpringBootApplication(scanBasePackages = "com.jizhang")
@MapperScan("com.jizhang.dao.mapper")
public class JizhangApplication {

    public static void main(String[] args) {
        SpringApplication.run(JizhangApplication.class, args);
    }
}
