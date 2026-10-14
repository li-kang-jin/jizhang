package com.jizhang.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jizhang.common.api.ResultCode;
import com.jizhang.common.context.UserContext;
import com.jizhang.common.exception.BizException;
import com.jizhang.dao.entity.BookEntity;
import com.jizhang.dao.entity.BookMemberEntity;
import com.jizhang.dao.entity.UserEntity;
import com.jizhang.dao.mapper.BookMapper;
import com.jizhang.dao.mapper.BookMemberMapper;
import com.jizhang.dao.mapper.UserMapper;
import com.jizhang.service.BookService;
import com.jizhang.service.dto.BookSaveRequest;
import com.jizhang.service.dto.MemberAddRequest;
import com.jizhang.service.support.BookAccessChecker;
import com.jizhang.service.vo.BookMemberVO;
import com.jizhang.service.vo.BookVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class BookServiceImpl implements BookService {

    private static final String DEFAULT_CURRENCY = "CNY";

    private final BookMapper bookMapper;
    private final BookMemberMapper bookMemberMapper;
    private final UserMapper userMapper;
    private final BookAccessChecker bookAccessChecker;

    public BookServiceImpl(BookMapper bookMapper,
                           BookMemberMapper bookMemberMapper,
                           UserMapper userMapper,
                           BookAccessChecker bookAccessChecker) {
        this.bookMapper = bookMapper;
        this.bookMemberMapper = bookMemberMapper;
        this.userMapper = userMapper;
        this.bookAccessChecker = bookAccessChecker;
    }

    @Override
    public List<BookVO> listMyBooks() {
        Long userId = UserContext.requireUserId();

        List<BookEntity> owned = bookMapper.selectList(Wrappers.<BookEntity>lambdaQuery()
                .eq(BookEntity::getOwnerId, userId)
                .orderByDesc(BookEntity::getCreatedAt));

        List<Long> joinedIds = bookMemberMapper.selectList(Wrappers.<BookMemberEntity>lambdaQuery()
                        .eq(BookMemberEntity::getUserId, userId))
                .stream()
                .map(BookMemberEntity::getBookId)
                .toList();

        List<BookEntity> joined = joinedIds.isEmpty() ? List.of()
                : bookMapper.selectList(Wrappers.<BookEntity>lambdaQuery()
                .in(BookEntity::getId, joinedIds)
                .orderByDesc(BookEntity::getCreatedAt));

        List<BookVO> result = new ArrayList<>(owned.size() + joined.size());
        for (BookEntity book : owned) {
            BookVO vo = BookVO.from(book);
            vo.setMyRole(BookAccessChecker.ROLE_OWNER);
            result.add(vo);
        }
        for (BookEntity book : joined) {
            BookVO vo = BookVO.from(book);
            vo.setMyRole(bookAccessChecker.roleOf(book.getId(), userId));
            result.add(vo);
        }
        return result;
    }

    @Override
    public BookVO create(BookSaveRequest request) {
        Long userId = UserContext.requireUserId();
        BookEntity book = new BookEntity();
        book.setName(request.getName());
        book.setIcon(request.getIcon());
        book.setCurrency(StrUtil.blankToDefault(request.getCurrency(), DEFAULT_CURRENCY));
        book.setType(request.getType() == null ? 0 : request.getType());
        book.setOwnerId(userId);
        book.setRemark(request.getRemark());
        book.setVersion(0);
        bookMapper.insert(book);

        BookVO vo = BookVO.from(book);
        vo.setMyRole(BookAccessChecker.ROLE_OWNER);
        return vo;
    }

    @Override
    public BookVO detail(Long bookId) {
        BookEntity book = bookAccessChecker.requireReadable(bookId);
        BookVO vo = BookVO.from(book);
        vo.setMyRole(bookAccessChecker.roleOf(bookId, UserContext.requireUserId()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookVO update(Long bookId, BookSaveRequest request) {
        BookEntity book = bookAccessChecker.requireWritable(bookId);
        book.setName(request.getName());
        book.setIcon(request.getIcon());
        if (StrUtil.isNotBlank(request.getCurrency())) {
            book.setCurrency(request.getCurrency());
        }
        if (request.getType() != null) {
            book.setType(request.getType());
        }
        book.setRemark(request.getRemark());
        bookMapper.updateById(book);
        return BookVO.from(bookMapper.selectById(bookId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long bookId) {
        BookEntity book = bookAccessChecker.requireOwner(bookId);
        // 仅软删除账本本身，账户 / 分类 / 交易数据保留，便于误删恢复
        bookMapper.deleteById(book.getId());
        log.info("账本已删除: id={}, by={}", bookId, UserContext.getUserId());
    }

    @Override
    public List<BookMemberVO> members(Long bookId) {
        BookEntity book = bookAccessChecker.requireReadable(bookId);

        Map<Long, BookMemberVO> memberMap = new LinkedHashMap<>();
        UserEntity owner = userMapper.selectById(book.getOwnerId());
        if (owner != null) {
            memberMap.put(owner.getId(), toMemberVO(owner, BookAccessChecker.ROLE_OWNER, true));
        }

        List<BookMemberEntity> members = bookMemberMapper.selectList(Wrappers.<BookMemberEntity>lambdaQuery()
                .eq(BookMemberEntity::getBookId, bookId));
        if (!members.isEmpty()) {
            List<Long> userIds = members.stream().map(BookMemberEntity::getUserId).toList();
            Map<Long, UserEntity> userMap = new LinkedHashMap<>();
            userMapper.selectByIds(userIds).forEach(user -> userMap.put(user.getId(), user));
            for (BookMemberEntity member : members) {
                UserEntity user = userMap.get(member.getUserId());
                if (user != null) {
                    memberMap.put(user.getId(), toMemberVO(user, member.getRole(), false));
                }
            }
        }
        return new ArrayList<>(memberMap.values());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addMember(Long bookId, MemberAddRequest request) {
        bookAccessChecker.requireOwner(bookId);
        UserEntity user = userMapper.selectById(request.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        Long exists = bookMemberMapper.selectCount(Wrappers.<BookMemberEntity>lambdaQuery()
                .eq(BookMemberEntity::getBookId, bookId)
                .eq(BookMemberEntity::getUserId, request.getUserId()));
        if (exists != null && exists > 0) {
            return;
        }
        BookMemberEntity member = new BookMemberEntity();
        member.setBookId(bookId);
        member.setUserId(request.getUserId());
        member.setRole(request.getRole() == null ? 1 : request.getRole());
        bookMemberMapper.insert(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long bookId, Long userId) {
        BookEntity book = bookAccessChecker.requireOwner(bookId);
        if (Objects.equals(book.getOwnerId(), userId)) {
            throw new BizException(ResultCode.PARAM_ERROR, "不能移除账本所有者");
        }
        bookMemberMapper.delete(Wrappers.<BookMemberEntity>lambdaQuery()
                .eq(BookMemberEntity::getBookId, bookId)
                .eq(BookMemberEntity::getUserId, userId));
    }

    private BookMemberVO toMemberVO(UserEntity user, Integer role, boolean owner) {
        BookMemberVO vo = new BookMemberVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRole(role);
        vo.setOwner(owner);
        return vo;
    }
}
