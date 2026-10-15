package com.jizhang.service.vo;

import com.jizhang.dao.entity.BookInviteEntity;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 账本邀请码。
 */
@Data
public class BookInviteVO {

    private String code;

    /** 加入后的角色 0=只读 1=可写 2=管理员 */
    private Integer role;

    private OffsetDateTime expireAt;

    /** 最大使用次数，0 表示不限 */
    private Integer maxUses;

    private Integer usedCount;

    private OffsetDateTime createdAt;

    public static BookInviteVO from(BookInviteEntity entity) {
        if (entity == null) {
            return null;
        }
        BookInviteVO vo = new BookInviteVO();
        vo.setCode(entity.getCode());
        vo.setRole(entity.getRole());
        vo.setExpireAt(entity.getExpireAt());
        vo.setMaxUses(entity.getMaxUses());
        vo.setUsedCount(entity.getUsedCount());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
