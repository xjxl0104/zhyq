package com.zhyq.park.marketing.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Connection-local MySQL tables shadow real tables; runs without writing application records. */
@EnabledIfEnvironmentVariable(named="QUOTE_IT_MYSQL_USER",matches=".+")
class QuoteIntakeServiceMysqlTest {
    private SingleConnectionDataSource ds;
    private JdbcTemplate jdbc;
    private QuoteIntakeService intake;
    private TransactionTemplate tx;
    private final List<Object> events=new ArrayList<>();
    @BeforeEach void init() throws Exception {
        ds=new SingleConnectionDataSource("jdbc:mysql://127.0.0.1:13326/warehouse_http_it?serverTimezone=Asia/Shanghai",
                System.getenv("QUOTE_IT_MYSQL_USER"),System.getenv("QUOTE_IT_MYSQL_PASSWORD"),true);
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TEMPORARY TABLE crm_customer(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,referrer_id BIGINT,deleted INT)");
        jdbc.execute("CREATE TEMPORARY TABLE crm_promoter(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,status INT,deleted INT)");
        jdbc.execute("CREATE TEMPORARY TABLE biz_project(id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted INT)");
        jdbc.execute("CREATE TEMPORARY TABLE sys_user(username VARCHAR(50),tenant_id BIGINT,status INT,deleted INT)");
        jdbc.update("INSERT INTO sys_user VALUES('operator',1,1,0)");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("operator",null,List.of()));
        var migration=new ClassPathResource("db/migration/V128__customer_quote_intake.sql").getContentAsString(StandardCharsets.UTF_8);
        String ddl=migration.substring(migration.indexOf("CREATE TABLE")).replaceFirst("CREATE TABLE","CREATE TEMPORARY TABLE");
        jdbc.execute(ddl);
        jdbc.update("INSERT INTO biz_project VALUES(3,1,0),(4,1,0)");
        jdbc.update("INSERT INTO crm_promoter VALUES(1,1,3,1,0),(2,1,3,1,0),(4,1,4,1,0),(5,2,3,1,0)");
        jdbc.update("INSERT INTO crm_customer VALUES(100,1,3,1,0),(101,1,3,4,0),(102,1,3,5,0)");
        intake=new QuoteIntakeService(jdbc,new ObjectMapper(),events::add);
        tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
    }
    @AfterEach void close() {
        SecurityContextHolder.clearContext();
        if(ds!=null) ds.destroy();
    }
    @Test void reportPromoteShareSubmitRetryAndReadDemandInProject() {
        assertEquals(Map.of("stage","lead"),intake.partner(1,100));
        assertThrows(BizException.class,()->intake.partnerToken(1,100));
        String token=(String)tx.execute(s->intake.markIntent(1,100)).get("token");
        assertEquals(32,token.length());
        assertEquals(token,tx.execute(s->intake.markIntent(1,100)).get("token"));
        assertEquals(Map.of("stage","intent","submitted",false),intake.publicStatus(token));
        var raw=new LinkedHashMap<String,Object>(); raw.put("agreed",true);raw.put("submissionId","request_0123456789");
        raw.put("requirements",Map.of("services",List.of("退件回仓上架","成品组套"),"invoice","开普通发票"));
        var receipt=tx.execute(s->intake.submit(token,raw));
        assertEquals(Map.of("submitted",true),receipt);
        assertEquals(Map.of("stage","intent","submitted",true),intake.publicStatus(token));
        assertEquals(receipt,tx.execute(s->intake.submit(token,raw)));
        assertEquals(List.of(new QuoteDemandSubmitted(100L,1L,3L)),events);
        raw.put("requirements",Map.of("invoice","不开票"));
        assertThrows(BizException.class,()->tx.execute(s->intake.submit(token,raw)));
        raw.put("submissionId","another_0123456789");
        assertThrows(BizException.class,()->tx.execute(s->intake.submit(token,raw)));
        var admin=intake.admin(100,3);
        assertFalse(admin.containsKey("token"));
        assertEquals("开普通发票",((Map<?,?>)admin.get("requirements")).get("invoice"));
        assertTrue(intake.partner(1,100).containsKey("submittedAt"));
        assertThrows(BizException.class,()->intake.admin(100,4));
        jdbc.update("UPDATE sys_user SET tenant_id=2");
        assertThrows(BizException.class,()->intake.admin(100,3));
    }
    @Test void ownershipAndLiveTenantProjectAreEnforcedAndFailClosedOnDeletion() {
        assertThrows(BizException.class,()->intake.partner(2,100));
        assertThrows(BizException.class,()->intake.markIntent(4,101));
        assertThrows(BizException.class,()->intake.markIntent(5,102));
        String old=(String)tx.execute(s->intake.markIntent(1,100)).get("token");
        jdbc.update("UPDATE crm_customer SET referrer_id=2 WHERE id=100");
        assertThrows(BizException.class,()->intake.publicStatus(old));
        assertThrows(BizException.class,()->intake.partnerToken(1,100));
        String fresh=(String)tx.execute(s->intake.markIntent(2,100)).get("token");
        assertNotEquals(old,fresh);
        assertThrows(BizException.class,()->intake.publicStatus(old));
        jdbc.update("UPDATE crm_promoter SET status=0 WHERE id=2");
        assertThrows(BizException.class,()->intake.publicStatus(fresh));
        jdbc.update("UPDATE crm_promoter SET status=1 WHERE id=2");
        jdbc.update("UPDATE crm_customer SET deleted=1 WHERE id=100");
        assertThrows(BizException.class,()->intake.publicStatus(fresh));
    }
}
