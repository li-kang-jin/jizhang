package com.jizhang.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 管理后台 - 用户列表查询条件。
 */
@Data
public class AdminUserQueryRequest {

    /** 用户名 / 昵称 / 手机号模糊搜索 */
    private String keyword;

    /** 0=禁用 1=正常 */
    private Integer status;

    /** 角色：USER / ADMIN */
    private String role;

    @Min(1)
    private Integer page = 1;

    @Min(1)
    @Max(200)
    private Integer size = 20;
}
