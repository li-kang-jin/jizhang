package com.jizhang.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jizhang.common.context.UserContext;
import com.jizhang.dao.entity.AccountEntity;
import com.jizhang.dao.entity.BaseEntity;
import com.jizhang.dao.entity.BookEntity;
import com.jizhang.dao.entity.CategoryEntity;
import com.jizhang.dao.entity.SyncLogEntity;
import com.jizhang.dao.entity.TransactionEntity;
import com.jizhang.dao.mapper.AccountMapper;
import com.jizhang.dao.mapper.BookMapper;
import com.jizhang.dao.mapper.CategoryMapper;
import com.jizhang.dao.mapper.SyncLogMapper;
import com.jizhang.dao.mapper.TransactionMapper;
import com.jizhang.service.SyncService;
import com.jizhang.service.TransactionService;
import com.jizhang.service.dto.SyncPullRequest;
import com.jizhang.service.dto.SyncPushRequest;
import com.jizhang.service.dto.TransactionSaveRequest;
import com.jizhang.service.support.BookAccessChecker;
import com.jizhang.service.vo.AccountVO;
import com.jizhang.service.vo.BookVO;
import com.jizhang.service.vo.CategoryVO;
import com.jizhang.service.vo.SyncPullVO;
import com.jizhang.service.vo.SyncPushVO;
import com.jizhang.service.vo.TransactionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class SyncServiceImpl implements SyncService {

    /** 单次拉取的交易条数上限，超出时客户端用返回的 nextCursor 继续拉取 */
    private static final int MAX_PULL_SIZE = 500;

    /**
     * 游标重叠窗口（秒）。
     * <p>
     * 客户端上报的游标会回退这么多秒再查询。多拉一点数据是幂等的（upsert），
     * 但能规避「事务提交时间晚于 updated_at 赋值时间」造成的数据不可见问题。
     */
    private static final long OVERLAP_SECONDS = 5;

    /** 全量同步起始时间 */
    private static final OffsetDateTime EPOCH =
            OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private final BookMapper bookMapper;
    private final AccountMapper accountMapper;
    private final CategoryMapper categoryMapper;
    private final TransactionMapper transactionMapper;
    private final SyncLogMapper syncLogMapper;
    private final BookAccessChecker bookAccessChecker;
    private final TransactionService transactionService;

    public SyncServiceImpl(BookMapper bookMapper,
                           AccountMapper accountMapper,
                           CategoryMapper categoryMapper,
                           TransactionMapper transactionMapper,
                           SyncLogMapper syncLogMapper,
                           BookAccessChecker bookAccessChecker,
                           TransactionService transactionService) {
        this.bookMapper = bookMapper;
        this.accountMapper = accountMapper;
        this.categoryMapper = categoryMapper;
        this.transactionMapper = transactionMapper;
        this.syncLogMapper = syncLogMapper;
        this.bookAccessChecker = bookAccessChecker;
        this.transactionService = transactionService;
    }

    @Override
    public SyncPullVO pull(SyncPullRequest request) {
        bookAccessChecker.requireReadable(request.getBookId());

        Long userId = UserContext.requireUserId();
        Long bookId = request.getBookId();
        OffsetDateTime since = request.getLastSyncAt() == null
                ? EPOCH
                : request.getLastSyncAt().minusSeconds(OVERLAP_SECONDS);

        SyncPullVO vo = new SyncPullVO();
        vo.setServerTime(OffsetDateTime.now(ZoneOffset.UTC));

        List<BookEntity> books = bookMapper.selectList(Wrappers.<BookEntity>lambdaQuery()
                .eq(BookEntity::getOwnerId, userId)
                .gt(BookEntity::getUpdatedAt, since));
        List<AccountEntity> accounts = accountMapper.selectList(Wrappers.<AccountEntity>lambdaQuery()
                .eq(AccountEntity::getBookId, bookId)
                .gt(AccountEntity::getUpdatedAt, since));
        List<CategoryEntity> categories = categoryMapper.selectList(Wrappers.<CategoryEntity>lambdaQuery()
                .eq(CategoryEntity::getBookId, bookId)
                .gt(CategoryEntity::getUpdatedAt, since));

        List<TransactionEntity> transactions = transactionMapper.selectList(
                Wrappers.<TransactionEntity>lambdaQuery()
                        .eq(TransactionEntity::getBookId, bookId)
                        .gt(TransactionEntity::getUpdatedAt, since)
                        .orderByAsc(TransactionEntity::getUpdatedAt)
                        .last("limit " + (MAX_PULL_SIZE + 1)));

        boolean hasMore = transactions.size() > MAX_PULL_SIZE;
        if (hasMore) {
            transactions = new ArrayList<>(transactions.subList(0, MAX_PULL_SIZE));
        }

        vo.setBooks(books.stream().map(BookVO::from).toList());
        vo.setAccounts(accounts.stream().map(AccountVO::from).toList());
        vo.setCategories(categories.stream().map(CategoryVO::from).toList());
        vo.setTransactions(transactions.stream().map(TransactionVO::from).toList());
        vo.setHasMore(hasMore);

        vo.setDeletedBookIds(bookMapper.selectDeletedIdsSince(userId, since));
        vo.setDeletedAccountIds(accountMapper.selectDeletedIdsSince(bookId, since));
        vo.setDeletedCategoryIds(categoryMapper.selectDeletedIdsSince(bookId, since));
        vo.setDeletedTransactionIds(transactionMapper.selectDeletedIdsSince(bookId, since));

        vo.setNextCursor(resolveNextCursor(vo, books, accounts, categories, transactions, hasMore));

        saveSyncCursor(userId, request.getDeviceId(), vo.getServerTime());
        log.debug("同步拉取: user={}, device={}, book={}, since={}, tx={}, hasMore={}",
                userId, request.getDeviceId(), bookId, since, transactions.size(), hasMore);
        return vo;
    }

    /**
     * 计算下一次拉取游标。
     * <p>
     * 交易被截断时必须停在最后一笔交易上，否则超出的记录会永久漏掉。
     */
    private OffsetDateTime resolveNextCursor(SyncPullVO vo,
                                              List<BookEntity> books,
                                              List<AccountEntity> accounts,
                                              List<CategoryEntity> categories,
                                              List<TransactionEntity> transactions,
                                              boolean hasMore) {
        if (hasMore && !transactions.isEmpty()) {
            OffsetDateTime last = transactions.get(transactions.size() - 1).getUpdatedAt();
            if (last != null) {
                return last;
            }
        }

        OffsetDateTime cursor = null;
        cursor = maxUpdatedAt(cursor, books);
        cursor = maxUpdatedAt(cursor, accounts);
        cursor = maxUpdatedAt(cursor, categories);
        cursor = maxUpdatedAt(cursor, transactions);
        return cursor == null ? vo.getServerTime() : cursor;
    }

    private OffsetDateTime maxUpdatedAt(OffsetDateTime current, List<? extends BaseEntity> entities) {
        OffsetDateTime max = current;
        for (BaseEntity entity : entities) {
            OffsetDateTime updatedAt = entity.getUpdatedAt();
            if (updatedAt != null && (max == null || updatedAt.isAfter(max))) {
                max = updatedAt;
            }
        }
        return max;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SyncPushVO push(SyncPushRequest request) {
        bookAccessChecker.requireWritable(request.getBookId());

        // 先处理删除：避免「先新增再删除」时幂等判断出现歧义
        List<Long> deletedIds = request.getDeletedTransactionIds();
        if (CollUtil.isNotEmpty(deletedIds)) {
            transactionService.batchDelete(deletedIds);
            log.debug("同步删除: book={}, 共 {} 条", request.getBookId(), deletedIds.size());
        }

        List<Long> successIds = new ArrayList<>();
        List<TransactionVO> conflicts = new ArrayList<>();

        for (TransactionSaveRequest item : request.getTransactions()) {
            // 强制归属到当前账本，避免客户端传错
            item.setBookId(request.getBookId());

            if (item.getId() == null) {
                // 没有本地 ID 的记录直接新建，服务端生成 ID，客户端下次拉取时获得
                transactionService.create(item);
                continue;
            }

            TransactionEntity existing = transactionMapper.selectById(item.getId());
            if (existing == null) {
                transactionService.create(item);
                successIds.add(item.getId());
                continue;
            }

            OffsetDateTime clientUpdatedAt = item.getUpdatedAt();
            if (clientUpdatedAt != null && existing.getUpdatedAt() != null
                    && clientUpdatedAt.isBefore(existing.getUpdatedAt())) {
                // 服务端版本更新，客户端应以服务端版本覆盖本地
                conflicts.add(TransactionVO.from(existing));
            } else {
                transactionService.update(existing.getId(), item);
                successIds.add(item.getId());
            }
        }

        SyncPushVO vo = new SyncPushVO();
        vo.setSuccessIds(successIds);
        vo.setConflicts(conflicts);
        vo.setServerTime(OffsetDateTime.now(ZoneOffset.UTC));
        log.debug("同步推送: book={}, 删除 {} 条, 写入 {} 条, 冲突 {} 条",
                request.getBookId(), deletedIds.size(), successIds.size(), conflicts.size());
        return vo;
    }

    /** 记录设备同步游标，便于排查与统计 */
    private void saveSyncCursor(Long userId, String deviceId, OffsetDateTime syncAt) {
        SyncLogEntity existing = syncLogMapper.selectOne(Wrappers.<SyncLogEntity>lambdaQuery()
                .eq(SyncLogEntity::getUserId, userId)
                .eq(SyncLogEntity::getDeviceId, deviceId)
                .last("limit 1"));
        if (existing == null) {
            SyncLogEntity entity = new SyncLogEntity();
            entity.setUserId(userId);
            entity.setDeviceId(deviceId);
            entity.setLastSyncAt(syncAt);
            syncLogMapper.insert(entity);
        } else {
            existing.setLastSyncAt(syncAt);
            syncLogMapper.updateById(existing);
        }
    }
}
