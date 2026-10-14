package com.jizhang.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 收支分类（树形，支持系统内置与自定义）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_category")
public class CategoryEntity extends BaseEntity {

    /** 所属账本；0 表示系统内置分类 */
    private Long bookId;

    /** 父分类 ID，0 表示顶级 */
    private Long parentId;

    private String name;

    /** 1=支出 2=收入 */
    private Integer type;

    private String icon;

    private String color;

    private Integer sort;

    private Integer version;
}
