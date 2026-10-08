package com.zhyq.park.marketing.admin;

import com.zhyq.park.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Live database grants. Neither request bodies nor JWT role claims can grant administration. */
@Service
@RequiredArgsConstructor
public class MktAccountControl {
    private final JdbcTemplate jdbc;

    public record State(boolean superAdmin, boolean disabled, long revision) {}

    public State state(String type, Long id) {
        var rows = jdbc.query("SELECT super_admin,login_disabled,revision FROM crm_marketing_account_control WHERE identity_type=? AND identity_id=?",
                (rs, n) -> new State(rs.getBoolean(1), rs.getBoolean(2), rs.getLong(3)), type, id);
        return rows.isEmpty() ? new State(false, false, 0) : rows.get(0);
    }

    public boolean isSuperAdmin(Long id) {
        State state = state("mp", id);
        return state.superAdmin() && !state.disabled();
    }

    public Long requireSuperAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || !auth.getName().startsWith("mp:")
                || auth.getAuthorities().stream().noneMatch(a -> "ROLE_MP".equals(a.getAuthority())))
            throw new BizException(403, "仅小程序超级管理员可管理账号");
        long id;
        try { id = Long.parseLong(auth.getName().substring(3)); }
        catch (NumberFormatException e) { throw new BizException(403, "管理身份无效"); }
        if (id <= 0 || !isSuperAdmin(id)
                || !Integer.valueOf(1).equals(jdbc.queryForObject("SELECT COUNT(*) FROM crm_promoter WHERE id=? AND deleted=0 AND status=1", Integer.class, id)))
            throw new BizException(403, "仅正常状态的超级管理员可管理账号");
        return id;
    }
}
