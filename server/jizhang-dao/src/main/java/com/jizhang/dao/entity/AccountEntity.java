package com.jizhang.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 账户（现金 / 银行卡 / 支付宝 / 微信 / 投资 …）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_account")
public class AccountEntity extends BaseEntity {

    private Long bookId;

    private String name;

    /** 1=现金 2=储蓄卡 3=信用卡 4=支付宝 5=微信 6=投资 7=其他 */
    private Integer type;

    /** 当前余额（分），冗余字段，由交易变更维护 */
    private Long balance;

    /** 初始余额（分） */
    private Long initialBalance;

    private String currency;

    private String icon;

    private Integer sort;

    /** 是否计入总资产：0=否 1=是 */
    private Integer includeInTotal;

    private Integer version;
}
