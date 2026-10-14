package com.jizhang.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

@Data
public class TransactionQueryRequest {

    @NotNull(message = "账本不能为空")
    private Long bookId;

    private Integer type;

    private Long accountId;

    private Long categoryId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime startTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime endTime;

    private String keyword;

    /** 金额区间（分） */
    private Long minAmount;

    private Long maxAmount;

    @Min(1)
    private Integer page = 1;

    @Min(1)
    @Max(200)
    private Integer size = 20;
}
