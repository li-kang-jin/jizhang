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

    private String remark;

    /** 当前用户在账本中的角色：0=只读 1=可写 2=管理员 */
    private Integer myRole;

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
