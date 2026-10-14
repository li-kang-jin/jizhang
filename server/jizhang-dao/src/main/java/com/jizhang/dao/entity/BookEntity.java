package com.jizhang.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 账本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_book")
public class BookEntity extends BaseEntity {

    private String name;

    private String icon;

    /** 本位币，如 CNY */
    private String currency;

    /** 0=个人 1=家庭 2=共享 */
    private Integer type;

    /** 创建者用户 ID */
    private Long ownerId;

    private String remark;

    /** 乐观锁 / 同步版本号 */
    private Integer version;
}
