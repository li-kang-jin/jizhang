package com.jizhang.service.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.jizhang.dao.entity.AccountEntity;
import lombok.Data;

@Data
public class AccountVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long bookId;

    private String name;

    private Integer type;

    /** 当前余额（分） */
    private Long balance;

    private Long initialBalance;

    private String currency;

    private String icon;

    private Integer sort;

    private Integer includeInTotal;

    public static AccountVO from(AccountEntity entity) {
        if (entity == null) {
            return null;
        }
        AccountVO vo = new AccountVO();
        vo.setId(entity.getId());
        vo.setBookId(entity.getBookId());
        vo.setName(entity.getName());
        vo.setType(entity.getType());
        vo.setBalance(entity.getBalance());
        vo.setInitialBalance(entity.getInitialBalance());
        vo.setCurrency(entity.getCurrency());
        vo.setIcon(entity.getIcon());
        vo.setSort(entity.getSort());
        vo.setIncludeInTotal(entity.getIncludeInTotal());
        return vo;
    }
}
