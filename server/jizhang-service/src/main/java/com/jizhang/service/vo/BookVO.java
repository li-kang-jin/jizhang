package com.jizhang.service.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.jizhang.dao.entity.BookEntity;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class BookVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    private String icon;

    private String currency;

    private Integer type;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerId;

    /** 所有者登录名 / 昵称（管理后台列表由 Service 批量补齐，App 端不填） */
    private String ownerUsername;

    private String remark;

    /** 当前用户在账本中的角色：0=只读 1=可写 2=管理员 */
    private Integer myRole;

    /** 成员数（含所有者） */
    private Integer memberCount;

    /** 账户数（管理后台列表由 Service 批量补齐，App 端不填） */
    private Integer accountCount;

    /** 是否为我自己的个人账本（type=0 且我是 owner） */
    private Boolean personal;

    private OffsetDateTime createdAt;

    public static BookVO from(BookEntity entity) {
        if (entity == null) {
            return null;
        }
        BookVO vo = new BookVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setIcon(entity.getIcon());
        vo.setCurrency(entity.getCurrency());
        vo.setType(entity.getType());
        vo.setOwnerId(entity.getOwnerId());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
