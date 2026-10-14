package com.jizhang.service.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类占比报表（饼图数据源）。
 */
@Data
public class CategoryReportVO {

    /** 合计金额（分） */
    private long total;

    private List<CategoryStatVO> items = new ArrayList<>();
}
