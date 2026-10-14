package com.jizhang.api.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.dao.entity.UserEntity;
import com.jizhang.dao.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动时创建默认管理员账号，已存在则跳过。
 * <p>
 * 默认账号：admin / admin123456 —— 请在生产环境登录后立即修改。
 */
@Slf4j
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123456";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountInitializer(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long exists = userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getUsername, DEFAULT_ADMIN_USERNAME));
        if (exists != null && exists > 0) {
            return;
        }
        UserEntity admin = new UserEntity();
        admin.setUsername(DEFAULT_ADMIN_USERNAME);
        admin.setPassword(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
        admin.setNickname("超级管理员");
        admin.setRole(CommonConstants.ROLE_ADMIN);
        admin.setStatus(CommonConstants.STATUS_ENABLED);
        userMapper.insert(admin);
        log.warn("已创建默认管理员账号: {} / {}，请登录后立即修改密码",
                DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_PASSWORD);
    }
}
