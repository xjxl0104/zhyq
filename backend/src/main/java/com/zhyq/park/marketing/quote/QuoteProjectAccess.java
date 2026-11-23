package com.zhyq.park.marketing.quote;

import com.zhyq.park.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

/** Resolves a PC operator's current tenant from their active back-office identity. */
public final class QuoteProjectAccess {
    private QuoteProjectAccess() {}

    public static long requireTenant(JdbcTemplate jdbc, Long projectId) {
        if (projectId == null || projectId <= 0) throw new BizException(400,"请先选择园区");
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new BizException(401,"请先登录后台");
        var ids = jdbc.queryForList("""
            SELECT u.tenant_id FROM sys_user u JOIN biz_project p
              ON p.id=? AND p.deleted=0 AND p.tenant_id=u.tenant_id
            WHERE u.username=? AND u.status=1 AND u.deleted=0
            """, Long.class, projectId, auth.getName());
        if (ids.size() != 1 || ids.get(0) == null) throw new BizException(403,"无权访问当前园区");
        return ids.get(0);
    }
}
