package com.jizhang.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 增量拉取请求。
 */
@Data
public class SyncPullRequest {

    @NotBlank(message = "设备标识不能为空")
    private String deviceId;

    @NotNull(message = "账本不能为空")
    private Long bookId;

    /**
     * 上次同步时间。首次同步（全量）传空。
     */
    private OffsetDateTime lastSyncAt;
}
