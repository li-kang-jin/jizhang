package com.jizhang.service.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jizhang.common.api.ResultCode;
import com.jizhang.common.context.UserContext;
import com.jizhang.common.exception.BizException;
import com.jizhang.dao.entity.BookEntity;
import com.jizhang.dao.entity.BookMemberEntity;
import com.jizhang.dao.mapper.BookMapper;
import com.jizhang.dao.mapper.BookMemberMapper;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 账本访问权限校验。所有涉及账本数据的操作都必须先经过此校验。
 */
@Component
public class BookAccessChecker {

    /** 账本所有者拥有的角色等级 */
    public static final int ROLE_OWNER = 2;

    private final BookMapper bookMapper;
    private final BookMemberMapper bookMemberMapper;

    public BookAccessChecker(BookMapper bookMapper, BookMemberMapper bookMemberMapper) {
        this.bookMapper = bookMapper;
        this.bookMemberMapper = bookMemberMapper;
    }

    /** 校验只读权限，返回账本实体 */
    public BookEntity requireReadable(Long bookId) {
        BookEntity book = loadBook(bookId);
        Long userId = UserContext.requireUserId();
        if (Objects.equals(book.getOwnerId(), userId) || findMember(bookId, userId) != null) {
            return book;
        }
        throw new BizException(ResultCode.BOOK_NO_PERMISSION);
    }

    /** 校验写权限（所有者或角色 >= 可写），返回账本实体 */
    public BookEntity requireWritable(Long bookId) {
        BookEntity book = loadBook(bookId);
        Long userId = UserContext.requireUserId();
        if (Objects.equals(book.getOwnerId(), userId)) {
            return book;
        }
        BookMemberEntity member = findMember(bookId, userId);
        if (member != null && member.getRole() != null && member.getRole() >= 1) {
            return book;
        }
        throw new BizException(ResultCode.BOOK_NO_PERMISSION);
    }

    /** 校验所有者身份，返回账本实体 */
    public BookEntity requireOwner(Long bookId) {
        BookEntity book = loadBook(bookId);
        Long userId = UserContext.requireUserId();
        if (!Objects.equals(book.getOwnerId(), userId)) {
            throw new BizException(ResultCode.BOOK_NO_PERMISSION);
        }
        return book;
    }

    /** 当前用户在账本中的角色，非成员返回 null */
    public Integer roleOf(Long bookId, Long userId) {
        BookEntity book = loadBook(bookId);
        if (Objects.equals(book.getOwnerId(), userId)) {
            return ROLE_OWNER;
        }
        BookMemberEntity member = findMember(bookId, userId);
        return member == null ? null : member.getRole();
    }

    private BookEntity loadBook(Long bookId) {
        if (bookId == null) {
            throw new BizException(ResultCode.BOOK_NOT_FOUND);
        }
        BookEntity book = bookMapper.selectById(bookId);
        if (book == null) {
            throw new BizException(ResultCode.BOOK_NOT_FOUND);
        }
        return book;
    }

    private BookMemberEntity findMember(Long bookId, Long userId) {
        return bookMemberMapper.selectOne(Wrappers.<BookMemberEntity>lambdaQuery()
                .eq(BookMemberEntity::getBookId, bookId)
                .eq(BookMemberEntity::getUserId, userId)
                .last("limit 1"));
    }
}
