package com.zhyq.park.property.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.property.entity.WorkOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WorkOrderMapper extends BaseMapper<WorkOrder> {

    // The previous transactional writer logged “已通知” only after the inbox and todo were persisted.
    // A read/completed/soft-deleted todo is still evidence that a notice was delivered.
    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM pm_work_order_log
                WHERE order_id = #{orderId} AND tenant_id = #{tenantId}
                  AND action = 'SLA升级' AND operator = 'system'
                  AND content LIKE CONCAT(#{reason}, '，已通知 _%')
            ) OR EXISTS (
                SELECT 1 FROM sys_todo
                WHERE biz_type = 'workorder' AND biz_id = #{orderId} AND tenant_id = #{tenantId}
                  AND owner IS NOT NULL AND owner != ''
                  AND title = CONCAT('工单 ', #{code}, ' ', #{reason}, '，请及时处理')
            )
            """)
    boolean hasPreviousSlaDelivery(@Param("orderId") long orderId, @Param("tenantId") long tenantId,
                                   @Param("code") String code, @Param("reason") String reason);
}
