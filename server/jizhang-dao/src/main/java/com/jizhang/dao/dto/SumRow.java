package com.jizhang.dao.dto;

import lombok.Data;

/**
 * 收支汇总（金额单位：分）。
 */
@Data
public class SumRow {

    /** 收入合计 */
    private Long income;

    /** 支出合计 */
    private Long expense;
}
