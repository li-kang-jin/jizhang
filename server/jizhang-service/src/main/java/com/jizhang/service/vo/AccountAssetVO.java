package com.jizhang.service.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.jizhang.dao.entity.AccountEntity;
import lombok.Data;

/**
 * 账户资产项。
 */
@Data
public class AccountAssetVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    private String name;

    private Integer type;

    private String icon;

    /** 当前余额（分） */
    private long balance;

    /** 是否计入总资产：0=否 1=是 */
    private Integer includeInTotal;

    public static AccountAssetVO from(AccountEntity entity) {
        AccountAssetVO vo = new AccountAssetVO();
        vo.setAccountId(entity.getId());
        vo.setName(entity.getName());
        vo.setType(entity.getType());
        vo.setIcon(entity.getIcon());
        vo.setBalance(entity.getBalance() == null ? 0L : entity.getBalance());
        vo.setIncludeInTotal(entity.getIncludeInTotal());
        return vo;
    }
}
