package com.zhyq.park.marketing.service;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/** 删除采用可撤销冻结和到期归档，不级联销毁合同、订单、财务或维护记录。 */
@Service
@RequiredArgsConstructor
public class MktWarehouseDeletionService {
    public static final int PENDING_DELETE = 8;
    private final MktWarehouseMapper warehouses;
    private final JdbcTemplate jdbc;
    private final MktAuditService audit;

    @Transactional
    public void request(Long id, String reason) {
        String note = reason(reason);
        MktWarehouse w = require(id);
        if (Integer.valueOf(PENDING_DELETE).equals(w.getJoinStatus()))
            throw new BizException(409, "该云仓已冻结待删除，请勿重复提交");
        List<Long> released = releaseIdleCustomers(id);
        LocalDateTime now = LocalDateTime.now(), due = now.plusDays(7);
        int changed = jdbc.update("UPDATE crm_warehouse SET delete_previous_status=join_status,join_status=8,"
                        + "delete_requested_at=?,delete_due_at=?,delete_reason=?,version=version+1,update_by=?,update_time=NOW() WHERE id=? AND deleted=0 AND join_status<>8",
                now, due, note, MktAuditService.currentOperator(), id);
        changed(changed);
        Map<String, Object> after = snapshot(w); after.put("status", PENDING_DELETE);
        after.put("deleteDueAt", due.toString()); after.put("loginFrozen", true);
        after.put("releasedCustomerIds", released);
        audit.logRequired("warehouse.delete.request", w, note, snapshot(w), after);
    }

    @Transactional
    public void cancel(Long id, String reason) {
        String note = reason(reason);
        MktWarehouse w = require(id);
        // 到期后不允许延长窗口，即使定时任务尚未运行。
        if (!Integer.valueOf(PENDING_DELETE).equals(w.getJoinStatus()) || w.getDeleteDueAt() == null
                || !w.getDeleteDueAt().isAfter(LocalDateTime.now()))
            throw new BizException(409, "该云仓不在可撤销的7天冻结期内，请刷新列表");
        int changed = jdbc.update("UPDATE crm_warehouse SET join_status=delete_previous_status,delete_previous_status=NULL,"
                        + "delete_requested_at=NULL,delete_due_at=NULL,delete_reason=NULL,version=version+1,update_by=?,update_time=NOW() "
                        + "WHERE id=? AND deleted=0 AND join_status=8 AND delete_due_at>NOW()",
                MktAuditService.currentOperator(), id);
        changed(changed);
        Map<String, Object> after = snapshot(w); after.put("status", w.getDeletePreviousStatus());
        audit.logRequired("warehouse.delete.cancel", w, note, snapshot(w), after);
    }

    /** 每个云仓独立事务；行锁保证撤销与多实例任务不会重复完成删除。 */
    @Transactional
    public boolean completeDue(Long id) {
        MktWarehouse w = warehouses.selectForUpdate(id);
        if (w == null || !Integer.valueOf(PENDING_DELETE).equals(w.getJoinStatus()) || w.getDeleteDueAt() == null
                || w.getDeleteDueAt().isAfter(LocalDateTime.now())) return false;
        List<Long> released = releaseIdleCustomers(id);
        String op = MktAuditService.currentOperator();
        int changed = jdbc.update("UPDATE crm_warehouse SET deleted=1,join_status=7,contact_openid=NULL,version=version+1,update_by=?,update_time=NOW() "
                + "WHERE id=? AND deleted=0 AND join_status=8 AND delete_due_at<=NOW()", op, id);
        changed(changed);
        jdbc.update("UPDATE crm_marketing_credential SET username=CONCAT('~deleted_',id),registration_phone=NULL,password_hash='!deleted',"
                + "status=0,deleted=1,version=version+1,update_by=?,update_time=NOW() WHERE identity_type='wh' AND identity_id=? AND deleted=0", op, id);
        jdbc.update("UPDATE crm_warehouse_contact SET openid=NULL,status=0,deleted=1,version=version+1,update_by=?,update_time=NOW() WHERE warehouse_id=? AND deleted=0", op, id);
        jdbc.update("UPDATE crm_warehouse_erp SET status=0,version=version+1,update_by=?,update_time=NOW() WHERE warehouse_id=? AND deleted=0", op, id);
        jdbc.update("UPDATE crm_competition_protection SET deleted=1,version=version+1,update_by=?,update_time=NOW() WHERE warehouse_id=? AND deleted=0", op, id);
        Map<String, Object> after = snapshot(w); after.put("deleted", true); after.put("status", 7);
        after.put("releasedCustomerIds", released);
        audit.logRequired("warehouse.delete.complete", w, w.getDeleteReason(), snapshot(w), after);
        return true;
    }

    private List<Long> releaseIdleCustomers(Long id) {
        block("SELECT id FROM crm_service_contract WHERE warehouse_id=? AND deleted=0 AND status IN (1,2,3,4,5,6) LIMIT 1 FOR UPDATE",
                "该云仓仍有未结束的服务合同，请先处理合同后再删除", id);
        block("SELECT id FROM crm_yunshan_volume_map WHERE warehouse_id=? AND status=1 AND deleted=0 LIMIT 1 FOR UPDATE",
                "该云仓仍在拉取云山发货量，请先停用云山映射后再删除", id);
        List<Long> customerIds = jdbc.queryForList("SELECT id FROM crm_customer WHERE assigned_warehouse_id=? AND deleted=0 ORDER BY id FOR UPDATE", Long.class, id);
        // 客户行锁与新建合同共用；逐个检查所有云仓的未结束合同，不能误释放仍在履约的客户。
        for (Long customerId : customerIds) {
            block("SELECT id FROM crm_service_contract WHERE customer_id=? AND deleted=0 AND status IN (1,2,3,4,5,6) LIMIT 1 FOR UPDATE",
                    "云仓关联客户仍有未结束的服务合同，请先处理合同或调整客户归属", customerId);
        }
        for (Long customerId : customerIds) {
            int affected = jdbc.update("UPDATE crm_customer SET assigned_warehouse_id=NULL,warehouse_assignment_status=0,version=version+1,update_by=?,update_time=NOW() WHERE id=? AND assigned_warehouse_id=? AND deleted=0",
                    MktAuditService.currentOperator(), customerId, id);
            if (affected != 1) throw new BizException(409, "客户归属已变化，请刷新后重试");
        }
        return customerIds;
    }

    private void block(String sql, String message, Long id) {
        if (!jdbc.queryForList(sql, Long.class, id).isEmpty()) throw new BizException(409, message);
    }
    private MktWarehouse require(Long id) {
        MktWarehouse w = id == null ? null : warehouses.selectForUpdate(id);
        if (w == null) throw new BizException(404, "云仓不存在或已删除，请刷新列表");
        return w;
    }
    private static String reason(String reason) {
        String note = reason == null ? "" : reason.trim();
        if (note.isEmpty() || note.length() > 500) throw new BizException(400, "操作原因必填且不能超过500字");
        return note;
    }
    private static void changed(int changed) {
        if (changed != 1) throw new BizException(409, "云仓状态已变化，请刷新后重试");
    }
    private static Map<String, Object> snapshot(MktWarehouse w) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", w.getId()); out.put("name", w.getName()); out.put("code", w.getCode()); out.put("status", w.getJoinStatus());
        if (w.getDeleteDueAt() != null) out.put("deleteDueAt", w.getDeleteDueAt().toString());
        return out;
    }
}
