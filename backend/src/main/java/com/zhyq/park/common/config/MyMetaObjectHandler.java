package com.zhyq.park.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充。
 * 说明:本系统按需求不做登录鉴权,操作人统一填充为 system;
 * 租户默认 1(单租户运营主体),逻辑删除与版本号给初始值。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "createBy", String.class, "system");
        this.strictInsertFill(metaObject, "updateBy", String.class, "system");
        fillIfNull(metaObject, "tenantId", 1L);
        fillIfNull(metaObject, "version", 1);
        fillIfNull(metaObject, "deleted", 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updateBy", String.class, "system");
    }

    private void fillIfNull(MetaObject metaObject, String field, Object value) {
        if (metaObject.getValue(field) == null) {
            this.setFieldValByName(field, value, metaObject);
        }
    }
}
