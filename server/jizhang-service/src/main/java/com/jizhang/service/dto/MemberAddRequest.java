package com.jizhang.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MemberAddRequest {

    @NotNull(message = "成员用户 ID 不能为空")
    private Long userId;

    /** 0=只读 1=可写 2=管理员 */
    @Min(0)
    @Max(2)
    private Integer role = 1;
}
