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

    @Min(1)
    private Integer page = 1;

    @Min(1)
    @Max(200)
    private Integer size = 20;
}
