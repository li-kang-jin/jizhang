package com.jizhang.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 生成账本邀请码。
 */
@Data
public class InviteCreateRequest {

    /** 加入后的角色 0=只读 1=可写 2=管理员 */
    @Min(0)
    @Max(2)
    private Integer role = 1;

    /** 有效期天数，0 或空表示永不过期 */
    @Min(0)
    @Max(365)
    private Integer expireDays = 7;

    /** 最大使用次数，0 表示不限 */
    @Min(0)
    @Max(100)
    private Integer maxUses = 1;
}
