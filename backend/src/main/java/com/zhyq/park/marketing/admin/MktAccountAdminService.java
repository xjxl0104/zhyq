package com.zhyq.park.marketing.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.MktCredentialMapper;
import com.zhyq.park.marketing.mapper.SysAuditMapper;
import com.zhyq.park.marketing.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MktAccountAdminService {
    private final MktAccountControl control;
    private final JdbcTemplate jdbc;
    private final MktPromoterService promoters;
    private final MktWarehouseOnboardingService warehouses;
    private final MktPromoterDeletionService deletion;
    private final MktCredentialMapper credentials;
    private final PasswordEncoder encoder;
    private final SysAuditMapper audits;
    private final ObjectMapper json;

    public record CreateRequest(String type, String name, String phone, String username,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password, String parentInviteCode, String reason) {
        @Override public String toString() { return "CreateRequest[credentials=REDACTED]"; }
    }
    public record InviteRequest(String inviteCode, String expectedInviteCode, String reason) {}
    public record StatusRequest(Boolean disabled, Boolean expectedDisabled, String reason) {}
    public record DeleteRequest(String reason) {}

    public Map<String, Object> page(String type, String keyword, int pageNo, int pageSize) {
        long operator = control.requireSuperAdmin();
        requireType(type);
        if (pageNo < 1 || pageNo > 100000 || pageSize < 1 || pageSize > 50) throw new BizException(400, "分页参数无效");
        String search = keyword == null ? "" : keyword.trim();
        if (search.length() > 100) throw new BizException(400, "搜索内容不能超过100字");
        String table = table(type);
        String code = "mp".equals(type) ? "p.invite_code" : "p.code";
        String status = "mp".equals(type) ? "p.status" : "p.join_status";
        String like = "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        String from = " FROM " + table + " p LEFT JOIN crm_marketing_credential c ON c.identity_type=? AND c.identity_id=p.id AND c.deleted=0 "
                + "LEFT JOIN crm_marketing_account_control ac ON ac.identity_type=? AND ac.identity_id=p.id WHERE p.deleted=0 "
                + "AND (p.name LIKE ? ESCAPE '!' OR p.phone LIKE ? ESCAPE '!' OR c.username LIKE ? ESCAPE '!' OR " + code + " LIKE ? ESCAPE '!')";
        Object[] args = {type, type, like, like, like, like};
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, args);
        List<Object> queryArgs = new ArrayList<>(Arrays.asList(args));
        queryArgs.add(pageSize); queryArgs.add((pageNo - 1) * pageSize);
        var rows = jdbc.queryForList("SELECT p.id,p.name,CASE WHEN p.phone LIKE 'p:%' THEN NULL ELSE p.phone END AS phone,"
                + "c.username," + code + " AS inviteCode," + status + " AS businessStatus,p.create_time AS createTime,"
                + "COALESCE(ac.super_admin,0) AS superAdmin,COALESCE(ac.login_disabled,0) AS loginDisabled"
                + from + " ORDER BY p.id DESC LIMIT ? OFFSET ?", queryArgs.toArray());
        // Explicit fields only: no password hashes, WeChat IDs, collection details or device data.
        return Map.of("records", rows, "total", total == null ? 0 : total, "operatorId", operator, "type", type);
    }

    @Transactional
    public Map<String, Object> create(CreateRequest request) {
        control.requireSuperAdmin();
        requireType(request.type());
        String reason = reason(request.reason());
        String name = required(request.name(), "姓名或云仓名称", "mp".equals(request.type()) ? 32 : 100);
        String username = required(request.username(), "登录账号", 512).toLowerCase(Locale.ROOT);
        String password = request.password();
        if (password == null || password.isEmpty()) throw new BizException(400, "请填写初始密码");
        if (password.length() > 4096) throw new BizException(400, "初始密码过长");
        String phone = MktSelfProfileService.optionalPhone(request.phone());
        if (phone != null && !jdbc.queryForList("SELECT id FROM crm_promoter WHERE phone=? AND deleted=0 UNION ALL SELECT id FROM crm_warehouse WHERE phone=? AND deleted=0 UNION ALL SELECT id FROM crm_warehouse_contact WHERE phone=? AND deleted=0",
                Long.class, phone, phone, phone).isEmpty()) throw new BizException(409, "手机号已有账号，请管理现有账号");
        String parent = request.parentInviteCode() == null || request.parentInviteCode().isBlank() ? null
                : MktPromoterService.requireRegistrationInvite(request.parentInviteCode());
        Long id;
        String invite = null;
        try {
            if ("mp".equals(request.type())) {
                MktPromoter p = new MktPromoter(); p.setName(name); p.setPhone(phone);
                p.setRemark("超级管理员创建；联系电话未经验证");
                promoters.registerManaged(p, parent); id = p.getId(); invite = p.getInviteCode();
            } else {
                MktWarehouse w = new MktWarehouse(); w.setName(name); w.setPhone(phone);
                w.setCode("WH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT));
                w.setRemark("超级管理员创建；待资质审核");
                warehouses.apply(w); id = w.getId();
            }
            MktCredential c = new MktCredential(); c.setIdentityType(request.type()); c.setIdentityId(id);
            c.setUsername(username); c.setPasswordHash(hash(password)); c.setRegistrationPhone(phone); c.setStatus(1);
            if (credentials.insert(c) != 1) throw new BizException("账号创建失败，请重试");
        } catch (DuplicateKeyException e) { throw new BizException(409, "账号或手机号已被使用，请修改后重试"); }
        audit("account.create", request.type(), id, reason, null, Map.of("name", name, "username", username));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id); result.put("type", request.type()); result.put("name", name);
        result.put("username", username); result.put("inviteCode", invite);
        return result;
    }

    @Transactional
    public void changeInvite(Long id, InviteRequest request) {
        control.requireSuperAdmin();
        String note = reason(request.reason());
        String next = MktPromoterService.requireRegistrationInvite(request.inviteCode());
        Map<String, Object> before = lock("mp", id);
        String current = String.valueOf(before.get("invite_code"));
        if (!current.equals(request.expectedInviteCode())) throw new BizException(409, "邀请码已变化，请刷新后重试");
        if (current.equals(next)) return;
        try {
            int changed = jdbc.update("UPDATE crm_promoter SET invite_code=?,version=version+1,update_by=?,update_time=NOW() WHERE id=? AND deleted=0 AND invite_code=?",
                    next, MktAuditService.currentOperator(), id, current);
            if (changed != 1) throw new BizException(409, "账号已变化，请刷新后重试");
        } catch (DuplicateKeyException e) { throw new BizException(409, "邀请码已被使用，请换一个"); }
        audit("account.invite.change", "mp", id, note, Map.of("inviteCode", current), Map.of("inviteCode", next));
    }

    @Transactional
    public void setDisabled(String type, Long id, StatusRequest request) {
        long operator = control.requireSuperAdmin();
        requireType(type);
        String note = reason(request.reason());
        if (request.disabled() == null || request.expectedDisabled() == null) throw new BizException(400, "请指定登录状态");
        protectAdmin(type, id, operator);
        lock(type, id);
        // The identity row serializes competing administrative operations, including first insert.
        var state = control.state(type, id);
        if (state.disabled() != request.expectedDisabled()) throw new BizException(409, "登录状态已变化，请刷新后重试");
        if (state.disabled() == request.disabled()) return;
        jdbc.update("INSERT INTO crm_marketing_account_control(identity_type,identity_id,login_disabled,update_by) VALUES(?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE login_disabled=VALUES(login_disabled),revision=revision+1,update_by=VALUES(update_by),update_time=NOW()",
                type, id, request.disabled() ? 1 : 0, MktAuditService.currentOperator());
        audit("account.login.status", type, id, note, Map.of("disabled", state.disabled()), Map.of("disabled", request.disabled()));
    }

    @Transactional
    public void delete(String type, Long id, String deleteReason) {
        long operator = control.requireSuperAdmin(); requireType(type);
        String note = reason(deleteReason);
        protectAdmin(type, id, operator);
        Map<String, Object> before = lock(type, id);
        if ("mp".equals(type)) { deletion.delete(id, note); return; }
        // Empty warehouses can be removed; contracts, ERP credentials, customer ownership and
        // money remain attached to their original archives. Locking range reads protect empty gaps.
        for (String relation : List.of("crm_service_contract", "crm_contract_warehouse", "crm_referral_order",
                "crm_warehouse_erp", "crm_customer_erp_map", "crm_erp_sync_log", "crm_erp_event_inbox", "crm_erp_dead_letter", "crm_erp_unmapped",
                "crm_erp_reconcile_snapshot", "crm_service_fee_bill", "crm_warehouse_settlement", "crm_direct_sign_payment")) {
            block("SELECT id FROM " + relation + " WHERE warehouse_id=? LIMIT 1 FOR UPDATE", id);
        }
        block("SELECT id FROM crm_customer WHERE intended_warehouse_id=? OR assigned_warehouse_id=? LIMIT 1 FOR UPDATE", id, id);
        String op = MktAuditService.currentOperator();
        jdbc.update("UPDATE crm_marketing_credential SET username=CONCAT('~deleted_',id),registration_phone=NULL,password_hash='!deleted',status=0,deleted=1,version=version+1,update_by=?,update_time=NOW() WHERE identity_type='wh' AND identity_id=?", op, id);
        jdbc.update("UPDATE crm_warehouse_contact SET openid=NULL,status=0,deleted=1,version=version+1,update_by=?,update_time=NOW() WHERE warehouse_id=? AND deleted=0", op, id);
        int changed = jdbc.update("UPDATE crm_warehouse SET deleted=1,join_status=7,contact_openid=NULL,version=version+1,update_by=?,update_time=NOW() WHERE id=? AND deleted=0", op, id);
        if (changed != 1) throw new BizException(409, "账号已变化，请刷新后重试");
        audit("account.delete", type, id, note, Map.of("name", before.get("name")), Map.of("deleted", true, "loginRevoked", true));
    }

    private void protectAdmin(String type, Long id, long operator) {
        if ("mp".equals(type) && (Objects.equals(id, operator) || control.state(type, id).superAdmin()))
            throw new BizException(409, "不能停用或删除超级管理员，请先由运维调整管理权限");
    }
    private Map<String, Object> lock(String type, Long id) {
        if (id == null || id <= 0) throw new BizException(400, "账号编号无效");
        String extra = "mp".equals(type) ? ",invite_code" : "";
        var rows = jdbc.queryForList("SELECT id,name" + extra + " FROM " + table(type) + " WHERE id=? AND deleted=0 FOR UPDATE", id);
        if (rows.isEmpty()) throw new BizException(404, "账号不存在或已删除，请刷新列表");
        return rows.get(0);
    }
    private void block(String sql, Object... args) {
        if (!jdbc.queryForList(sql, Long.class, args).isEmpty())
            throw new BizException(409, "该云仓有关联业务记录，不能删除；可停用登录并保留历史档案");
    }
    private void audit(String action, String type, Long id, String reason, Object before, Object after) {
        SysAudit audit = new SysAudit(); audit.setModule(MktAuditService.MODULE); audit.setAction(action);
        audit.setBizType("mp".equals(type) ? "promoter" : "warehouse"); audit.setBizId(id);
        audit.setOperator(MktAuditService.currentOperator()); audit.setReason(reason);
        try {
            audit.setBeforeJson(before == null ? null : json.writeValueAsString(before));
            audit.setAfterJson(json.writeValueAsString(after));
        } catch (Exception e) { throw new BizException("管理审计生成失败，请重试"); }
        if (audits.insert(audit) != 1) throw new BizException("管理审计保存失败，请重试");
    }
    private String hash(String password) {
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8)));
            return "{bcrypt-sha256}" + encoder.encode(digest);
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String table(String type) { requireType(type); return "mp".equals(type) ? "crm_promoter" : "crm_warehouse"; }
    private static void requireType(String type) {
        if (!"mp".equals(type) && !"wh".equals(type)) throw new BizException(400, "账号类型无效");
    }
    private static String required(String value, String label, int limit) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > limit) throw new BizException(400, label + "必填且不能超过" + limit + "字");
        return normalized;
    }
    private static String reason(String value) { return required(value, "操作原因", 500); }
}
