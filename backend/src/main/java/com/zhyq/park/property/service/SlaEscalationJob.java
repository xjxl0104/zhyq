package com.zhyq.park.property.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.property.entity.WorkOrder;
import com.zhyq.park.property.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** 每十分钟扫描在办工单，首次升级或补送旧版尚未投递的同类超时通知。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlaEscalationJob {

    private final WorkOrderMapper workOrderMapper;
    private final SlaEscalationWriter writer;

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
                        .isNull(WorkOrder::getSlaDeliveryConfirmedAt)
                        .and(w -> w.eq(WorkOrder::getEscalated, 0).isNull(WorkOrder::getSlaState)
                                .or(legacy -> legacy.eq(WorkOrder::getEscalated, 1)
                                        .in(WorkOrder::getSlaState, WorkOrderService.SLA_RESP_TIMEOUT,
                                                WorkOrderService.SLA_RESOLVE_TIMEOUT))));

        for (WorkOrder wo : actives) {
            try {
                Integer status = wo.getStatus();
                if (status == null) {
                    continue;
                }
                if ((status == WorkOrderService.ST_PENDING_DISPATCH || status == WorkOrderService.ST_PENDING_ACCEPT)
                        && (wo.getSlaState() == null || wo.getSlaState() == WorkOrderService.SLA_RESP_TIMEOUT)
                        && SlaCalculator.isTimedOut(wo.getCreateTime(), now, wo.getSlaRespondMin())) {
                    writer.escalate(wo, WorkOrderService.SLA_RESP_TIMEOUT, "响应超时");
                } else if ((status == WorkOrderService.ST_PROCESSING || status == WorkOrderService.ST_PENDING_VERIFY)
                        && (wo.getSlaState() == null || wo.getSlaState() == WorkOrderService.SLA_RESOLVE_TIMEOUT)
                        && SlaCalculator.isTimedOut(wo.getCreateTime(), now, wo.getSlaResolveMin())) {
                    writer.escalate(wo, WorkOrderService.SLA_RESOLVE_TIMEOUT, "解决超时");
                }
            } catch (Exception e) {
                log.error("[sla] escalate failed for workOrderId={}", wo.getId(), e);
            }
        }
    }

}
