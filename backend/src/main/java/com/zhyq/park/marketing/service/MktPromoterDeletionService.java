package com.zhyq.park.marketing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.SysAudit;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.SysAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/** Only empty partner archives may be removed. Business history is never cascaded or detached. */
@Service
@RequiredArgsConstructor
public class MktPromoterDeletionService {
    private final MktPromoterMapper promoters;
    private final JdbcTemplate jdbc;
    private final SysAuditMapper audits;
    private final ObjectMapper json;

    @Transactional
    public void delete(Long id, String reason) {
        String note = reason == null ? "" : reason.trim();
        if (note.isEmpty() || note.length() > 500) throw new BizException(400, "删除原因必填且不能超过500字");
        MktPromoter partner = id == null ? null : promoters.selectForUpdate(id);
        if (partner == null) throw new BizException(404, "伙伴不存在或已删除，请刷新列表");

        // Writers of these relations hold this same partner lock. Locking reads also avoid an
        // earlier repeatable-read snapshot hiding business records committed before we got it.
        block("SELECT id FROM crm_promoter WHERE deleted=0 AND id<>? AND (parent_id=? OR path LIKE ?) LIMIT 1 FOR UPDATE",
                "该伙伴仍有下级团队，请先调整下级的上级归属", id, id, partner.getPath() + "%");
        block("SELECT id FROM crm_lead WHERE referrer_id=? LIMIT 1 FOR UPDATE", "该伙伴已有推荐线索，不能删除，请使用冻结或退出", id);
        block("SELECT id FROM crm_customer WHERE referrer_id=? LIMIT 1 FOR UPDATE", "该伙伴已有推荐客户，不能删除，请使用冻结或退出", id);
        block("SELECT id FROM crm_customer_lock WHERE promoter_id=? LIMIT 1 FOR UPDATE", "该伙伴已有客户锁定记录，不能删除，请使用冻结或退出", id);
        block("SELECT id FROM crm_service_contract WHERE partner_id=? LIMIT 1 FOR UPDATE", "该伙伴已关联合同，不能删除，请使用冻结或退出", id);
        block("SELECT id FROM crm_referral_order WHERE promoter_id=? LIMIT 1 FOR UPDATE", "该伙伴已有业务订单，不能删除，请使用冻结或退出", id);
        block("SELECT id FROM crm_promoter_commission WHERE promoter_id=? LIMIT 1 FOR UPDATE", "该伙伴已有佣金流水，不能删除，请使用冻结或退出", id);
        block("SELECT id FROM crm_withdrawal WHERE promoter_id=? LIMIT 1 FOR UPDATE", "该伙伴已有提现记录，不能删除，请使用冻结或退出", id);

        String operator = MktAuditService.currentOperator();
        // Tombstones cannot be valid registration values. They remain unique even though the
        // existing unique indexes intentionally cover deleted rows as well as active records.
        jdbc.update("UPDATE crm_marketing_credential SET username=CONCAT('~deleted_',id), registration_phone=NULL, "
                        + "password_hash='!deleted', status=0, deleted=1, version=version+1, update_by=?, update_time=NOW() "
                        + "WHERE identity_type='mp' AND identity_id=?", operator, id);
        int changed = jdbc.update("UPDATE crm_promoter SET deleted=1, status=4, phone=?, openid=NULL, unionid=NULL, "
                        + "version=version+1, update_by=?, update_time=NOW() WHERE id=? AND deleted=0",
                "D" + Long.toString(id, 36), operator, id);
        if (changed != 1) throw new BizException(409, "伙伴已变化，请刷新后重试");

        // Deletion must fail closed when its audit cannot be saved, unlike routine best-effort logs.
        SysAudit audit = new SysAudit();
        audit.setModule(MktAuditService.MODULE);
        audit.setAction("promoter.delete");
        audit.setBizType("promoter");
        audit.setBizId(id);
        audit.setReason(note);
        audit.setOperator(operator);
        audit.setProjectId(partner.getProjectId());
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("id", id); before.put("name", partner.getName()); before.put("status", partner.getStatus());
        before.put("parentId", partner.getParentId()); before.put("inviteCode", partner.getInviteCode());
        try {
            audit.setBeforeJson(json.writeValueAsString(before));
            audit.setAfterJson("{\"deleted\":1,\"status\":4,\"loginRevoked\":true}");
        } catch (JsonProcessingException e) {
            throw new BizException("删除审计生成失败，请重试");
        }
        if (audits.insert(audit) != 1) throw new BizException("删除审计保存失败，请重试");
    }

    private void block(String sql, String message, Object... args) {
        if (!jdbc.queryForList(sql, Long.class, args).isEmpty()) throw new BizException(409, message);
    }
}
