package com.zhyq.park.marketing.mapper;

import com.zhyq.park.marketing.entity.MktErpEventInbox;
import com.zhyq.park.marketing.entity.MktErpUnmapped;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
import java.util.List;

/** EXISTS keeps the query bounded in size even when a period contains many orders. */
public interface ErpReconciliationQueryMapper {
    String PERIOD = """
        ((i.occurred_at >= #{start} AND i.occurred_at < #{end}) OR EXISTS (
          SELECT 1 FROM crm_referral_order o WHERE o.deleted = 0 AND o.source_type = 2
          AND o.source_scope = i.warehouse_id AND o.source_no = i.order_no
          AND o.tenant_id = i.tenant_id
          AND o.event_time >= #{start} AND o.event_time < #{end}))
        """;

    @Select("SELECT i.* FROM crm_erp_event_inbox i WHERE i.deleted = 0 AND i.warehouse_id = #{warehouseId} AND " + PERIOD + " ORDER BY i.id")
    List<MktErpEventInbox> periodEvents(@Param("warehouseId") Long warehouseId,
                                       @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
        SELECT u.* FROM crm_erp_unmapped u JOIN crm_erp_event_inbox i
        ON i.id = u.inbox_id AND i.warehouse_id = u.warehouse_id AND i.tenant_id = u.tenant_id AND i.deleted = 0
        WHERE u.deleted = 0 AND u.status = 'OPEN' AND u.warehouse_id = #{warehouseId} AND
        """ + PERIOD + " ORDER BY u.id")
    List<MktErpUnmapped> periodUnmapped(@Param("warehouseId") Long warehouseId,
                                       @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
