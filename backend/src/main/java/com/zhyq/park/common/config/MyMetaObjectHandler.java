package com.zhyq.park.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充。
 * 操作人取当前登录用户(JWT subject = username);无认证上下文(定时任务/迁移)回退 system。
 * 租户默认 1(单租户运营主体),逻辑删除与版本号给初始值。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperator();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "createBy", String.class, operator);
        this.strictInsertFill(metaObject, "updateBy", String.class, operator);
        fillIfNull(metaObject, "tenantId", 1L);
        fillIfNull(metaObject, "version", 1);
        fillIfNull(metaObject, "deleted", 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updateBy", String.class, currentOperator());
    }

    /** 当前操作人:已认证用户名;匿名/无上下文 → system */
    public static String currentOperator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null
                || "anonymousUser".equals(auth.getName())) {
            return "system";
        }
        return auth.getName();
    }

    private void fillIfNull(MetaObject metaObject, String field, Object value) {
        if (metaObject.getValue(field) == null) {
            this.setFieldValByName(field, value, metaObject);
        }
    }
}
