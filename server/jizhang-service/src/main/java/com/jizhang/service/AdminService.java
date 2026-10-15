package com.jizhang.service;

import com.jizhang.common.api.PageResult;
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

    PageResult<BookVO> pageBooks(AdminBookQueryRequest request);

    PageResult<TransactionVO> pageTransactions(AdminTransactionQueryRequest request);

    /** 平台运营总览 */
    PlatformOverviewVO overview();

    /**
     * 平台增长趋势。
     *
     * @param days 天数，取值范围 7-90
     */
    List<TrendPointVO> trend(int days);
}
