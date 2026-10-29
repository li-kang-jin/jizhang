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
 * 默认账号：admin / admin123456。建号时标记 must_change_password，
 * 首次登录会被强制改密；仍在使用默认密码的老账号在登录时也会被识别出来。
 */
@Slf4j
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountInitializer(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long exists = userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getUsername, CommonConstants.DEFAULT_ADMIN_USERNAME));
        if (exists != null && exists > 0) {
            return;
        }
        UserEntity admin = new UserEntity();
        admin.setUsername(CommonConstants.DEFAULT_ADMIN_USERNAME);
        admin.setPassword(passwordEncoder.encode(CommonConstants.DEFAULT_ADMIN_PASSWORD));
        admin.setNickname("超级管理员");
        admin.setRole(CommonConstants.ROLE_ADMIN);
        admin.setStatus(CommonConstants.STATUS_ENABLED);
        // 默认密码是公开的，首次登录必须改掉才能调用其他管理接口
        admin.setMustChangePassword(1);
        userMapper.insert(admin);
        log.warn("已创建默认管理员账号: {}，首次登录将强制修改密码",
                CommonConstants.DEFAULT_ADMIN_USERNAME);
    }
}
