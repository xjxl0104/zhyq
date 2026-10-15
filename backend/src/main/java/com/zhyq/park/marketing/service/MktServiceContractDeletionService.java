package com.zhyq.park.marketing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.entity.SysAudit;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.mapper.SysAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 仅清理已终止且无财务业务的合同，保留原始行、附件、版本和删除审计。 */
@Service
@RequiredArgsConstructor
public class MktServiceContractDeletionService {
    private final MktServiceContractMapper contracts;
    private final CustomerMapper customers;
    private final JdbcTemplate jdbc;
    private final SysAuditMapper audits;
    private final ObjectMapper json;

    @Transactional
    @PreAuthorize("hasRole('admin')")
    public void delete(Long id, String reason) {
        String note = reason == null ? "" : reason.trim();
        if (note.isEmpty() || note.length() > 500) throw new BizException(400, "删除原因必填且不能超过500字");
        MktServiceContract initial = id == null ? null : contracts.selectById(id);
        if (initial == null) throw new BizException(404, "合同不存在或已删除，请刷新列表");
        // 与终止和创建合同一致，先锁客户再锁合同；客户已归档也允许清理历史空合同。
        customers.selectForUpdate(initial.getCustomerId());
        MktServiceContract contract = contracts.selectForUpdate(id);
        if (contract == null) throw new BizException(404, "合同不存在或已删除，请刷新列表");
        if (!Integer.valueOf(MktServiceContractService.ST_TERMINATED).equals(contract.getStatus()))
            throw new BizException(409, "仅已终止的合同可以删除");
        block("SELECT id FROM fin_bill WHERE contract_id=? AND source='mkt_service' LIMIT 1 FOR UPDATE", id);
        block("SELECT id FROM crm_service_fee_bill WHERE contract_id=? LIMIT 1 FOR UPDATE", id);
        block("SELECT id FROM crm_direct_sign_payment WHERE contract_id=? LIMIT 1 FOR UPDATE", id);
        // 签约奖已全部作废且从未提现时不再阻止清理；真实业务和结算历史始终保留入口。
        block("SELECT id FROM crm_referral_order WHERE source_id=? AND source_type IN (2,3,4) AND (source_type<>4 OR EXISTS "
                + "(SELECT 1 FROM crm_promoter_commission pc WHERE pc.referral_order_id=crm_referral_order.id "
                + "AND (pc.status IS NULL OR pc.status<>5 OR COALESCE(pc.withdrawn_amount,0)>0))) LIMIT 1 FOR UPDATE", id);
        String operator = MktAuditService.currentOperator();
        int changed = jdbc.update("UPDATE crm_service_contract SET deleted=1,version=version+1,update_by=?,update_time=NOW() WHERE id=? AND deleted=0 AND status=8", operator, id);
        if (changed != 1) throw new BizException(409, "合同状态已变化，请刷新后重试");
        SysAudit audit = new SysAudit();
        audit.setModule(MktAuditService.MODULE); audit.setAction("contract.delete");
        audit.setBizType("service_contract"); audit.setBizId(id); audit.setReason(note);
        audit.setOperator(operator); audit.setProjectId(contract.getProjectId());
        try {
            audit.setBeforeJson(json.writeValueAsString(contract));
            audit.setAfterJson("{\"deleted\":1,\"status\":8}");
        } catch (JsonProcessingException e) {
            throw new BizException("删除审计生成失败，请重试");
        }
        if (audits.insert(audit) != 1) throw new BizException("删除审计保存失败，请重试");
    }

    private void block(String sql, Long id) {
        if (!jdbc.queryForList(sql, Long.class, id).isEmpty())
            throw new BizException(409, "该合同已有账单、订单或佣金记录，需保留财务档案，不能删除");
    }
}
