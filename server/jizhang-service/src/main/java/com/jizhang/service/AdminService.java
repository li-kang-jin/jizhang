package com.jizhang.service;

import com.jizhang.common.api.PageResult;
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

import java.util.List;

/**
 * 管理后台服务。所有接口要求登录账号具备 ADMIN 角色。
 */
public interface AdminService {

    /** 管理员登录（非 ADMIN 角色拒绝） */
    TokenVO login(LoginRequest request);

    /** 修改当前登录管理员的密码 */
    void changePassword(ChangePasswordRequest request);

    PageResult<UserVO> pageUsers(AdminUserQueryRequest request);

    /** 启用 / 禁用用户 */
    UserVO updateUserStatus(Long userId, Integer status);

    /**
     * 重置用户密码：服务端生成一次性临时密码。
     * <p>
     * 返回明文只此一次（不落库、不打日志），管理员把它交给用户，用户登录后自行修改。
     * 管理员账号不允许被重置，避免一个管理员接管另一个管理员。
     *
     * @return 临时密码
     */
    String resetUserPassword(Long userId);

    PageResult<BookVO> pageBooks(AdminBookQueryRequest request);

    PageResult<TransactionVO> pageTransactions(AdminTransactionQueryRequest request);

    /** 当前筛选条件下的流水合计（跨分页，转账单列） */
    AdminTransactionSummaryVO sumTransactions(AdminTransactionQueryRequest request);

    /** 全平台账户列表（带所属账本与所有者） */
    PageResult<AdminAccountVO> pageAccounts(AdminAccountQueryRequest request);

    /** 代用户创建账户（bookId 必填且账本需存在） */
    AdminAccountVO createAccount(AccountSaveRequest request);

    /** 修改账户；不允许改动所属账本 */
    AdminAccountVO updateAccount(Long accountId, AccountSaveRequest request);

    /** 删除账户；账户下仍有交易流水时拒绝 */
    void deleteAccount(Long accountId);

    /** 平台运营总览 */
    PlatformOverviewVO overview();

    /**
     * 平台增长趋势。
     *
     * @param days 天数，取值范围 7-90
     */
    List<TrendPointVO> trend(int days);
}
