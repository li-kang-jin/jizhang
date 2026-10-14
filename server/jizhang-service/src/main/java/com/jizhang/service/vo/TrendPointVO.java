package com.jizhang.service.vo;

import lombok.Data;

/**
 * 趋势图上的一个数据点（按日）。
 */
@Data
public class TrendPointVO {

    /** 日期 yyyy-MM-dd */
    private String date;

    /** 当日新增用户数 */
    private long newUsers;

    /** 当日新增交易笔数 */
    private long newTransactions;

    /** 当日交易金额（分） */
    private long amount;
}
