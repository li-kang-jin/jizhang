package com.jizhang.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BookSaveRequest {

    @NotBlank(message = "账本名称不能为空")
    @Size(max = 64, message = "账本名称最长 64 个字符")
    private String name;

    @Size(max = 8, message = "币种代码过长")
    private String currency = "CNY";

    /** 0=个人 1=家庭 2=共享 */
    @Min(0)
    @Max(2)
    private Integer type = 0;

    @Size(max = 64)
    private String icon;

    @Size(max = 255)
    private String remark;
}
