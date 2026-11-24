package com.zhyq.park.marketing.service;

import com.zhyq.park.marketing.entity.MktWarehouse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="QUOTE_IT_MYSQL_USER",matches=".+")
class WarehouseShipmentFactsMysqlTest {
    @Test void snapshotsAndConfirmedTicketsAreScopedAndNeverDoubleCounted() {
        var ds = new SingleConnectionDataSource("jdbc:mysql://127.0.0.1:13326/warehouse_http_it?serverTimezone=Asia/Shanghai",
                System.getenv("QUOTE_IT_MYSQL_USER"),System.getenv("QUOTE_IT_MYSQL_PASSWORD"),true);
        try {
            var jdbc = new JdbcTemplate(ds);
            jdbc.execute("CREATE TEMPORARY TABLE crm_customer (id BIGINT,tenant_id BIGINT,deleted INT,project_id BIGINT,assigned_warehouse_id BIGINT,warehouse_salesperson_id BIGINT)");
            jdbc.execute("CREATE TEMPORARY TABLE crm_warehouse (id BIGINT,tenant_id BIGINT,deleted INT)");
            jdbc.execute("CREATE TEMPORARY TABLE crm_service_contract (id BIGINT,customer_id BIGINT,tenant_id BIGINT,deleted INT,warehouse_id BIGINT)");
            jdbc.execute("CREATE TEMPORARY TABLE crm_yunshan_daily_volume (customer_id BIGINT,warehouse_id BIGINT,tenant_id BIGINT,deleted INT,stat_date DATE,consign_num BIGINT)");
            jdbc.execute("CREATE TEMPORARY TABLE crm_referral_order (customer_id BIGINT,warehouse_id BIGINT,tenant_id BIGINT,deleted INT,event_time DATETIME,source_type INT,status INT,source_id BIGINT,source_no VARCHAR(20),commission_order_count BIGINT)");
            jdbc.update("INSERT INTO crm_customer VALUES (1,1,0,8,5,9),(2,1,0,8,5,10),(3,2,0,8,7,11),(4,1,0,8,6,12)");
            jdbc.update("INSERT INTO crm_warehouse VALUES (5,1,0),(6,1,0),(7,2,0)");
            jdbc.update("INSERT INTO crm_yunshan_daily_volume VALUES (1,5,1,0,'2026-10-12',26),(1,5,1,0,'2026-10-12',4),(3,7,2,0,'2026-10-12',800),(4,6,1,0,'2026-10-12',100)");
            jdbc.update("""
                      INSERT INTO crm_referral_order VALUES
                      (1,5,1,0,'2026-10-12',2,2,NULL,'manual-duplicate',12),
                      (1,5,1,0,'2026-10-11',2,2,NULL,'manual-prior',2),
                      (2,5,1,0,'2026-10-12',2,2,NULL,'manual-staff2',7),
                      (2,5,1,0,'2026-10-12',2,2,NULL,'YS-rollup',99),
                      (2,5,1,0,'2026-10-12',2,3,NULL,'returned',10)
                      """);
            var named = new NamedParameterJdbcTemplate(jdbc);
            var facts = new WarehouseShipmentFacts(named);
            var wh = new MktWarehouse();wh.setId(5L);wh.setTenantId(1L);wh.setProjectId(8L);
            var day = LocalDate.parse("2026-10-12");
            assertThat(facts.month(wh,null,day)).containsEntry("todayOrders",new BigDecimal("37")).containsEntry("monthOrders",new BigDecimal("39"));
            assertThat(facts.month(wh,9L,day)).containsEntry("todayOrders",new BigDecimal("30")).containsEntry("monthOrders",new BigDecimal("32"));
            var rows=named.queryForList(WarehouseShipmentFacts.overviewSql(),Map.of("tenantId",1L,"startDate","2026-10-01","endExclusive","2026-10-13"));
            assertThat(rows).hasSize(3);
            assertThat(rows.stream().map(r->new BigDecimal(r.get("volume").toString())).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("139");
        } finally {
            ds.destroy();
        }
    }
}
