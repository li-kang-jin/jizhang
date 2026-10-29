package com.jizhang.service.vo;

import lombok.Data;

/**
 * 平台运营总览。
 */
@Data
public class PlatformOverviewVO {

    /** 用户总数 */
    private long userTotal;

    /** 今日新增用户 */
    private long userToday;

    /** 账本总数 */
    private long bookTotal;

    /** 今日有记账行为的账本数（按交易时间去重） */
    private long activeBookToday;

    /** 交易总笔数 */
    private long transactionTotal;

    /** 今日交易笔数（按交易时间口径，与金额统计一致） */
    private long transactionToday;

    /** 累计支出（分），不含转账 */
    private long expenseTotal;

    /** 累计收入（分），不含转账 */
    private long incomeTotal;

    /** 今日支出（分），不含转账 */
    private long expenseToday;

    /** 今日收入（分），不含转账 */
    private long incomeToday;
}
