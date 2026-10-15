package com.jizhang.service;

import com.jizhang.service.dto.BookSaveRequest;
import com.jizhang.service.dto.InviteCreateRequest;
import com.jizhang.service.dto.MemberAddRequest;
import com.jizhang.service.dto.MemberUpdateRequest;
import com.jizhang.service.vo.BookInviteVO;
import com.jizhang.service.vo.BookMemberVO;
import com.jizhang.service.vo.BookVO;

import java.util.List;

/**
 * 账本服务。
 */
public interface BookService {

    /** 我可访问的全部账本（自建 + 参与） */
    List<BookVO> listMyBooks();

    BookVO create(BookSaveRequest request);

    BookVO detail(Long bookId);

    BookVO update(Long bookId, BookSaveRequest request);

    /** 软删除账本，仅所有者可操作 */
    void delete(Long bookId);

    List<BookMemberVO> members(Long bookId);

    void addMember(Long bookId, MemberAddRequest request);

    /** 修改成员角色 / 家庭内称呼，仅所有者可操作 */
    void updateMember(Long bookId, Long userId, MemberUpdateRequest request);

    void removeMember(Long bookId, Long userId);

    /** 账本的有效邀请码列表，仅所有者可查看 */
    List<BookInviteVO> invites(Long bookId);

    /** 生成邀请码，仅所有者可操作 */
    BookInviteVO createInvite(Long bookId, InviteCreateRequest request);

    /** 作废邀请码，仅所有者可操作 */
    void revokeInvite(Long bookId, String code);

    /** 凭邀请码加入账本，返回加入后的账本 */
    BookVO joinByCode(String code);
}
