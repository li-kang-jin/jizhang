package com.jizhang.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 管理后台 - 账户列表查询条件。
 */
@Data
public class AdminAccountQueryRequest {

    /** 账户名称模糊搜索 */
    private String keyword;

    private Long bookId;

    /** 按账本所有者过滤 */
    private Long userId;

    /** 1=现金 2=储蓄卡 3=信用卡 4=支付宝 5=微信 6=投资 7=其他 */
    private Integer type;

    @Min(1)
    private Integer page = 1;

    @Min(1)
    @Max(200)
    private Integer size = 20;
}
