package com.zhyq.park.marketing.quote.rate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.wh.WhWorkspaceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the real joins, JSON serialization, and optimistic update using connection-local fixture tables. */
@EnabledIfEnvironmentVariable(named="QUOTE_IT_MYSQL_USER",matches=".+")
class QuoteRateServiceMysqlTest {
    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;
    private QuoteRateService service;
    private MktWarehouseMapper warehouses;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:mysql://127.0.0.1:13326/warehouse_http_it?serverTimezone=Asia/Shanghai",
                System.getenv("QUOTE_IT_MYSQL_USER"), System.getenv("QUOTE_IT_MYSQL_PASSWORD"), true);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TEMPORARY TABLE sys_user (tenant_id BIGINT,username VARCHAR(60),status INT,deleted INT)");
        jdbc.execute("CREATE TEMPORARY TABLE biz_project (id BIGINT,tenant_id BIGINT,deleted INT)");
        jdbc.execute("CREATE TEMPORARY TABLE crm_customer (id BIGINT,project_id BIGINT,tenant_id BIGINT,referrer_id BIGINT,deleted INT)");
        jdbc.execute("CREATE TEMPORARY TABLE crm_quote_intake (customer_id BIGINT,tenant_id BIGINT,project_id BIGINT,promoter_id BIGINT,requirements_json JSON,submitted_at DATETIME)");
        jdbc.execute("CREATE TEMPORARY TABLE crm_warehouse (id BIGINT,name VARCHAR(60),tenant_id BIGINT,project_id BIGINT,join_status INT,deleted INT,daily_capacity INT)");
        String migration = new ClassPathResource("db/migration/V129__warehouse_quote_rate_card.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(migration.replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE"));
        String adviceMigration = new ClassPathResource("db/migration/V130__quote_advice.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(adviceMigration.replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE"));
        jdbc.update("""
                INSERT INTO crm_quote_advice(customer_id,project_id,tenant_id,generation,status,suggestions)
                VALUES (1,11,1,1,'done',JSON_ARRAY()),(2,12,1,1,'done',JSON_ARRAY())
                """);
        jdbc.update("INSERT INTO sys_user VALUES (1,'quote_it_admin',1,0)");
        jdbc.update("INSERT INTO biz_project VALUES (11,1,0),(12,1,0),(22,2,0)");
        jdbc.update("INSERT INTO crm_customer VALUES (1,11,1,7,0),(2,12,1,7,0)");
        jdbc.update("INSERT INTO crm_quote_intake VALUES (1,1,11,7,JSON_OBJECT('couriers',JSON_ARRAY('中通')),NOW())");
        jdbc.update("""
                INSERT INTO crm_warehouse(id,name,tenant_id,project_id,join_status,deleted) VALUES
                (1,'本园已报价',1,11,5,0),(2,'本园无价卡',1,11,5,0),(3,'异园',1,12,5,0),
                (4,'异租户',2,22,5,0),(5,'未上线',1,11,4,0),(6,'已删除',1,11,5,1)
                """);
        warehouses = mock(MktWarehouseMapper.class);
        service = new QuoteRateService(jdbc, json, warehouses, mock(WhWorkspaceService.class), mock(MktAuditService.class));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("quote_it_admin", null, List.of()));
    }

    @AfterEach void close() {
        SecurityContextHolder.clearContext();
        if (dataSource != null) dataSource.destroy();
    }

    private MktWarehouse warehouse(long project, int status) {
        var result = new MktWarehouse();
        result.setId(1L);
        result.setName("本园已报价");
        result.setTenantId(1L);
        result.setProjectId(project);
        result.setJoinStatus(status);
        return result;
    }

    @Test void versionedCardRoundTripAndCrossProjectAccess() throws Exception {
        var w = warehouse(11, 5);
        when(warehouses.selectForUpdate(1L)).thenReturn(w);
        when(warehouses.selectById(1L)).thenReturn(w);
        var body = json.readTree("""
            {"scopeNote":"一般地区","fields":{"packSingle":0.3},"carriers":[{"name":"中通","rates":{"0-0.3":1.5}}],"services":{}}
            """);
        assertThat(service.adminCard(1L,11L)).containsEntry("version",0L);
        assertThat(service.saveAdmin(1L,11L,new QuoteRateService.Update(0L,body))).containsEntry("version",1L);
        assertThat(service.adminCard(1L,11L)).containsEntry("card",QuoteCard.parse(body));
        assertThat(service.saveAdmin(1L,11L,new QuoteRateService.Update(1L,body))).containsEntry("version",2L);
        assertThatExceptionOfType(BizException.class).isThrownBy(() -> service.saveAdmin(1L,11L,new QuoteRateService.Update(1L,body)))
                .extracting(BizException::getCode).isEqualTo(409);
        assertThatExceptionOfType(BizException.class).isThrownBy(() -> service.adminCard(1L,12L)).extracting(BizException::getCode).isEqualTo(404);
        assertThatExceptionOfType(BizException.class).isThrownBy(() -> service.saveAdmin(1L,22L,new QuoteRateService.Update(2L,body)))
                .extracting(BizException::getCode).isEqualTo(403);
        when(warehouses.selectForUpdate(1L)).thenReturn(warehouse(11,4));
        assertThatExceptionOfType(BizException.class).isThrownBy(() -> service.saveAdmin(1L,11L,new QuoteRateService.Update(2L,body)))
                .extracting(BizException::getCode).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT version FROM crm_quote_rate_card WHERE warehouse_id=1",Long.class)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT generation FROM crm_quote_advice WHERE customer_id=1 AND status='pending' AND suggestions IS NULL", Long.class)).isEqualTo(3L);
        assertThat(jdbc.queryForObject("SELECT generation FROM crm_quote_advice WHERE customer_id=2 AND status='done'", Long.class)).isEqualTo(1L);
    }

    @Test @SuppressWarnings("unchecked")
    void comparisonChecksSubmittedCustomerAndLimitsEveryJoinedRowToScope() {
        jdbc.update("INSERT INTO crm_quote_rate_card(warehouse_id,tenant_id,project_id,version,card_json) VALUES (1,2,22,31,JSON_OBJECT())");
        var result = service.comparison(1L,11L);
        var rows = (List<Map<String,Object>>) result.get("warehouses");
        assertThat(rows).extracting(r -> r.get("warehouseId")).containsExactlyInAnyOrder(1L,2L);
        assertThat(rows).allSatisfy(r -> assertThat(r).containsEntry("version",0L));
        var offers = (List<Map<String,Object>>) rows.get(0).get("offers");
        assertThat(offers).singleElement().satisfies(o -> assertThat(o).containsEntry("carrier","中通").containsEntry("status","missing"));
        assertThatExceptionOfType(BizException.class).isThrownBy(() -> service.comparisonForSubmission(1L,2L,22L))
                .extracting(BizException::getCode).isEqualTo(404);
        assertThatExceptionOfType(BizException.class).isThrownBy(() -> service.comparisonForSubmission(2L,1L,12L))
                .extracting(BizException::getCode).isEqualTo(409);
        jdbc.update("UPDATE crm_customer SET referrer_id=8 WHERE id=1");
        assertThat(service.comparison(1L,11L)).isEqualTo(result);
        jdbc.update("UPDATE crm_customer SET deleted=1 WHERE id=1");
        assertThatExceptionOfType(BizException.class).isThrownBy(() -> service.comparison(1L,11L))
                .extracting(BizException::getCode).isEqualTo(404);
    }
}
