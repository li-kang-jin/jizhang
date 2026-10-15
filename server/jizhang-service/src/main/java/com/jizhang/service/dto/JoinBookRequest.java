package com.jizhang.service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 凭邀请码加入账本。
 */
@Data
public class JoinBookRequest {

    @NotBlank(message = "邀请码不能为空")
    private String code;
}
