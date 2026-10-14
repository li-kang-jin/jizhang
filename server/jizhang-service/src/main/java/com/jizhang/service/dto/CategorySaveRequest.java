package com.jizhang.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategorySaveRequest {

    @NotNull(message = "账本不能为空")
    private Long bookId;

    /** 父分类 ID，0 表示顶级 */
    private Long parentId = 0L;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 64, message = "分类名称最长 64 个字符")
    private String name;

    /** 1=支出 2=收入 */
    @NotNull(message = "分类类型不能为空")
    @Min(1)
    @Max(2)
    private Integer type;

    @Size(max = 64)
    private String icon;

    @Size(max = 16)
    private String color;

    private Integer sort = 0;
}
