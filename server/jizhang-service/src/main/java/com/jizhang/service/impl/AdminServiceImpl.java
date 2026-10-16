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
import com.jizhang.dao.entity.AccountEntity;
import com.jizhang.dao.entity.BookEntity;
import com.jizhang.dao.entity.CategoryEntity;
import com.jizhang.dao.entity.TransactionEntity;
import com.jizhang.dao.entity.UserEntity;
import com.jizhang.dao.mapper.AccountMapper;
import com.jizhang.dao.mapper.BookMapper;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

    private static final String DEFAULT_CURRENCY = "CNY";

    private final UserMapper userMapper;
    private final BookMapper bookMapper;
    private final AccountMapper accountMapper;
    private final CategoryMapper categoryMapper;
    private final TransactionMapper transactionMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public AdminServiceImpl(UserMapper userMapper,
                            BookMapper bookMapper,
                            AccountMapper accountMapper,
                            CategoryMapper categoryMapper,
                            TransactionMapper transactionMapper,
                            PasswordEncoder passwordEncoder,
                            JwtUtils jwtUtils) {
        this.userMapper = userMapper;
        this.bookMapper = bookMapper;
        this.accountMapper = accountMapper;
        this.categoryMapper = categoryMapper;
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
                // 转账时「这个账户发生的事」也包含它作为转入方的那一条，所以两个字段都要看
                .and(request.getAccountId() != null, w -> w
                        .eq(TransactionEntity::getAccountId, request.getAccountId())
                        .or().eq(TransactionEntity::getToAccountId, request.getAccountId()))
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
        List<TransactionVO> records = page.getRecords().stream().map(TransactionVO::from).toList();
        fillDisplayNames(records);
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
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
