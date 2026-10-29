package com.jizhang.service.vo;

import lombok.Data;

/**
 * 管理后台 - 当前筛选条件下的流水合计（对账用）。
 * <p>
 * 统计范围是「整个筛选结果」而不是当前分页，否则翻页后合计会跳变，对账时没法用。
 */
@Data
public class AdminTransactionSummaryVO {

    /** 支出合计（分） */
    private long expense;

    /** 收入合计（分） */
    private long income;

    /** 转账合计（分） */
    private long transfer;

    /** 流水笔数 */
    private long count;
}
