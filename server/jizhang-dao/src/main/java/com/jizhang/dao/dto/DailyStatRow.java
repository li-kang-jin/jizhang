package com.jizhang.dao.dto;

import lombok.Data;

/**
 * 按日聚合统计的查询结果行（平台运营看板用）。
 */
@Data
public class DailyStatRow {

    /** 日期，格式 yyyy-MM-dd */
    private String day;

    /** 数量 */
    private Long cnt;

    /** 支出金额（分） */
    private Long expense;

    /** 收入金额（分） */
    private Long income;
}
