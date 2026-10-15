package com.jizhang.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改成员角色 / 家庭内称呼。
 */
@Data
public class MemberUpdateRequest {

    /** 0=只读 1=可写 2=管理员，为空表示不改 */
    @Min(0)
    @Max(2)
    private Integer role;

    /** 家庭内称呼（如「爸爸」），传空串表示清除 */
    @Size(max = 32, message = "称呼最长 32 个字符")
    private String alias;
}
