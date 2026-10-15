package com.jizhang.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jizhang.common.api.PageResult;
import com.jizhang.common.api.ResultCode;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.common.context.LoginUser;
import com.jizhang.common.context.UserContext;
import com.jizhang.common.exception.BizException;
import com.jizhang.common.util.JwtUtils;
import com.jizhang.dao.dto.DailyStatRow;
import com.jizhang.dao.entity.BookEntity;
import com.jizhang.dao.entity.TransactionEntity;
import com.jizhang.dao.entity.UserEntity;
import com.jizhang.dao.mapper.BookMapper;
import com.jizhang.dao.mapper.TransactionMapper;
import com.jizhang.dao.mapper.UserMapper;
import com.jizhang.service.AdminService;
import com.jizhang.service.dto.AdminBookQueryRequest;
import com.jizhang.service.dto.AdminTransactionQueryRequest;
import com.jizhang.service.dto.AdminUserQueryRequest;
import com.jizhang.service.dto.ChangePasswordRequest;
import com.jizhang.service.dto.LoginRequest;
import com.jizhang.service.vo.BookVO;
import com.jizhang.service.vo.PlatformOverviewVO;
import com.jizhang.service.vo.TokenVO;
import com.jizhang.service.vo.TransactionVO;
import com.jizhang.service.vo.TrendPointVO;
import com.jizhang.service.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AdminServiceImpl implements AdminService {

    private static final int MIN_TREND_DAYS = 7;
    private static final int MAX_TREND_DAYS = 90;

    private final UserMapper userMapper;
    private final BookMapper bookMapper;
    private final TransactionMapper transactionMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public AdminServiceImpl(UserMapper userMapper,
                            BookMapper bookMapper,
                            TransactionMapper transactionMapper,
                            PasswordEncoder passwordEncoder,
                            JwtUtils jwtUtils) {
        this.userMapper = userMapper;
        this.bookMapper = bookMapper;
        this.transactionMapper = transactionMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    @Override
    public TokenVO login(LoginRequest request) {
        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getUsername, request.getUsername())
                .last("limit 1"));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (!CommonConstants.ROLE_ADMIN.equals(user.getRole())) {
            throw new BizException(ResultCode.FORBIDDEN, "该账号不具备管理员权限");
        }
        if (!Objects.equals(user.getStatus(), CommonConstants.STATUS_ENABLED)) {
            throw new BizException(ResultCode.USER_DISABLED);
        }

        UserEntity update = new UserEntity();
        update.setId(user.getId());
        update.setLastLoginAt(OffsetDateTime.now(ZoneOffset.UTC));
        userMapper.updateById(update);

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
        userMapper.updateById(update);
        log.info("管理员修改密码成功: userId={}, username={}", userId, user.getUsername());
    }

    @Override
    public PageResult<UserVO> pageUsers(AdminUserQueryRequest request) {
        Page<UserEntity> page = userMapper.selectPage(
                new Page<>(request.getPage(), request.getSize()),
                Wrappers.<UserEntity>lambdaQuery()
                        .eq(request.getStatus() != null, UserEntity::getStatus, request.getStatus())
                        .eq(StrUtil.isNotBlank(request.getRole()), UserEntity::getRole, request.getRole())
                        .and(StrUtil.isNotBlank(request.getKeyword()), wrapper -> wrapper
                                .like(UserEntity::getUsername, request.getKeyword())
                                .or().like(UserEntity::getNickname, request.getKeyword())
                                .or().like(UserEntity::getPhone, request.getKeyword()))
                        .orderByDesc(UserEntity::getCreatedAt));
        return PageResult.of(page.getRecords().stream().map(UserVO::from).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateUserStatus(Long userId, Integer status) {
        UserEntity user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        if (CommonConstants.ROLE_ADMIN.equals(user.getRole())) {
            throw new BizException(ResultCode.PARAM_ERROR, "不能修改管理员账号状态");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        log.info("管理员修改用户状态: userId={}, status={}", userId, status);
        return UserVO.from(userMapper.selectById(userId));
    }

    @Override
    public PageResult<BookVO> pageBooks(AdminBookQueryRequest request) {
        Page<BookEntity> page = bookMapper.selectPage(
                new Page<>(request.getPage(), request.getSize()),
                Wrappers.<BookEntity>lambdaQuery()
                        .eq(request.getOwnerId() != null, BookEntity::getOwnerId, request.getOwnerId())
                        .eq(request.getType() != null, BookEntity::getType, request.getType())
                        .and(StrUtil.isNotBlank(request.getKeyword()), wrapper -> wrapper
                                .like(BookEntity::getName, request.getKeyword())
                                .or().like(BookEntity::getRemark, request.getKeyword()))
                        .orderByDesc(BookEntity::getCreatedAt));
        return PageResult.of(page.getRecords().stream().map(BookVO::from).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PageResult<TransactionVO> pageTransactions(AdminTransactionQueryRequest request) {
        LambdaQueryWrapper<TransactionEntity> wrapper = Wrappers.<TransactionEntity>lambdaQuery()
                .eq(request.getBookId() != null, TransactionEntity::getBookId, request.getBookId())
                .eq(request.getType() != null, TransactionEntity::getType, request.getType())
                .ge(request.getStartTime() != null, TransactionEntity::getTradeTime, request.getStartTime())
                .le(request.getEndTime() != null, TransactionEntity::getTradeTime, request.getEndTime())
                .like(StrUtil.isNotBlank(request.getKeyword()), TransactionEntity::getRemark, request.getKeyword())
                .orderByDesc(TransactionEntity::getTradeTime);

        if (request.getUserId() != null) {
            List<Long> bookIds = bookMapper.selectList(Wrappers.<BookEntity>lambdaQuery()
                            .eq(BookEntity::getOwnerId, request.getUserId()))
                    .stream()
                    .map(BookEntity::getId)
                    .toList();
            if (bookIds.isEmpty()) {
                return PageResult.empty(request.getPage(), request.getSize());
            }
            wrapper.in(TransactionEntity::getBookId, bookIds);
        }

        Page<TransactionEntity> page = transactionMapper.selectPage(
                new Page<>(request.getPage(), request.getSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(TransactionVO::from).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PlatformOverviewVO overview() {
        OffsetDateTime todayStart = todayStart();
        PlatformOverviewVO vo = new PlatformOverviewVO();
        vo.setUserTotal(userMapper.countAll());
        vo.setUserToday(userMapper.countSince(todayStart));
        vo.setBookTotal(bookMapper.countAll());
        vo.setTransactionTotal(transactionMapper.countAll());
        vo.setTransactionToday(transactionMapper.countSince(todayStart));
        vo.setAmountTotal(transactionMapper.sumAmount());
        vo.setAmountToday(transactionMapper.sumAmountSince(todayStart));
        return vo;
    }

    @Override
    public List<TrendPointVO> trend(int days) {
        int span = Math.min(Math.max(days, MIN_TREND_DAYS), MAX_TREND_DAYS);
        LocalDate startDate = LocalDate.now(ZoneOffset.UTC).minusDays(span - 1L);
        OffsetDateTime start = startDate.atStartOfDay().atOffset(ZoneOffset.UTC);

        Map<String, DailyStatRow> userRows = userMapper.dailyNewUsers(start).stream()
                .collect(Collectors.toMap(DailyStatRow::getDay, Function.identity(), (a, b) -> a));
        Map<String, DailyStatRow> txRows = transactionMapper.dailyTransactions(start).stream()
                .collect(Collectors.toMap(DailyStatRow::getDay, Function.identity(), (a, b) -> a));

        List<TrendPointVO> result = new ArrayList<>(span);
        for (int i = 0; i < span; i++) {
            String day = startDate.plusDays(i).toString();
            DailyStatRow userRow = userRows.get(day);
            DailyStatRow txRow = txRows.get(day);

            TrendPointVO point = new TrendPointVO();
            point.setDate(day);
            point.setNewUsers(userRow == null || userRow.getCnt() == null ? 0L : userRow.getCnt());
            point.setNewTransactions(txRow == null || txRow.getCnt() == null ? 0L : txRow.getCnt());
            point.setAmount(txRow == null || txRow.getAmount() == null ? 0L : txRow.getAmount());
            result.add(point);
        }
        return result;
    }

    private OffsetDateTime todayStart() {
        return LocalDate.now(ZoneOffset.UTC).atStartOfDay().atOffset(ZoneOffset.UTC);
    }
}
