package com.jizhang.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

/**
 * 管理后台 - 交易流水查询条件。
 */
@Data
public class AdminTransactionQueryRequest {

    private Long bookId;

    /** 按账户过滤：转出或转入命中该账户的流水都会返回 */
    private Long accountId;

    /** 按账本所有者过滤 */
    private Long userId;

    /** 1=支出 2=收入 3=转账 */
    private Integer type;

    private String keyword;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime startTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime endTime;

    /**
     * 是否包含已删除（软删除 deleted = 1）的流水。
     * <p>
     * 默认 false，与 App / 后台其它列表口径一致：删除后的记录不再出现在正常视图里。
     * 传 true 时列表与合计都会把墓碑记录一并纳入，供管理员排查「我删的那条去哪了」。
     */
    private Boolean includeDeleted = false;

    @Min(1)
    private Integer page = 1;

    @Min(1)
    @Max(200)
    private Integer size = 20;
}
