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

/** 删除立即归档，不级联销毁合同、订单、财务或维护记录。 */
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
        complete(w, note);
    }

    /** 兼容旧版待删除记录，不再等待原定期限；每个云仓独立事务，行锁避免重复删除。 */
    @Transactional
    public boolean completePending(Long id) {
        MktWarehouse w = warehouses.selectForUpdate(id);
        if (w == null || !Integer.valueOf(PENDING_DELETE).equals(w.getJoinStatus())) return false;
        complete(w, w.getDeleteReason());
        return true;
    }

    private void complete(MktWarehouse w, String note) {
        Long id = w.getId();
        List<Long> released = releaseIdleCustomers(id);
        String op = MktAuditService.currentOperator();
        LocalDateTime now = LocalDateTime.now();
        int changed = jdbc.update("UPDATE crm_warehouse SET delete_previous_status=COALESCE(delete_previous_status,join_status),"
                + "deleted=1,join_status=7,contact_openid=NULL,delete_requested_at=COALESCE(delete_requested_at,?),"
                + "delete_due_at=?,delete_reason=?,version=version+1,update_by=?,update_time=NOW() "
                + "WHERE id=? AND deleted=0 AND join_status=?", now, now, note, op, id, w.getJoinStatus());
        changed(changed);
        jdbc.update("UPDATE crm_marketing_credential SET username=CONCAT('~deleted_',id),registration_phone=NULL,password_hash='!deleted',"
                + "status=0,deleted=1,version=version+1,update_by=?,update_time=NOW() WHERE identity_type='wh' AND identity_id=? AND deleted=0", op, id);
        jdbc.update("UPDATE crm_warehouse_contact SET openid=NULL,status=0,deleted=1,version=version+1,update_by=?,update_time=NOW() WHERE warehouse_id=? AND deleted=0", op, id);
        jdbc.update("UPDATE crm_warehouse_erp SET status=0,version=version+1,update_by=?,update_time=NOW() WHERE warehouse_id=? AND deleted=0", op, id);
        jdbc.update("UPDATE crm_competition_protection SET deleted=1,version=version+1,update_by=?,update_time=NOW() WHERE warehouse_id=? AND deleted=0", op, id);
        Map<String, Object> after = snapshot(w); after.put("deleted", true); after.put("status", 7);
        after.put("deleteDueAt", now.toString());
        after.put("releasedCustomerIds", released);
        audit.logRequired("warehouse.delete.complete", w, note, snapshot(w), after);
    }

    private List<Long> releaseIdleCustomers(Long id) {
        block("SELECT id FROM crm_service_contract WHERE warehouse_id=? AND deleted=0 AND status IN (1,2,3,4,5,6) LIMIT 1 FOR UPDATE",
                "该云仓仍有未结束的服务合同，请先处理合同后再删除", id);
        block("SELECT id FROM crm_yunshan_volume_map WHERE warehouse_id=? AND status=1 AND deleted=0 LIMIT 1 FOR UPDATE",
                "该云仓仍在拉取云山云仓发货量，请先停用发货量客户关联后再删除", id);
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
