package com.jizhang.dao.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.common.context.UserContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 审计字段与软删除标记自动填充。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Long userId = UserContext.getUserId();
        strictInsertFill(metaObject, "createdAt", OffsetDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", OffsetDateTime.class, now);
        if (userId != null) {
            strictInsertFill(metaObject, "createdBy", Long.class, userId);
            strictInsertFill(metaObject, "updatedBy", Long.class, userId);
        }
        strictInsertFill(metaObject, "deleted", Integer.class, CommonConstants.NOT_DELETED);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", OffsetDateTime.class, OffsetDateTime.now(ZoneOffset.UTC));
        Long userId = UserContext.getUserId();
        if (userId != null) {
            strictUpdateFill(metaObject, "updatedBy", Long.class, userId);
        }
    }
}
