package com.jizhang.dao.dto;

import lombok.Data;

/**
 * 分类占比统计结果行。
 */
@Data
public class CategoryStatRow {

    private Long categoryId;

    private String categoryName;

    private String icon;

    private String color;

    /** 该分类合计金额（分） */
    private Long totalAmount;
}
