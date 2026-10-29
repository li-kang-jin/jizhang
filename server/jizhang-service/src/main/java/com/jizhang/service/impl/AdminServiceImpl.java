package com.jizhang.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.jizhang.dao.entity.AccountEntity;
import com.jizhang.dao.entity.BookEntity;
import com.jizhang.dao.entity.BookMemberEntity;
import com.jizhang.dao.entity.CategoryEntity;
import com.jizhang.dao.entity.TransactionEntity;
import com.jizhang.dao.entity.UserEntity;
import com.jizhang.dao.mapper.AccountMapper;
import com.jizhang.dao.mapper.BookMapper;
import com.jizhang.dao.mapper.BookMemberMapper;
import com.jizhang.dao.mapper.CategoryMapper;
import com.jizhang.dao.mapper.TransactionMapper;
import com.jizhang.dao.mapper.UserMapper;
import com.jizhang.service.AdminService;
import com.jizhang.service.dto.AccountSaveRequest;
import com.jizhang.service.dto.AdminAccountQueryRequest;
import com.jizhang.service.dto.AdminBookQueryRequest;
import com.jizhang.service.dto.AdminTransactionQueryRequest;
import com.jizhang.service.dto.AdminUserQueryRequest;
import com.jizhang.service.dto.ChangePasswordRequest;
import com.jizhang.service.dto.LoginRequest;
import com.jizhang.service.vo.AdminAccountVO;
import com.jizhang.service.vo.AdminTransactionSummaryVO;
import com.jizhang.service.vo.BookVO;
import com.jizhang.service.vo.PlatformOverviewVO;
import com.jizhang.service.vo.TokenVO;
import com.jizhang.service.vo.TransactionVO;
import com.jizhang.service.vo.TrendPointVO;
import com.jizhang.service.vo.UserVO;
import com.jizhang.service.support.LoginAttemptGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class AdminServiceImpl implements AdminService {

    private static final int MIN_TREND_DAYS = 7;
    private static final int MAX_TREND_DAYS = 90;

    private static final int TYPE_EXPENSE = 1;
    private static final int TYPE_INCOME = 2;

    private static final String DEFAULT_CURRENCY = "CNY";

    /** 临时密码字符集：去掉 0/O/1/l/I，避免电话/微信口头传达时听错登不进去 */
    private static final char[] TEMP_PASSWORD_ALPHABET =
            "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private static final int TEMP_PASSWORD_LENGTH = 10;

    private final UserMapper userMapper;
    private final BookMapper bookMapper;
    private final BookMemberMapper bookMemberMapper;
    private final AccountMapper accountMapper;
    private final CategoryMapper categoryMapper;
    private final TransactionMapper transactionMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final LoginAttemptGuard loginAttemptGuard;

    public AdminServiceImpl(UserMapper userMapper,
                            BookMapper bookMapper,
                            BookMemberMapper bookMemberMapper,
                            AccountMapper accountMapper,
                            CategoryMapper categoryMapper,
                            TransactionMapper transactionMapper,
                            PasswordEncoder passwordEncoder,
                            JwtUtils jwtUtils,
                            LoginAttemptGuard loginAttemptGuard) {
        this.userMapper = userMapper;
        this.bookMapper = bookMapper;
        this.bookMemberMapper = bookMemberMapper;
        this.accountMapper = accountMapper;
        this.categoryMapper = categoryMapper;
        this.transactionMapper = transactionMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.loginAttemptGuard = loginAttemptGuard;
    }

    @Override
    public TokenVO login(LoginRequest request) {
        String attemptKey = "admin:" + request.getUsername().trim().toLowerCase(Locale.ROOT);
        // 连续失败 5 次锁 15 分钟，挡掉对公开默认密码的暴力尝试
        loginAttemptGuard.ensureAllowed(attemptKey);

        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getUsername, request.getUsername())
                .last("limit 1"));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginAttemptGuard.recordFailure(attemptKey);
            throw new BizException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (!CommonConstants.ROLE_ADMIN.equals(user.getRole())) {
            loginAttemptGuard.recordFailure(attemptKey);
            throw new BizException(ResultCode.FORBIDDEN, "该账号不具备管理员权限");
        }
        if (!Objects.equals(user.getStatus(), CommonConstants.STATUS_ENABLED)) {
            throw new BizException(ResultCode.USER_DISABLED);
        }
        loginAttemptGuard.reset(attemptKey);

        // 仍在使用公开的初始密码（含升级前就建好的老账号）→ 标记强制改密
        if (CommonConstants.DEFAULT_ADMIN_PASSWORD.equals(request.getPassword())
                && !Objects.equals(user.getMustChangePassword(), 1)) {
            UserEntity flag = new UserEntity();
            flag.setId(user.getId());
            flag.setMustChangePassword(1);
            userMapper.updateById(flag);
            user.setMustChangePassword(1);
            log.warn("管理员仍在使用初始密码，已要求登录后修改: userId={}", user.getId());
        }

        UserEntity update = new UserEntity();
        update.setId(user.getId());
        update.setLastLoginAt(OffsetDateTime.now(ZoneOffset.UTC));
        userMapper.updateById(update);

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setRole(user.getRole());
        loginUser.setTokenVersion(user.getTokenVersion() == null ? 0 : user.getTokenVersion());

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
        if (CommonConstants.DEFAULT_ADMIN_PASSWORD.equals(request.getNewPassword())) {
            throw new BizException(ResultCode.PARAM_ERROR, "新密码不能使用初始密码");
        }
        UserEntity update = new UserEntity();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        update.setMustChangePassword(0);
        userMapper.updateById(update);
        // 改密后旧会话立即失效：否则「改了密码」只是换了个登录凭据，已登录的设备还能继续用
        userMapper.incrementTokenVersion(userId);
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
        if (Objects.equals(status, CommonConstants.STATUS_DISABLED)) {
            // 禁用必须立刻生效：JWT 无状态，不升版本的话已登录设备还能继续用到令牌过期
            userMapper.incrementTokenVersion(userId);
        }
        log.info("管理员修改用户状态: userId={}, status={}", userId, status);
        return UserVO.from(userMapper.selectById(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetUserPassword(Long userId) {
        UserEntity user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        // 管理员之间不能互改密码：一个被攻破的管理员账号不该能顺走其他管理员
        if (CommonConstants.ROLE_ADMIN.equals(user.getRole())) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "不能重置管理员账号的密码，请用该账号登录后自行修改");
        }

        String tempPassword = randomPassword();
        UserEntity update = new UserEntity();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(tempPassword));
        update.setMustChangePassword(1);
        userMapper.updateById(update);
        // 重置密码的核心目的是「把可能被盗的会话踢掉」，所以旧令牌必须立刻失效
        userMapper.incrementTokenVersion(userId);

        // 日志里只留「谁重置了谁」，不记密码：日志经常被转发、归档，比数据库更容易泄漏
        log.info("管理员重置用户密码: userId={}, username={}, operatorId={}",
                userId, user.getUsername(), UserContext.requireUserId());
        return tempPassword;
    }

    /**
     * 临时密码：10 位，去掉 0/O/1/l/I 这类口头传达时容易听错的字符。
     * <p>
     * 用 [SecureRandom] 而不是 `Math.random()`：后者是可预测的伪随机，
     * 而这个密码在用户改密之前就是这套账号的唯一凭据。
     */
    private static String randomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_ALPHABET[random.nextInt(TEMP_PASSWORD_ALPHABET.length)]);
        }
        return sb.toString();
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
        List<BookVO> records = decorateBooks(page.getRecords().stream().map(BookVO::from).toList());
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PageResult<TransactionVO> pageTransactions(AdminTransactionQueryRequest request) {
        List<Long> ownerBookIds = ownerBookIds(request.getUserId());
        if (ownerBookIds != null && ownerBookIds.isEmpty()) {
            return PageResult.empty(request.getPage(), request.getSize());
        }

        QueryWrapper<TransactionEntity> wrapper = buildTransactionFilter(request, ownerBookIds)
                .orderByDesc("trade_time");

        Page<TransactionEntity> page = transactionMapper.selectPage(
                new Page<>(request.getPage(), request.getSize()), wrapper);
        List<TransactionVO> records = page.getRecords().stream().map(TransactionVO::from).toList();
        fillDisplayNames(records);
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public AdminTransactionSummaryVO sumTransactions(AdminTransactionQueryRequest request) {
        AdminTransactionSummaryVO vo = new AdminTransactionSummaryVO();
        List<Long> ownerBookIds = ownerBookIds(request.getUserId());
        if (ownerBookIds != null && ownerBookIds.isEmpty()) {
            return vo;
        }

        // 用聚合而不是把结果集捞回内存：全平台流水不设上限，翻到哪一页都要能算全量合计
        QueryWrapper<TransactionEntity> wrapper = buildTransactionFilter(request, ownerBookIds)
                .select("COALESCE(sum(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) AS expense",
                        "COALESCE(sum(CASE WHEN type = 2 THEN amount ELSE 0 END), 0) AS income",
                        "COALESCE(sum(CASE WHEN type = 3 THEN amount ELSE 0 END), 0) AS transfer",
                        "count(*) AS tx_count");
        List<Map<String, Object>> rows = transactionMapper.selectMaps(wrapper);
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            vo.setExpense(asLong(row.get("expense")));
            vo.setIncome(asLong(row.get("income")));
            vo.setTransfer(asLong(row.get("transfer")));
            vo.setCount(asLong(row.get("tx_count")));
        }
        return vo;
    }

    /**
     * 按「账本所有者」反查账本 ID。
     *
     * @return null 表示未按用户过滤；空列表表示该用户没有账本，调用方应直接返回空结果
     */
    private List<Long> ownerBookIds(Long userId) {
        if (userId == null) {
            return null;
        }
        return bookMapper.selectList(Wrappers.<BookEntity>lambdaQuery()
                        .eq(BookEntity::getOwnerId, userId))
                .stream()
                .map(BookEntity::getId)
                .toList();
    }

    /** 列表与合计共用同一套筛选条件，避免两个接口口径不一致 */
    private QueryWrapper<TransactionEntity> buildTransactionFilter(AdminTransactionQueryRequest request,
                                                                    List<Long> ownerBookIds) {
        QueryWrapper<TransactionEntity> wrapper = Wrappers.<TransactionEntity>query()
                .eq(request.getBookId() != null, "book_id", request.getBookId())
                .eq(request.getType() != null, "type", request.getType())
                .ge(request.getStartTime() != null, "trade_time", request.getStartTime())
                .le(request.getEndTime() != null, "trade_time", request.getEndTime())
                .like(StrUtil.isNotBlank(request.getKeyword()), "remark", request.getKeyword());

        // 转账时「这个账户发生的事」也包含它作为转入方的那一条，所以两个字段都要看
        if (request.getAccountId() != null) {
            wrapper.and(w -> w.eq("account_id", request.getAccountId())
                    .or().eq("to_account_id", request.getAccountId()));
        }
        if (ownerBookIds != null) {
            wrapper.in("book_id", ownerBookIds);
        }
        return wrapper;
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /** 批量补齐账本的所有者、成员数与账户数，避免列表里只看得到一串 ID */
    private List<BookVO> decorateBooks(List<BookVO> records) {
        if (records.isEmpty()) {
            return records;
        }

        Set<Long> bookIds = records.stream().map(BookVO::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> ownerIds = records.stream().map(BookVO::getOwnerId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> owners = ownerIds.isEmpty()
                ? Collections.emptyMap()
                : toNameMap(userMapper.selectBatchIds(ownerIds), UserEntity::getId, this::displayName);

        Map<Long, Integer> accountCounts = new HashMap<>();
        accountMapper.selectList(Wrappers.<AccountEntity>lambdaQuery()
                        .in(AccountEntity::getBookId, bookIds)
                        .select(AccountEntity::getBookId))
                .forEach(account -> accountCounts.merge(account.getBookId(), 1, Integer::sum));

        Map<Long, Integer> memberCounts = new HashMap<>();
        bookMemberMapper.selectList(Wrappers.<BookMemberEntity>lambdaQuery()
                        .in(BookMemberEntity::getBookId, bookIds)
                        .select(BookMemberEntity::getBookId))
                .forEach(member -> memberCounts.merge(member.getBookId(), 1, Integer::sum));

        for (BookVO vo : records) {
            vo.setOwnerUsername(owners.get(vo.getOwnerId()));
            vo.setAccountCount(accountCounts.getOrDefault(vo.getId(), 0));
            // 与 App 端口径一致：成员数 = 所有者 1 人 + t_book_member 记录数
            vo.setMemberCount(1 + memberCounts.getOrDefault(vo.getId(), 0));
        }
        return records;
    }

    @Override
    public PageResult<AdminAccountVO> pageAccounts(AdminAccountQueryRequest request) {
        LambdaQueryWrapper<AccountEntity> wrapper = Wrappers.<AccountEntity>lambdaQuery()
                .eq(request.getBookId() != null, AccountEntity::getBookId, request.getBookId())
                .eq(request.getType() != null, AccountEntity::getType, request.getType())
                .like(StrUtil.isNotBlank(request.getKeyword()), AccountEntity::getName, request.getKeyword())
                .orderByDesc(AccountEntity::getCreatedAt)
                .orderByDesc(AccountEntity::getId);

        if (request.getUserId() != null) {
            List<Long> bookIds = bookMapper.selectList(Wrappers.<BookEntity>lambdaQuery()
                            .eq(BookEntity::getOwnerId, request.getUserId()))
                    .stream()
                    .map(BookEntity::getId)
                    .toList();
            if (bookIds.isEmpty()) {
                return PageResult.empty(request.getPage(), request.getSize());
            }
            wrapper.in(AccountEntity::getBookId, bookIds);
        }

        Page<AccountEntity> page = accountMapper.selectPage(
                new Page<>(request.getPage(), request.getSize()), wrapper);
        List<AdminAccountVO> records = decorateAccounts(page.getRecords().stream()
                .map(AdminAccountVO::from)
                .toList());
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminAccountVO createAccount(AccountSaveRequest request) {
        BookEntity book = request.getBookId() == null ? null : bookMapper.selectById(request.getBookId());
        if (book == null) {
            throw new BizException(ResultCode.BOOK_NOT_FOUND);
        }

        long initialBalance = request.getInitialBalance() == null ? 0L : request.getInitialBalance();
        AccountEntity account = new AccountEntity();
        account.setBookId(request.getBookId());
        account.setName(request.getName());
        account.setType(request.getType());
        // 新建账户的当前余额等于初始余额（与 App 端口径一致）
        account.setBalance(initialBalance);
        account.setInitialBalance(initialBalance);
        account.setCurrency(StrUtil.blankToDefault(request.getCurrency(), DEFAULT_CURRENCY));
        account.setIcon(request.getIcon());
        account.setSort(request.getSort() == null ? 0 : request.getSort());
        account.setIncludeInTotal(request.getIncludeInTotal() == null ? 1 : request.getIncludeInTotal());
        account.setVersion(0);
        accountMapper.insert(account);

        log.info("管理员创建账户: bookId={}, accountId={}, name={}", account.getBookId(), account.getId(),
                account.getName());
        return decorateAccount(AdminAccountVO.from(account));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminAccountVO updateAccount(Long accountId, AccountSaveRequest request) {
        AccountEntity account = requireAccount(accountId);

        account.setName(request.getName());
        account.setType(request.getType());
        account.setCurrency(StrUtil.blankToDefault(request.getCurrency(), account.getCurrency()));
        account.setIcon(request.getIcon());
        if (request.getSort() != null) {
            account.setSort(request.getSort());
        }
        if (request.getIncludeInTotal() != null) {
            account.setIncludeInTotal(request.getIncludeInTotal());
        }
        // 说明：balance 由交易流水驱动，此处不直接修改，仅允许调整初始余额（改动额同步到当前余额）
        if (request.getInitialBalance() != null) {
            long delta = request.getInitialBalance()
                    - (account.getInitialBalance() == null ? 0L : account.getInitialBalance());
            account.setInitialBalance(request.getInitialBalance());
            account.setBalance((account.getBalance() == null ? 0L : account.getBalance()) + delta);
        }
        // 所属账本不允许通过后台改动：账户一旦挂到别的账本，历史流水就跨账本了
        accountMapper.updateById(account);

        log.info("管理员修改账户: accountId={}, name={}", accountId, account.getName());
        return decorateAccount(AdminAccountVO.from(accountMapper.selectById(accountId)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long accountId) {
        AccountEntity account = requireAccount(accountId);

        // 与「分类有交易引用时拒绝删除」同一套口径：留着流水指向不存在的账户，报表和同步都会看不懂
        Long used = transactionMapper.selectCount(Wrappers.<TransactionEntity>lambdaQuery()
                .and(w -> w.eq(TransactionEntity::getAccountId, accountId)
                        .or().eq(TransactionEntity::getToAccountId, accountId)));
        if (used != null && used > 0) {
            throw new BizException(ResultCode.ACCOUNT_IN_USE, "该账户下还有 " + used + " 笔流水，请先处理流水后再删除");
        }
        accountMapper.deleteById(accountId);
        log.info("管理员删除账户: accountId={}, name={}", accountId, account.getName());
    }

    /** 批量补齐账户的所属账本名与所有者（避免逐行查库） */
    private List<AdminAccountVO> decorateAccounts(List<AdminAccountVO> result) {
        if (result.isEmpty()) {
            return result;
        }

        Set<Long> bookIds = result.stream().map(AdminAccountVO::getBookId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, BookEntity> books = bookIds.isEmpty()
                ? Collections.emptyMap()
                : bookMapper.selectBatchIds(bookIds).stream()
                        .collect(Collectors.toMap(BookEntity::getId, Function.identity(), (a, b) -> a));

        Set<Long> ownerIds = books.values().stream().map(BookEntity::getOwnerId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> owners = ownerIds.isEmpty()
                ? Collections.emptyMap()
                : toNameMap(userMapper.selectBatchIds(ownerIds), UserEntity::getId, this::displayName);

        for (AdminAccountVO vo : result) {
            BookEntity book = vo.getBookId() == null ? null : books.get(vo.getBookId());
            if (book == null) {
                continue;
            }
            vo.setBookName(book.getName());
            vo.setOwnerId(book.getOwnerId());
            vo.setOwnerUsername(owners.get(book.getOwnerId()));
        }
        return result;
    }

    private AdminAccountVO decorateAccount(AdminAccountVO vo) {
        if (vo == null) {
            return null;
        }
        return decorateAccounts(List.of(vo)).get(0);
    }

    private String displayName(UserEntity user) {
        return StrUtil.isBlank(user.getNickname()) ? user.getUsername() : user.getNickname();
    }

    /**
     * id -> 名称映射。
     * <p>
     * 特意不用 `Collectors.toMap`：那个实现对 null 值直接抛 NPE（名称列为空是允许的），
     * 用普通 HashMap 顺手把脏数据跳过即可。
     */
    private static <T> Map<Long, String> toNameMap(List<T> list,
                                                   Function<T, Long> idGetter,
                                                   Function<T, String> nameGetter) {
        Map<Long, String> map = new HashMap<>();
        for (T item : list) {
            Long id = idGetter.apply(item);
            String name = nameGetter.apply(item);
            if (id != null && name != null) {
                map.put(id, name);
            }
        }
        return map;
    }

    /**
     * 给交易流水补上账本 / 账户 / 分类名称。
     * <p>
     * 后台列表原本只有一串 ID，管理员看不出「这是谁的哪张卡、什么分类」，所以统一在这里批量翻译。
     * 名称缺失（记录已被删除）时保持为空，由前端回落到展示 ID。
     */
    private void fillDisplayNames(List<TransactionVO> records) {
        if (records.isEmpty()) {
            return;
        }

        Set<Long> bookIds = records.stream().map(TransactionVO::getBookId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> accountIds = records.stream()
                .flatMap(vo -> Stream.of(vo.getAccountId(), vo.getToAccountId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> categoryIds = records.stream().map(TransactionVO::getCategoryId).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 注意：这里不能用 Map.of() 当空值——它的 get(null) 会抛 NPE，
        // 而流水里 toAccountId / categoryId 本来就可能为空（支出一类没有转入账户）
        Map<Long, String> bookNames = bookIds.isEmpty()
                ? Collections.emptyMap()
                : toNameMap(bookMapper.selectBatchIds(bookIds), BookEntity::getId, BookEntity::getName);
        Map<Long, String> accountNames = accountIds.isEmpty()
                ? Collections.emptyMap()
                : toNameMap(accountMapper.selectBatchIds(accountIds), AccountEntity::getId, AccountEntity::getName);
        Map<Long, String> categoryNames = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : toNameMap(categoryMapper.selectBatchIds(categoryIds), CategoryEntity::getId, CategoryEntity::getName);

        for (TransactionVO vo : records) {
            vo.setBookName(bookNames.get(vo.getBookId()));
            vo.setAccountName(accountNames.get(vo.getAccountId()));
            vo.setToAccountName(accountNames.get(vo.getToAccountId()));
            vo.setCategoryName(categoryNames.get(vo.getCategoryId()));
        }
    }

    private AccountEntity requireAccount(Long accountId) {
        AccountEntity account = accountId == null ? null : accountMapper.selectById(accountId);
        if (account == null) {
            throw new BizException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        return account;
    }

    @Override
    public PlatformOverviewVO overview() {
        OffsetDateTime todayStart = todayStart();
        PlatformOverviewVO vo = new PlatformOverviewVO();
        vo.setUserTotal(userMapper.countAll());
        vo.setUserToday(userMapper.countSince(todayStart));
        vo.setBookTotal(bookMapper.countAll());
        vo.setActiveBookToday(transactionMapper.countActiveBooksSince(todayStart));
        vo.setTransactionTotal(transactionMapper.countAll());
        vo.setTransactionToday(transactionMapper.countSince(todayStart));
        // 收支分开统计，且不含转账：以前的 sum(amount) 会把支出、收入和转账加在一起，数字没有业务含义
        vo.setExpenseTotal(transactionMapper.sumAmountByType(TYPE_EXPENSE));
        vo.setIncomeTotal(transactionMapper.sumAmountByType(TYPE_INCOME));
        vo.setExpenseToday(transactionMapper.sumAmountByTypeSince(TYPE_EXPENSE, todayStart));
        vo.setIncomeToday(transactionMapper.sumAmountByTypeSince(TYPE_INCOME, todayStart));
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
            point.setExpense(txRow == null || txRow.getExpense() == null ? 0L : txRow.getExpense());
            point.setIncome(txRow == null || txRow.getIncome() == null ? 0L : txRow.getIncome());
            result.add(point);
        }
        return result;
    }

    private OffsetDateTime todayStart() {
        return LocalDate.now(ZoneOffset.UTC).atStartOfDay().atOffset(ZoneOffset.UTC);
    }
}
