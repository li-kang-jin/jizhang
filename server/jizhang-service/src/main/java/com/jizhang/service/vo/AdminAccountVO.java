package com.jizhang.service.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.jizhang.dao.entity.AccountEntity;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 管理后台 - 账户视图。
 * <p>
 * 比 App 端的 {@link AccountVO} 多带「所属账本名 / 账本所有者」：后台看的是全平台数据，
 * 只给一串 ID 管理员根本认不出这是谁的账户。这两个字段由 AdminService 批量补齐
 * （见 {@code AdminServiceImpl#decorateAccounts}），单个账户场景也能直接补齐。
 */
@Data
public class AdminAccountVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long bookId;

    /** 所属账本名称；账本已被删除时为 null */
    private String bookName;

    /** 账本所有者（用户 ID） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerId;

    /** 账本所有者登录名 */
    private String ownerUsername;

    private String name;

    /** 1=现金 2=储蓄卡 3=信用卡 4=支付宝 5=微信 6=投资 7=其他 */
    private Integer type;

    /** 当前余额（分），由交易流水维护 */
    private Long balance;

    /** 初始余额（分） */
    private Long initialBalance;

    private String currency;

    private String icon;

    private Integer sort;

    /** 是否计入总资产：0=否 1=是 */
    private Integer includeInTotal;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /** 只填账户自身的字段；账本 / 所有者信息由 Service 补齐 */
    public static AdminAccountVO from(AccountEntity entity) {
        if (entity == null) {
            return null;
        }
        AdminAccountVO vo = new AdminAccountVO();
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
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
