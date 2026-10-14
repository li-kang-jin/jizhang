package com.jizhang.dao.dto;

import lombok.Data;

/**
 * 按时间段（日/月）聚合的收支统计结果行。
 */
@Data
public class PeriodStatRow {

    /** 时间段标识，日粒度 yyyy-MM-dd，月粒度 yyyy-MM */
    private String period;

    private Long income;

    private Long expense;
}
