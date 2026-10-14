package com.jizhang.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jizhang.common.api.ResultCode;
import com.jizhang.common.exception.BizException;
import com.jizhang.dao.entity.AccountEntity;
import com.jizhang.dao.mapper.AccountMapper;
import com.jizhang.service.AccountService;
import com.jizhang.service.dto.AccountSaveRequest;
import com.jizhang.service.support.BookAccessChecker;
import com.jizhang.service.vo.AccountVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class AccountServiceImpl implements AccountService {

    private static final String DEFAULT_CURRENCY = "CNY";

    private final AccountMapper accountMapper;
    private final BookAccessChecker bookAccessChecker;

    public AccountServiceImpl(AccountMapper accountMapper, BookAccessChecker bookAccessChecker) {
        this.accountMapper = accountMapper;
        this.bookAccessChecker = bookAccessChecker;
    }

    @Override
    public List<AccountVO> listByBook(Long bookId) {
        bookAccessChecker.requireReadable(bookId);
        return accountMapper.selectList(Wrappers.<AccountEntity>lambdaQuery()
                        .eq(AccountEntity::getBookId, bookId)
                        .orderByAsc(AccountEntity::getSort)
                        .orderByAsc(AccountEntity::getId))
                .stream()
                .map(AccountVO::from)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AccountVO create(AccountSaveRequest request) {
        bookAccessChecker.requireWritable(request.getBookId());

        long initialBalance = request.getInitialBalance() == null ? 0L : request.getInitialBalance();
        AccountEntity account = new AccountEntity();
        account.setBookId(request.getBookId());
        account.setName(request.getName());
        account.setType(request.getType());
        // 新建账户的当前余额等于初始余额
        account.setBalance(initialBalance);
        account.setInitialBalance(initialBalance);
        account.setCurrency(StrUtil.blankToDefault(request.getCurrency(), DEFAULT_CURRENCY));
        account.setIcon(request.getIcon());
        account.setSort(request.getSort() == null ? 0 : request.getSort());
        account.setIncludeInTotal(request.getIncludeInTotal() == null ? 1 : request.getIncludeInTotal());
        account.setVersion(0);
        accountMapper.insert(account);
        return AccountVO.from(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AccountVO update(Long accountId, AccountSaveRequest request) {
        AccountEntity account = requireAccount(accountId);
        bookAccessChecker.requireWritable(account.getBookId());

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
        // 说明：balance 由交易流水驱动，此处不直接修改，仅允许调整初始余额
        if (request.getInitialBalance() != null) {
            long delta = request.getInitialBalance()
                    - (account.getInitialBalance() == null ? 0L : account.getInitialBalance());
            account.setInitialBalance(request.getInitialBalance());
            account.setBalance((account.getBalance() == null ? 0L : account.getBalance()) + delta);
        }
        accountMapper.updateById(account);
        return AccountVO.from(accountMapper.selectById(accountId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long accountId) {
        AccountEntity account = requireAccount(accountId);
        bookAccessChecker.requireWritable(account.getBookId());
        accountMapper.deleteById(accountId);
    }

    private AccountEntity requireAccount(Long accountId) {
        AccountEntity account = accountId == null ? null : accountMapper.selectById(accountId);
        if (account == null) {
            throw new BizException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        return account;
    }
}
