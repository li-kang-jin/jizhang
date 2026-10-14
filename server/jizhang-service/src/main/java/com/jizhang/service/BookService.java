package com.jizhang.service;

import com.jizhang.service.dto.BookSaveRequest;
import com.jizhang.service.dto.MemberAddRequest;
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

    void removeMember(Long bookId, Long userId);
}
