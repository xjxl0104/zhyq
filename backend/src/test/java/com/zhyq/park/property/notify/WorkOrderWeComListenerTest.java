package com.zhyq.park.property.notify;

import com.zhyq.park.property.entity.WorkOrder;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderWeComListenerTest {

    private WorkOrder order() {
        WorkOrder wo = new WorkOrder();
        wo.setCode("WO123");
        wo.setTitle("六楼A区女厕漏水");
        wo.setOrderType("报修");
        wo.setCategory("水电");
        wo.setUrgency(3);
        wo.setLocation("六楼A区");
        wo.setContact("林永棠");
        wo.setContactPhone("13925315830");
        wo.setCreateTime(LocalDateTime.of(2026, 9, 28, 9, 30));
        return wo;
    }

    @Test
    void buildsMarkdownWithKeyFields() {
        String md = WorkOrderWeComListener.buildMarkdown(order(), "五矿物业服务");

        assertThat(md).contains("【新报修工单】六楼A区女厕漏水")
                .contains("工单号:WO123")
                .contains("报修 / 水电")
                .contains("责任单位:五矿物业服务")
                .contains("联系人:林永棠 13925315830")
                .contains("2026-09-28 09:30");
        // 高紧急度标红,群里一眼分得出轻重
        assertThat(md).contains("<font color=\"warning\">高</font>");
    }

    @Test
    void skipsEmptyOptionalFields() {
        WorkOrder wo = new WorkOrder();
        wo.setCode("WO124");
        wo.setUrgency(1);

        String md = WorkOrderWeComListener.buildMarkdown(wo, null);

        assertThat(md).contains("无标题").contains("紧急度:低")
                .doesNotContain("责任单位").doesNotContain("联系人").doesNotContain("位置");
    }
}
