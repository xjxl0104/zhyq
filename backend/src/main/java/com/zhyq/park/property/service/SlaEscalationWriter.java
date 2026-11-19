package com.zhyq.park.property.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.notify.NotificationService;
import com.zhyq.park.property.entity.WorkOrder;
import com.zhyq.park.property.entity.WorkOrderLog;
import com.zhyq.park.property.mapper.WorkOrderLogMapper;
import com.zhyq.park.property.mapper.WorkOrderMapper;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SlaEscalationWriter {
    private final WorkOrderMapper orders;
    private final WorkOrderLogMapper logs;
    private final SysUserMapper users;
    private final NotificationService notifications;
    @Value("${zhyq.sla.duty-username:admin}")
    private String dutyUsername;

    @Transactional
    public void escalate(WorkOrder wo, int slaState, String reason) {
        // Reassignment or a status change during the scan makes the old decision stale.
        var guard = new LambdaUpdateWrapper<WorkOrder>()
                .eq(WorkOrder::getId, wo.getId()).eq(WorkOrder::getStatus, wo.getStatus())
                .eq(WorkOrder::getEscalated, 0).isNull(WorkOrder::getSlaState)
                .isNull(wo.getAssignee() == null, WorkOrder::getAssignee)
                .eq(wo.getAssignee() != null, WorkOrder::getAssignee, wo.getAssignee())
                .set(WorkOrder::getSlaState, slaState).set(WorkOrder::getEscalated, 1);
        if (orders.update(null, guard) == 0) return;
        SysUser receiver = recipient(wo);
        if (receiver == null || !notifications.sendInApp(receiver.getId(), "工单SLA超时：" + reason,
                "工单 " + wo.getCode() + " " + reason + "，请及时处理", "workorder", wo.getId())) {
            throw new IllegalStateException("工单升级通知未送达，等待下一次扫描");
        }
        WorkOrderLog entry = new WorkOrderLog();
        entry.setOrderId(wo.getId());
        entry.setTenantId(wo.getTenantId());
        entry.setAction("SLA升级");
        entry.setOperator("system");
        entry.setContent(reason + "，已通知 " + receiver.getUsername());
        logs.insert(entry);
    }

    private LambdaQueryWrapper<SysUser> activeUsers(WorkOrder wo) {
        return new LambdaQueryWrapper<SysUser>().eq(SysUser::getTenantId, wo.getTenantId())
                .eq(SysUser::getStatus, 1);
    }

    private SysUser recipient(WorkOrder wo) {
        String name = wo.getAssignee();
        if (name != null && !name.isBlank()) {
            var account = users.selectOne(activeUsers(wo).eq(SysUser::getUsername, name));
            if (account != null) return account;
            var legacy = users.selectList(activeUsers(wo).eq(SysUser::getNickname, name).last("limit 2"));
            if (legacy.size() == 1) return legacy.get(0);
        }
        return users.selectOne(activeUsers(wo).eq(SysUser::getUsername, dutyUsername));
    }
}
