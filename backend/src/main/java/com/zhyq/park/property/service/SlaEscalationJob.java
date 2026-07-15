package com.zhyq.park.property.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.notify.NotificationService;
import com.zhyq.park.property.entity.WorkOrder;
import com.zhyq.park.property.entity.WorkOrderLog;
import com.zhyq.park.property.mapper.WorkOrderLogMapper;
import com.zhyq.park.property.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SLA 超时升级扫描(#10)。每 10 分钟扫一次活动工单(status in 1..4、escalated=0、sla_state IS NULL):
 * 响应超时(status 1/2,超 slaRespondMin)打 sla_state=1;解决超时(status 3/4,超 slaResolveMin)打 sla_state=2。
 * 只标记 sla_state/escalated,不动主 status——工单继续正常流转(design §5 D1)。
 * 条件 UPDATE(eq escalated=0)防并发定时重复触发;通知失败不得中断扫描。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlaEscalationJob {

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderLogMapper workOrderLogMapper;
    private final NotificationService notificationService;

    /** 每 10 分钟(避开:00),扫描活动工单打 SLA 超时态 */
    @Scheduled(cron = "0 3/10 * * * ?")
    public void scan() {
        try {
            doScan();
        } catch (Exception e) {
            log.error("[sla] escalation scan failed", e);
        }
    }

    /** 供 debug 端点手动触发,复用同一扫描逻辑 */
    public void doScan() {
        LocalDateTime now = LocalDateTime.now();
        List<WorkOrder> actives = workOrderMapper.selectList(
                new LambdaQueryWrapper<WorkOrder>()
                        .in(WorkOrder::getStatus,
                                WorkOrderService.ST_PENDING_DISPATCH, WorkOrderService.ST_PENDING_ACCEPT,
                                WorkOrderService.ST_PROCESSING, WorkOrderService.ST_PENDING_VERIFY)
                        .eq(WorkOrder::getEscalated, 0)
                        .isNull(WorkOrder::getSlaState));

        for (WorkOrder wo : actives) {
            try {
                Integer status = wo.getStatus();
                if (status == null) {
                    continue;
                }
                if ((status == WorkOrderService.ST_PENDING_DISPATCH || status == WorkOrderService.ST_PENDING_ACCEPT)
                        && SlaCalculator.isTimedOut(wo.getCreateTime(), now, wo.getSlaRespondMin())) {
                    escalate(wo, WorkOrderService.SLA_RESP_TIMEOUT, "响应超时");
                } else if ((status == WorkOrderService.ST_PROCESSING || status == WorkOrderService.ST_PENDING_VERIFY)
                        && SlaCalculator.isTimedOut(wo.getCreateTime(), now, wo.getSlaResolveMin())) {
                    escalate(wo, WorkOrderService.SLA_RESOLVE_TIMEOUT, "解决超时");
                }
            } catch (Exception e) {
                log.error("[sla] escalate failed for workOrderId={}", wo.getId(), e);
            }
        }
    }

    private void escalate(WorkOrder wo, int slaState, String reason) {
        int updated = workOrderMapper.update(null, new LambdaUpdateWrapper<WorkOrder>()
                .eq(WorkOrder::getId, wo.getId())
                .eq(WorkOrder::getEscalated, 0)
                .set(WorkOrder::getSlaState, slaState)
                .set(WorkOrder::getEscalated, 1));
        if (updated == 0) {
            // 已被并发的另一次扫描抢先打上,跳过日志/通知避免重复
            return;
        }

        WorkOrderLog l = new WorkOrderLog();
        l.setOrderId(wo.getId());
        l.setAction("SLA升级");
        l.setOperator("system");
        l.setContent(reason + ",工单号:" + wo.getCode());
        workOrderLogMapper.insert(l);

        try {
            String assignee = wo.getAssignee();
            // assignee 占位(#7 鉴权轮后接真实指派),为空则通知 system 兜底
            notificationService.sendInApp(null,
                    "工单SLA超时:" + reason,
                    "工单 " + wo.getCode() + " " + reason + ",请及时处理",
                    "workorder",
                    wo.getId());
            log.info("[sla] escalated workOrderId={} reason={} assignee={}", wo.getId(), reason,
                    assignee == null ? "system" : assignee);
        } catch (Exception e) {
            log.warn("[sla] notify failed for workOrderId={}", wo.getId(), e);
        }
    }
}
