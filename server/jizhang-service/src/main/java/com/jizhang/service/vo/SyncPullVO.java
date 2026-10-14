package com.jizhang.service.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 增量拉取结果。
 * <p>
 * 客户端处理规则：
 * <ol>
 *   <li>用各集合中的记录 upsert 本地数据；</li>
 *   <li>用各 deletedXxxIds 删除本地对应记录；</li>
 *   <li><b>把 nextCursor 记为下次的 lastSyncAt</b>（不要用 serverTime）。</li>
 * </ol>
 */
@Data
public class SyncPullVO {

    /** 服务端当前时间，仅用于展示与排查 */
    private OffsetDateTime serverTime;

    /**
     * 下一次拉取应使用的游标。
     * <p>
     * 正常情况下等于本轮所有变更记录中最大的 updatedAt；
     * 当本轮交易被条数上限截断（hasMore = true）时，等于本页最后一条交易的 updatedAt，
     * 保证继续翻页时不会漏数据。
     */
    private OffsetDateTime nextCursor;

    /** 是否还有更多数据（单次拉取有条数上限） */
    private boolean hasMore;

    private List<BookVO> books = new ArrayList<>();

    private List<AccountVO> accounts = new ArrayList<>();

    private List<CategoryVO> categories = new ArrayList<>();

    private List<TransactionVO> transactions = new ArrayList<>();

    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> deletedBookIds = new ArrayList<>();

    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> deletedAccountIds = new ArrayList<>();

    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> deletedCategoryIds = new ArrayList<>();

    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> deletedTransactionIds = new ArrayList<>();
}
