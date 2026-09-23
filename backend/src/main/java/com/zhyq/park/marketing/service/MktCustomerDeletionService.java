package com.zhyq.park.marketing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.SysAudit;
import com.zhyq.park.marketing.mapper.SysAuditMapper;
import com.zhyq.park.marketing.mp.MpAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 删除共享入口：客户及锁记录逻辑删除，合同、订单、佣金和 CRM 跟进历史不可级联删除。 */
@Service
@RequiredArgsConstructor
public class MktCustomerDeletionService {
    private final CustomerMapper customers;
    private final JdbcTemplate jdbc;
    private final SysAuditMapper audits;
    private final ObjectMapper json;

    @Transactional
    @PreAuthorize("hasRole('admin')")
    public void deleteAsAdmin(Long id) {
        delete(requireLocked(id), "管理员删除推荐客户");
    }

    @Transactional
    @PreAuthorize("hasRole('MP')")
    public void deleteOwnReferral(Long id) {
        Long promoterId = MpAuthService.currentPromoterId();
        Customer customer = id == null ? null : customers.selectForUpdate(id);
        // 不向其他伙伴泄漏该编号是否存在或是否已签约。
        if (customer == null || !Objects.equals(promoterId, customer.getReferrerId()))
            throw new BizException(403, "无权删除该客户，仅能删除自己推荐的客户");
        delete(customer, "园区伙伴删除自己推荐的客户");
    }

    @Transactional
    @PreAuthorize("hasAuthority('crm:customer:delete')")
    public void deleteOrdinary(Long id) {
        Customer customer = requireLocked(id);
        if (customer.getReferrerId() != null || customer.getAssignedWarehouseId() != null)
            throw new BizException(403, "推荐客户请由管理员在全民营销中删除，或由推荐伙伴在小程序中删除");
        block("SELECT id FROM crm_customer_lock WHERE customer_id=? LIMIT 1 FOR UPDATE",
                "该客户关联营销业务，请到全民营销处理", id);
        delete(customer, "删除意向客户");
    }

    private Customer requireLocked(Long id) {
        Customer customer = id == null ? null : customers.selectForUpdate(id);
        if (customer == null) throw new BizException(404, "客户不存在或已删除，请刷新列表");
        return customer;
    }

    private void delete(Customer customer, String reason) {
        Long id = customer.getId();
        // 跟进记录属于 source_lead_id；保留已转化线索与客户的对应关系，不能只删一端。
        if (customer.getSourceLeadId() != null)
            throw new BizException(409, "该客户由 CRM 线索转化，需保留线索及跟进记录，请标记流失");
        if (Integer.valueOf(2).equals(customer.getStatus()))
            throw new BizException(409, "该客户已签约，需保留客户及履约档案，不能删除");

        // 创建合同、分派、转移及成交使用相同客户行锁；FOR UPDATE 读取最新提交结果，
        // 包括逻辑删除的财务历史，避免旧的 REPEATABLE READ 快照遗漏关联记录。
        block("SELECT id FROM crm_service_contract WHERE customer_id=? LIMIT 1 FOR UPDATE",
                "该客户已关联服务合同，需保留合同档案，不能删除", id);
        block("SELECT id FROM crm_referral_order WHERE customer_id=? LIMIT 1 FOR UPDATE",
                "该客户已有业务订单或佣金记录，需保留财务档案，不能删除", id);
        block("SELECT id FROM crm_customer_lock WHERE customer_id=? AND status=3 LIMIT 1 FOR UPDATE",
                "该客户已有成交记录，需保留业务档案，不能删除", id);
        block("SELECT id FROM crm_customer_erp_map WHERE customer_id=? AND deleted=0 LIMIT 1 FOR UPDATE",
                "该客户已绑定 ERP 货主编码，请先解除映射后再删除", id);

        String operator = MktAuditService.currentOperator();
        int changed = jdbc.update("UPDATE crm_customer SET deleted=1, version=version+1, update_by=?, update_time=NOW() "
                + "WHERE id=? AND deleted=0", operator, id);
        if (changed != 1) throw new BizException(409, "客户已变化，请刷新后重试");
        // 释放活动锁归还伙伴配额；历史记录仍在数据库中，普通业务查询不再显示。
        jdbc.update("UPDATE crm_customer_lock SET status=4, released_reason=?, released_by=?, released_at=NOW(), "
                + "version=version+1, update_by=?, update_time=NOW() WHERE customer_id=? AND deleted=0 AND status IN (1,2)",
                reason, operator, operator, id);
        jdbc.update("UPDATE crm_customer_lock SET deleted=1, version=version+1, update_by=?, update_time=NOW() "
                + "WHERE customer_id=? AND deleted=0", operator, id);

        // 与伙伴删除一样，审计失败必须使整个删除事务回滚。
        SysAudit audit = new SysAudit();
        audit.setModule(MktAuditService.MODULE);
        audit.setAction("customer.delete");
        audit.setBizType("customer");
        audit.setBizId(id);
        audit.setReason(reason);
        audit.setOperator(operator);
        audit.setProjectId(customer.getProjectId());
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("id", id); before.put("name", customer.getName()); before.put("status", customer.getStatus());
        before.put("referrerId", customer.getReferrerId()); before.put("assignedWarehouseId", customer.getAssignedWarehouseId());
        try {
            audit.setBeforeJson(json.writeValueAsString(before));
            audit.setAfterJson("{\"deleted\":1,\"locksReleased\":true}");
        } catch (JsonProcessingException e) {
            throw new BizException("删除审计生成失败，请重试");
        }
        if (audits.insert(audit) != 1) throw new BizException("删除审计保存失败，请重试");
    }

    private void block(String sql, String message, Object... args) {
        if (!jdbc.queryForList(sql, Long.class, args).isEmpty()) throw new BizException(409, message);
    }
}
