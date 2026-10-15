package com.jizhang.dao.dto;

import lombok.Data;

/**
 * 按记账人聚合的收支统计行。
 */
@Data
public class MemberStatRow {

    /** 记账人用户 ID */
    private Long userId;

    /** 收入合计（分） */
    private Long income;

    /** 支出合计（分） */
    private Long expense;

    /** 记账笔数 */
    private Long txCount;
}
