package com.jizhang.service.vo;

import lombok.Data;

/**
 * 收支趋势上的一个数据点（折线图数据源）。
 */
@Data
public class TrendItemVO {

    /** 时间段：日粒度 yyyy-MM-dd，月粒度 yyyy-MM */
    private String period;

    private long income;

    private long expense;

    /** 结余 */
    private long balance;
}
