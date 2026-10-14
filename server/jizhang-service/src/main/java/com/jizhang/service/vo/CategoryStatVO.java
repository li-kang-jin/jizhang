package com.jizhang.service.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 单个分类的统计项。
 */
@Data
public class CategoryStatVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;

    private String categoryName;

    private String icon;

    private String color;

    /** 金额（分） */
    private long amount;

    /** 占总支出的比例，0-1 */
    private double ratio;
}
