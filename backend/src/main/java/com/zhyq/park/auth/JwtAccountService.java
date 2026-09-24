package com.zhyq.park.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.auth.mapper.AuthQueryMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktCredential;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktCredentialMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Current database identity is authoritative; JWT roles are never used as an authorization snapshot. */
@Service
@RequiredArgsConstructor
public class JwtAccountService {
    private final SysUserMapper users;
    private final AuthQueryMapper permissions;
    private final MktPromoterMapper promoters;
    private final MktWarehouseMapper warehouses;
    private final MktCredentialMapper credentials;

    public record Account(String type, Long id, String subject, List<String> authorities, String credentialState) {
        @Override public String toString() { return "Account[type=" + type + ", id=" + id + "]"; }
    }

    public Account load(String type, Long id) {
        if (id == null || id <= 0) throw unavailable();
        if ("admin".equals(type)) {
            SysUser u = users.selectById(id);
            if (u == null || !Integer.valueOf(1).equals(u.getStatus()) || reservedUsername(u.getUsername())) throw unavailable();
            List<String> auth = new ArrayList<>();
            permissions.selectRoleCodesByUserId(id).stream().filter(s -> s != null && !s.isBlank())
                    .forEach(s -> auth.add("ROLE_" + s));
            permissions.selectPermsByUserId(id).stream().filter(s -> s != null && !s.isBlank()).forEach(auth::add);
            return new Account(type, id, u.getUsername(), List.copyOf(auth), "admin:" + id + ":" + u.getPassword());
        }
        String subject;
        if ("mp".equals(type)) {
            assertPromoterActive(promoters.selectById(id));
            subject = "mp:" + id;
        } else if ("wh".equals(type)) {
            assertWarehouseActive(warehouses.selectById(id));
            subject = "wh:" + id;
        } else throw unavailable();
        MktCredential credential = credentials.selectOne(new LambdaQueryWrapper<MktCredential>()
                .eq(MktCredential::getIdentityType, type).eq(MktCredential::getIdentityId, id));
        if (credential != null && !Integer.valueOf(1).equals(credential.getStatus())) throw unavailable();
        String state = credential == null ? "wechat-only" : credential.getId() + ":" + credential.getUsername() + ":" + credential.getPasswordHash();
        return new Account(type, id, subject, List.of("mp".equals(type) ? "ROLE_MP" : "ROLE_WH"), type + ":" + id + ":" + state);
    }

    public static boolean reservedUsername(String username) {
        if (username == null || username.isBlank()) return true;
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("mp:") || normalized.startsWith("wh:");
    }

    public static void assertPromoterActive(MktPromoter p) {
        if (p == null || !(Integer.valueOf(1).equals(p.getStatus()) || Integer.valueOf(3).equals(p.getStatus())))
            throw new BizException(403, "伙伴账号不存在、已冻结或已退出，请联系园区运营");
    }

    public static void assertWarehouseActive(MktWarehouse w) {
        if (w == null || w.getJoinStatus() == null || w.getJoinStatus() < 1 || w.getJoinStatus() > 6)
            throw new BizException(403, "云仓档案不存在或已退出，请联系园区运营");
    }

    private static BizException unavailable() { return new BizException(401, "登录状态已失效，请重新登录"); }
}
