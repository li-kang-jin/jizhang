package com.jizhang.service.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.jizhang.dao.entity.CategoryEntity;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CategoryVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long bookId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    private String name;

    /** 1=支出 2=收入 */
    private Integer type;

    private String icon;

    private String color;

    private Integer sort;

    /** 是否系统内置分类（bookId = 0） */
    private Boolean system;

    /** 子分类，仅树形返回时填充 */
    private List<CategoryVO> children = new ArrayList<>();

    public static CategoryVO from(CategoryEntity entity) {
        if (entity == null) {
            return null;
        }
        CategoryVO vo = new CategoryVO();
        vo.setId(entity.getId());
        vo.setBookId(entity.getBookId());
        vo.setParentId(entity.getParentId());
        vo.setName(entity.getName());
        vo.setType(entity.getType());
        vo.setIcon(entity.getIcon());
        vo.setColor(entity.getColor());
        vo.setSort(entity.getSort());
        vo.setSystem(entity.getBookId() != null && entity.getBookId() == 0L);
        return vo;
    }
}
