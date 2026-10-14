package com.jizhang.service;

import com.jizhang.service.dto.AccountSaveRequest;
import com.jizhang.service.vo.AccountVO;

import java.util.List;

/**
 * 账户服务。
 */
public interface AccountService {

    List<AccountVO> listByBook(Long bookId);

    AccountVO create(AccountSaveRequest request);

    AccountVO update(Long accountId, AccountSaveRequest request);

    void delete(Long accountId);
}
