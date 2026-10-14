package com.jizhang.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 管理后台 - 账本列表查询条件。
 */
@Data
public class AdminBookQueryRequest {

    /** 账本名称 / 备注模糊搜索 */
    private String keyword;

    private Long ownerId;

    /** 0=个人 1=家庭 2=共享 */
    private Integer type;

    @Min(1)
    private Integer page = 1;

    @Min(1)
    @Max(200)
    private Integer size = 20;
}
