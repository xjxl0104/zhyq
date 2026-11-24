package com.zhyq.park.marketing.service;

import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.marketing.entity.MktWarehouse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.Map;

/** Physical outbound tickets are independent of partner commission eligibility. */
@Service
@RequiredArgsConstructor
public class WarehouseShipmentFacts {
    private final NamedParameterJdbcTemplate jdbc;

    private static String facts(boolean oneWarehouse) {
        String snapshotScope = oneWarehouse ? " AND v.warehouse_id=:warehouseId " : "";
        String orderScope = oneWarehouse ? " AND COALESCE(o.warehouse_id,c.warehouse_id)=:warehouseId " : "";
        return """
            WITH daily_sources AS (
                SELECT v.warehouse_id,v.customer_id,v.stat_date AS ship_day,SUM(v.consign_num) AS snapshot_volume, 0 AS order_volume
                FROM crm_yunshan_daily_volume v
                WHERE v.tenant_id=:tenantId AND v.deleted=0 AND v.consign_num>=0
                  AND v.stat_date>=:startDate AND v.stat_date<:endExclusive
            """ + snapshotScope + """
                GROUP BY v.warehouse_id,v.customer_id,v.stat_date
                UNION ALL
                SELECT COALESCE(o.warehouse_id,c.warehouse_id),o.customer_id,DATE(o.event_time),
                       NULL,SUM(GREATEST(COALESCE(o.commission_order_count,1),0))
                FROM crm_referral_order o
                LEFT JOIN crm_service_contract c ON c.id=o.source_id AND c.customer_id=o.customer_id
                  AND c.tenant_id=o.tenant_id AND c.deleted=0
                WHERE o.tenant_id=:tenantId AND o.deleted=0 AND o.source_type=2 AND o.status=2
                  AND COALESCE(o.source_no,'') NOT LIKE 'YS-%'
                  AND o.event_time>=:startDate AND o.event_time<:endExclusive
            """ + orderScope + """
                GROUP BY COALESCE(o.warehouse_id,c.warehouse_id),o.customer_id,DATE(o.event_time)
            ), facts AS (
                SELECT warehouse_id,customer_id,ship_day,COALESCE(MAX(snapshot_volume),SUM(order_volume)) AS volume
                FROM daily_sources GROUP BY warehouse_id,customer_id,ship_day
            )
            """;
    }

    static String overviewSql() {
        return facts(false) + """
            SELECT f.warehouse_id AS warehouseId,f.customer_id,SUM(f.volume) AS volume
            FROM facts f
            JOIN crm_customer cu ON cu.id=f.customer_id AND cu.tenant_id=:tenantId AND cu.deleted=0
            JOIN crm_warehouse w ON w.id=f.warehouse_id AND w.tenant_id=:tenantId AND w.deleted=0
            GROUP BY f.warehouse_id,f.customer_id
            """;
    }

    public Map<String,Object> month(MktWarehouse warehouse, Long salespersonId, LocalDate today) {
        var params = new MapSqlParameterSource()
                .addValue("tenantId", warehouse.getTenantId()==null ? MyMetaObjectHandler.DEFAULT_TENANT_ID : warehouse.getTenantId())
                .addValue("warehouseId",warehouse.getId()).addValue("today",today.toString())
                .addValue("startDate",today.withDayOfMonth(1).toString()).addValue("endExclusive",today.plusDays(1).toString());
        String scope = "";
        if (warehouse.getProjectId()!=null) {
            scope += " AND cu.project_id=:projectId";
            params.addValue("projectId",warehouse.getProjectId());
        }
        if (salespersonId!=null) {
            scope += " AND cu.assigned_warehouse_id=:warehouseId AND cu.warehouse_salesperson_id=:salespersonId";
            params.addValue("salespersonId",salespersonId);
        }
        return jdbc.queryForMap(facts(true) + """
            SELECT COALESCE(SUM(CASE WHEN f.ship_day=:today THEN f.volume ELSE 0 END),0) AS todayOrders,
                   COALESCE(SUM(f.volume),0) AS monthOrders
            FROM facts f JOIN crm_customer cu ON cu.id=f.customer_id AND cu.deleted=0 AND cu.tenant_id=:tenantId
            WHERE f.warehouse_id=:warehouseId
            """ + scope, params);
    }
}
