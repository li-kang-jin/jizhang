package com.jizhang.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jizhang.common.api.ResultCode;
import com.jizhang.common.context.UserContext;
import com.jizhang.common.exception.BizException;
import com.jizhang.dao.entity.BookEntity;
import com.jizhang.dao.entity.BookInviteEntity;
import com.jizhang.dao.entity.BookMemberEntity;
import com.jizhang.dao.entity.UserEntity;
import com.jizhang.dao.mapper.BookInviteMapper;
import com.jizhang.dao.mapper.BookMapper;
import com.jizhang.dao.mapper.BookMemberMapper;
import com.jizhang.dao.mapper.UserMapper;
import com.jizhang.service.BookService;
import com.jizhang.service.dto.BookSaveRequest;
import com.jizhang.service.dto.InviteCreateRequest;
import com.jizhang.service.dto.MemberAddRequest;
import com.jizhang.service.dto.MemberUpdateRequest;
import com.jizhang.service.support.BookAccessChecker;
import com.jizhang.service.support.DefaultDataInitializer;
import com.jizhang.service.vo.BookInviteVO;
import com.jizhang.service.vo.BookMemberVO;
import com.jizhang.service.vo.BookVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class BookServiceImpl implements BookService {

    private static final String DEFAULT_CURRENCY = "CNY";

    /** 个人账本类型：仅自己可见 */
    private static final int PERSONAL_BOOK_TYPE = 0;

    /** 默认成员角色：可写 */
    private static final int ROLE_MEMBER = 1;

    /** 邀请码状态 */
    private static final int INVITE_STATUS_REVOKED = 0;
    private static final int INVITE_STATUS_ACTIVE = 1;

    /** 邀请码字符集：去掉 0/O/1/I 等易混淆字符，方便口头转述 */
    private static final char[] INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int INVITE_CODE_LENGTH = 8;
    private static final int INVITE_CODE_MAX_RETRY = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final BookMapper bookMapper;
    private final BookMemberMapper bookMemberMapper;
    private final BookInviteMapper bookInviteMapper;
    private final UserMapper userMapper;
    private final BookAccessChecker bookAccessChecker;
    private final DefaultDataInitializer defaultDataInitializer;

    public BookServiceImpl(BookMapper bookMapper,
                           BookMemberMapper bookMemberMapper,
                           BookInviteMapper bookInviteMapper,
                           UserMapper userMapper,
                           BookAccessChecker bookAccessChecker,
                           DefaultDataInitializer defaultDataInitializer) {
        this.bookMapper = bookMapper;
        this.bookMemberMapper = bookMemberMapper;
        this.bookInviteMapper = bookInviteMapper;
        this.userMapper = userMapper;
        this.bookAccessChecker = bookAccessChecker;
        this.defaultDataInitializer = defaultDataInitializer;
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
            vo.setPersonal(Objects.equals(book.getType(), PERSONAL_BOOK_TYPE));
            vo.setMemberCount(countMembers(book.getId()));
            result.add(vo);
        }
        for (BookEntity book : joined) {
            BookVO vo = BookVO.from(book);
            vo.setMyRole(bookAccessChecker.roleOf(book.getId(), userId));
            vo.setPersonal(false);
            vo.setMemberCount(countMembers(book.getId()));
            result.add(vo);
        }
        return result;
    }

    /** 成员数 = 所有者 1 人 + t_book_member 记录数 */
    private int countMembers(Long bookId) {
        Long count = bookMemberMapper.selectCount(Wrappers.<BookMemberEntity>lambdaQuery()
                .eq(BookMemberEntity::getBookId, bookId));
        return 1 + (count == null ? 0 : count.intValue());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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

        // 新账本必须自带默认账户与分类，否则记账页「没有账户可选」
        defaultDataInitializer.initForNewBook(book.getId(), userId);

        BookVO vo = BookVO.from(book);
        vo.setMyRole(BookAccessChecker.ROLE_OWNER);
        vo.setPersonal(Objects.equals(book.getType(), PERSONAL_BOOK_TYPE));
        vo.setMemberCount(1);
        return vo;
    }

    @Override
    public BookVO detail(Long bookId) {
        BookEntity book = bookAccessChecker.requireReadable(bookId);
        Long userId = UserContext.requireUserId();
        BookVO vo = BookVO.from(book);
        vo.setMyRole(bookAccessChecker.roleOf(bookId, userId));
        vo.setPersonal(Objects.equals(book.getOwnerId(), userId)
                && Objects.equals(book.getType(), PERSONAL_BOOK_TYPE));
        vo.setMemberCount(countMembers(bookId));
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
            BookMemberVO vo = toMemberVO(owner, BookAccessChecker.ROLE_OWNER, null);
            vo.setOwner(true);
            vo.setJoinedAt(book.getCreatedAt());
            vo.setDisplayName(displayName(null, owner));
            memberMap.put(owner.getId(), vo);
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
                    BookMemberVO vo = toMemberVO(user, member.getRole(), member.getAlias());
                    vo.setOwner(false);
                    vo.setJoinedAt(member.getJoinedAt());
                    vo.setDisplayName(displayName(member.getAlias(), user));
                    memberMap.put(user.getId(), vo);
                }
            }
        }
        return new ArrayList<>(memberMap.values());
    }

    /** 展示名：优先家庭内称呼，其次昵称，最后登录名 */
    private String displayName(String alias, UserEntity user) {
        if (StrUtil.isNotBlank(alias)) {
            return alias;
        }
        if (user != null && StrUtil.isNotBlank(user.getNickname())) {
            return user.getNickname();
        }
        return user == null ? null : user.getUsername();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addMember(Long bookId, MemberAddRequest request) {
        bookAccessChecker.requireOwner(bookId);
        UserEntity user = userMapper.selectById(request.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        BookMemberEntity exists = findMember(bookId, request.getUserId());
        if (exists != null) {
            return;
        }
        BookMemberEntity member = new BookMemberEntity();
        member.setBookId(bookId);
        member.setUserId(request.getUserId());
        member.setRole(request.getRole() == null ? ROLE_MEMBER : request.getRole());
        member.setAlias(StrUtil.trimToNull(request.getAlias()));
        member.setJoinedAt(OffsetDateTime.now(ZoneOffset.UTC));
        bookMemberMapper.insert(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMember(Long bookId, Long userId, MemberUpdateRequest request) {
        BookEntity book = bookAccessChecker.requireOwner(bookId);
        if (Objects.equals(book.getOwnerId(), userId)) {
            // 所有者角色固定，只允许改称呼
            throw new BizException(ResultCode.PARAM_ERROR, "不能修改账本所有者的角色");
        }
        BookMemberEntity member = findMember(bookId, userId);
        if (member == null) {
            throw new BizException(ResultCode.BOOK_MEMBER_NOT_FOUND);
        }
        if (request.getRole() != null) {
            member.setRole(request.getRole());
        }
        if (request.getAlias() != null) {
            member.setAlias(StrUtil.trimToNull(request.getAlias()));
        }
        bookMemberMapper.updateById(member);
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

    @Override
    public List<BookInviteVO> invites(Long bookId) {
        bookAccessChecker.requireOwner(bookId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return bookInviteMapper.selectList(Wrappers.<BookInviteEntity>lambdaQuery()
                        .eq(BookInviteEntity::getBookId, bookId)
                        .eq(BookInviteEntity::getStatus, INVITE_STATUS_ACTIVE)
                        .and(w -> w.isNull(BookInviteEntity::getExpireAt)
                                .or().gt(BookInviteEntity::getExpireAt, now))
                        .orderByDesc(BookInviteEntity::getCreatedAt))
                .stream()
                .map(BookInviteVO::from)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookInviteVO createInvite(Long bookId, InviteCreateRequest request) {
        BookEntity book = bookAccessChecker.requireOwner(bookId);
        // 个人账本是「只有自己」的容器，拉人进来会让 type 与成员数自相矛盾
        if (Objects.equals(book.getType(), PERSONAL_BOOK_TYPE)) {
            throw new BizException(ResultCode.PARAM_ERROR, "个人账本不能邀请成员，请先创建家庭账本");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        BookInviteEntity invite = new BookInviteEntity();
        invite.setBookId(bookId);
        invite.setCode(generateUniqueCode());
        invite.setRole(request.getRole() == null ? ROLE_MEMBER : request.getRole());
        Integer expireDays = request.getExpireDays();
        invite.setExpireAt(expireDays == null || expireDays <= 0 ? null : now.plusDays(expireDays));
        invite.setMaxUses(request.getMaxUses() == null ? 1 : request.getMaxUses());
        invite.setUsedCount(0);
        invite.setStatus(INVITE_STATUS_ACTIVE);
        bookInviteMapper.insert(invite);
        log.info("账本 {} 生成邀请码 {}，by={}", bookId, invite.getCode(), UserContext.getUserId());
        return BookInviteVO.from(invite);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeInvite(Long bookId, String code) {
        bookAccessChecker.requireOwner(bookId);
        BookInviteEntity invite = findInvite(code);
        if (invite == null || !Objects.equals(invite.getBookId(), bookId)) {
            throw new BizException(ResultCode.BOOK_INVITE_INVALID);
        }
        invite.setStatus(INVITE_STATUS_REVOKED);
        bookInviteMapper.updateById(invite);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookVO joinByCode(String code) {
        Long userId = UserContext.requireUserId();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        BookInviteEntity invite = findInvite(code);
        if (invite == null
                || !Objects.equals(invite.getStatus(), INVITE_STATUS_ACTIVE)
                || (invite.getExpireAt() != null && !invite.getExpireAt().isAfter(now))) {
            throw new BizException(ResultCode.BOOK_INVITE_INVALID);
        }
        boolean unlimited = invite.getMaxUses() == null || invite.getMaxUses() <= 0;
        if (!unlimited && invite.getUsedCount() != null && invite.getUsedCount() >= invite.getMaxUses()) {
            throw new BizException(ResultCode.BOOK_INVITE_INVALID);
        }

        Long bookId = invite.getBookId();
        BookEntity book = bookMapper.selectById(bookId);
        if (book == null) {
            throw new BizException(ResultCode.BOOK_NOT_FOUND);
        }

        // 已经是成员（含所有者）时幂等返回，不消耗邀请码次数
        boolean alreadyMember = Objects.equals(book.getOwnerId(), userId) || findMember(bookId, userId) != null;
        if (!alreadyMember) {
            BookMemberEntity member = new BookMemberEntity();
            member.setBookId(bookId);
            member.setUserId(userId);
            member.setRole(invite.getRole() == null ? ROLE_MEMBER : invite.getRole());
            member.setJoinedAt(now);
            bookMemberMapper.insert(member);

            invite.setUsedCount((invite.getUsedCount() == null ? 0 : invite.getUsedCount()) + 1);
            bookInviteMapper.updateById(invite);
            log.info("用户 {} 凭邀请码 {} 加入账本 {}", userId, invite.getCode(), bookId);
        }

        BookVO vo = BookVO.from(book);
        vo.setMyRole(bookAccessChecker.roleOf(bookId, userId));
        vo.setPersonal(false);
        vo.setMemberCount(countMembers(bookId));
        return vo;
    }

    private BookMemberEntity findMember(Long bookId, Long userId) {
        return bookMemberMapper.selectOne(Wrappers.<BookMemberEntity>lambdaQuery()
                .eq(BookMemberEntity::getBookId, bookId)
                .eq(BookMemberEntity::getUserId, userId)
                .last("limit 1"));
    }

    private BookInviteEntity findInvite(String code) {
        String normalized = StrUtil.trimToEmpty(code).toUpperCase();
        if (normalized.isEmpty()) {
            return null;
        }
        return bookInviteMapper.selectOne(Wrappers.<BookInviteEntity>lambdaQuery()
                .eq(BookInviteEntity::getCode, normalized)
                .last("limit 1"));
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < INVITE_CODE_MAX_RETRY; attempt++) {
            String code = randomCode();
            Long exists = bookInviteMapper.selectCount(Wrappers.<BookInviteEntity>lambdaQuery()
                    .eq(BookInviteEntity::getCode, code));
            if (exists == null || exists == 0) {
                return code;
            }
        }
        throw new BizException(ResultCode.ERROR, "邀请码生成失败，请重试");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_ALPHABET[RANDOM.nextInt(INVITE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    private BookMemberVO toMemberVO(UserEntity user, Integer role, String alias) {
        BookMemberVO vo = new BookMemberVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRole(role);
        vo.setAlias(alias);
        return vo;
    }
}
