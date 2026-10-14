package com.jizhang.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/**
 * 多端同步游标记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_sync_log")
public class SyncLogEntity extends BaseEntity {

    private Long userId;

    private String deviceId;

    private OffsetDateTime lastSyncAt;

    private Long syncCursor;
}
