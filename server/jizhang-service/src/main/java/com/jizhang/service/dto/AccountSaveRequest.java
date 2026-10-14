package com.jizhang.service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AccountSaveRequest {

    /** 新增必填；修改时忽略 */
    @NotNull(message = "账本不能为空")
    private Long bookId;

    @NotBlank(message = "账户名称不能为空")
    @Size(max = 64, message = "账户名称最长 64 个字符")
    private String name;

    /** 1=现金 2=储蓄卡 3=信用卡 4=支付宝 5=微信 6=投资 7=其他 */
    @NotNull(message = "账户类型不能为空")
    private Integer type;

    /** 初始余额（分） */
    private Long initialBalance = 0L;

    @Size(max = 8)
    private String currency = "CNY";

    @Size(max = 64)
    private String icon;

    private Integer sort = 0;

    /** 是否计入总资产：0=否 1=是 */
    @Min(0)
    private Integer includeInTotal = 1;
}
