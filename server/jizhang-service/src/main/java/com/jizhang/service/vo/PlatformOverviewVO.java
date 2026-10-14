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

    /** 交易总笔数 */
    private long transactionTotal;

    /** 今日交易笔数 */
    private long transactionToday;

    /** 累计交易金额（分） */
    private long amountTotal;

    /** 今日交易金额（分） */
    private long amountToday;
}
