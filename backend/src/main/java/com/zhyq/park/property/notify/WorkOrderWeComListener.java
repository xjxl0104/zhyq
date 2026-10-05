package com.zhyq.park.property.notify;

import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.property.entity.ResponsibleUnit;
import com.zhyq.park.property.entity.WorkOrder;
import com.zhyq.park.property.mapper.ResponsibleUnitMapper;
import com.zhyq.park.pur.entity.Supplier;
import com.zhyq.park.pur.mapper.SupplierMapper;
import com.zhyq.park.property.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;

/**
 * 新建工单后往企业微信群推一条报修信息。
 *
 * <p>走 AFTER_COMMIT:工单确实落库了才通知,避免事务回滚后群里已经喊了人。
 * 整段包在 try 里 —— 通知失败不能反过来影响建单。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOrderWeComListener {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String[] URGENCY = {"", "低", "中", "高"};

    private final WorkOrderMapper workOrderMapper;
    private final ResponsibleUnitMapper unitMapper;
    private final SupplierMapper supplierMapper;
    private final WeComBotNotifier notifier;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWorkOrderCreated(DomainEvent.WorkOrderCreated event) {
        try {
            if (!notifier.enabled()) {
                return;
            }
            WorkOrder wo = workOrderMapper.selectById(event.workOrderId());
            if (wo == null) {
                return;
            }
            notifier.sendMarkdown(buildMarkdown(wo, unitName(wo.getSupplierId(), wo.getResponsibleUnitId())));
        } catch (Exception e) {
            log.warn("[wecom] 工单 {} 推送失败", event.workOrderId(), e);
        }
    }

    private String unitName(Long supplierId, Long unitId) {
        if (supplierId != null) {
            Supplier supplier = supplierMapper.selectById(supplierId);
            if (supplier != null) return supplier.getName();
        }
        if (unitId == null) {
            return null;
        }
        ResponsibleUnit unit = unitMapper.selectById(unitId);
        return unit == null ? null : unit.getName();
    }

    /** 群消息正文:标题加粗 + 关键信息逐行,手机上一眼能看完 */
    static String buildMarkdown(WorkOrder wo, String unitName) {
        StringBuilder sb = new StringBuilder();
        sb.append("**【新报修工单】").append(nvl(wo.getTitle(), "无标题")).append("**\n");
        sb.append("> 工单号:").append(nvl(wo.getCode(), "-")).append("\n");
        sb.append("> 类型:").append(nvl(wo.getOrderType(), "报修"));
        if (wo.getCategory() != null && !wo.getCategory().isBlank()) {
            sb.append(" / ").append(wo.getCategory());
        }
        sb.append("\n");
        sb.append("> 紧急度:").append(urgencyText(wo.getUrgency())).append("\n");
        if (wo.getLocation() != null && !wo.getLocation().isBlank()) {
            sb.append("> 位置:").append(wo.getLocation()).append("\n");
        }
        if (unitName != null) {
            sb.append("> 责任单位:").append(unitName).append("\n");
        }
        if (wo.getContact() != null && !wo.getContact().isBlank()) {
            sb.append("> 联系人:").append(wo.getContact());
            if (wo.getContactPhone() != null && !wo.getContactPhone().isBlank()) {
                sb.append(" ").append(wo.getContactPhone());
            }
            sb.append("\n");
        }
        if (wo.getRemark() != null && !wo.getRemark().isBlank()) {
            sb.append("> 备注:").append(wo.getRemark()).append("\n");
        }
        if (wo.getCreateTime() != null) {
            sb.append("> 报修时间:").append(wo.getCreateTime().format(TIME));
        }
        return sb.toString();
    }

    /** 高紧急度加红,群里一眼分得出轻重 */
    private static String urgencyText(Integer urgency) {
        int u = urgency == null ? 2 : Math.max(0, Math.min(3, urgency));
        String text = URGENCY[u].isEmpty() ? "中" : URGENCY[u];
        return u == 3 ? "<font color=\"warning\">" + text + "</font>" : text;
    }

    private static String nvl(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }
}
