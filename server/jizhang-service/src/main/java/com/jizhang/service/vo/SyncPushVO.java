package com.jizhang.service.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量推送结果。
 */
@Data
public class SyncPushVO {

    /** 写入成功的记录 ID（客户端可标记为已同步） */
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> successIds = new ArrayList<>();

    /**
     * 冲突记录（服务端版本更新，采用 last-write-wins 策略）。
     * 返回的是服务端当前版本，客户端应以它覆盖本地。
     */
    private List<TransactionVO> conflicts = new ArrayList<>();

    private OffsetDateTime serverTime;
}
