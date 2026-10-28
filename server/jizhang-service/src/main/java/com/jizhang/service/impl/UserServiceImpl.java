package com.jizhang.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jizhang.common.api.ResultCode;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.common.context.LoginUser;
import com.jizhang.common.context.UserContext;
import com.jizhang.common.exception.BizException;
import com.jizhang.common.util.JwtUtils;
import com.jizhang.dao.entity.UserEntity;
import com.jizhang.dao.mapper.UserMapper;
import com.jizhang.service.TokenBlacklistService;
import com.jizhang.service.UserService;
import com.jizhang.service.dto.ChangePasswordRequest;
import com.jizhang.service.dto.LoginRequest;
import com.jizhang.service.dto.RegisterRequest;
import com.jizhang.service.support.DefaultDataInitializer;
import com.jizhang.service.vo.TokenVO;
import com.jizhang.service.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;
    private final DefaultDataInitializer defaultDataInitializer;

    public UserServiceImpl(UserMapper userMapper,
                           PasswordEncoder passwordEncoder,
                           JwtUtils jwtUtils,
                           TokenBlacklistService tokenBlacklistService,
                           DefaultDataInitializer defaultDataInitializer) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.tokenBlacklistService = tokenBlacklistService;
        this.defaultDataInitializer = defaultDataInitializer;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO register(RegisterRequest request) {
        Long exists = userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }

        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(StrUtil.isBlank(request.getNickname()) ? request.getUsername() : request.getNickname());
        user.setRole(CommonConstants.ROLE_USER);
        user.setStatus(CommonConstants.STATUS_ENABLED);
        userMapper.insert(user);

        defaultDataInitializer.initForNewUser(user.getId(), user.getNickname());
        log.info("新用户注册成功: id={}, username={}", user.getId(), user.getUsername());
        return UserVO.from(user);
    }

    @Override
    public TokenVO login(LoginRequest request) {
        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getUsername, request.getUsername())
                .last("limit 1"));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (!Objects.equals(user.getStatus(), CommonConstants.STATUS_ENABLED)) {
            throw new BizException(ResultCode.USER_DISABLED);
        }

        UserEntity update = new UserEntity();
        update.setId(user.getId());
        update.setLastLoginAt(OffsetDateTime.now(ZoneOffset.UTC));
        userMapper.updateById(update);

        return buildToken(user);
    }

    @Override
    public TokenVO refresh(String refreshToken) {
        if (!jwtUtils.isRefreshToken(refreshToken)) {
            throw new BizException(ResultCode.TOKEN_INVALID);
        }
        LoginUser loginUser = jwtUtils.parse(refreshToken);
        if (loginUser == null) {
            throw new BizException(ResultCode.TOKEN_INVALID);
        }
        UserEntity user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        if (!Objects.equals(user.getStatus(), CommonConstants.STATUS_ENABLED)) {
            throw new BizException(ResultCode.USER_DISABLED);
        }
        return buildToken(user);
    }

    @Override
    public void logout(String accessToken) {
        long ttl = jwtUtils.getRemainingSeconds(accessToken);
        tokenBlacklistService.blacklist(accessToken, ttl);
    }

    @Override
    public UserVO currentUser() {
        Long userId = UserContext.requireUserId();
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        return UserVO.from(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordRequest request) {
        Long userId = UserContext.requireUserId();
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.PARAM_ERROR, "原密码不正确");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BizException(ResultCode.PARAM_ERROR, "新密码不能与原密码相同");
        }

        UserEntity update = new UserEntity();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        // 自己改过密码，就不需要再提示「密码已被重置」
        update.setMustChangePassword(0);
        userMapper.updateById(update);
        log.info("用户修改密码: userId={}, username={}", userId, user.getUsername());
    }

    private TokenVO buildToken(UserEntity user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setRole(user.getRole());

        TokenVO vo = new TokenVO();
        vo.setAccessToken(jwtUtils.generateAccessToken(loginUser));
        vo.setRefreshToken(jwtUtils.generateRefreshToken(loginUser));
        vo.setExpiresIn(jwtUtils.getAccessExpireSeconds());
        vo.setUser(UserVO.from(user));
        return vo;
    }
}
