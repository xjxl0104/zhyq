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
        jdbc.execute("CREATE TEMPORARY TABLE crm_customer(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,referrer_id BIGINT,deleted INT,version INT DEFAULT 1,assigned_warehouse_id BIGINT,intended_warehouse_id BIGINT)");
        jdbc.execute("CREATE TEMPORARY TABLE crm_warehouse(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,name VARCHAR(100),join_status INT,deleted INT,daily_capacity INT)");
        for(String filename:List.of("V129__warehouse_quote_rate_card.sql","V130__quote_advice.sql")){
            String source=new ClassPathResource("db/migration/"+filename).getContentAsString(StandardCharsets.UTF_8);
            jdbc.execute(source.substring(source.indexOf("CREATE TABLE")).replaceFirst("CREATE TABLE","CREATE TEMPORARY TABLE"));
        }
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
        jdbc.update("INSERT INTO crm_customer(id,tenant_id,project_id,referrer_id,deleted) VALUES(100,1,3,1,0),(101,1,3,4,0),(102,1,3,5,0)");
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

    @Test void singleProjectCorrectsHistoricalSubmissionAndMissingQueueWithoutCrossingScopes() throws Exception {
        jdbc.update("UPDATE crm_customer SET project_id=NULL WHERE id=100");
        jdbc.update("UPDATE crm_promoter SET project_id=NULL WHERE id=1");
        String token=(String)tx.execute(s->intake.markIntent(1,100)).get("token");
        var body=Map.<String,Object>of("agreed",true,"submissionId","unscoped_submission","requirements",Map.of(
            "weightRanges",List.of("0-0.3"),"couriers",List.of("圆通"),"materialProviders",List.of("客户提供")));
        tx.execute(s->intake.submit(token,body));
        assertEquals("project_required",intake.submittedDetails(100,1L,null).get("assessmentStatus"));
        assertEquals(Map.of("stage","project_required"),intake.admin(100,3));
        // Existing historical demand only has a unique park after the second park is removed.
        jdbc.update("UPDATE biz_project SET deleted=1 WHERE id=4");
        var migration=new ClassPathResource("db/migration/V132__scope_and_queue_submitted_quotes.sql").getContentAsString(StandardCharsets.UTF_8);
        for(String statement:migration.split(";")) if(!statement.isBlank()) jdbc.execute(statement);
        assertEquals(Map.of("stage","intent","submitted",true),intake.publicStatus(token));
        var detail=intake.submittedDetails(100,1L,3L);
        assertEquals("pending",detail.get("assessmentStatus"));
        assertEquals(body.get("requirements"),detail.get("requirements"));
        assertTrue(intake.submittedDetails(100,2L,3L).isEmpty());
        assertTrue(intake.submittedDetails(100,1L,4L).isEmpty());
        var json=new ObjectMapper();
        var rates=new com.zhyq.park.marketing.quote.rate.QuoteRateService(jdbc,json,null,null,null);
        var ai=org.mockito.Mockito.spy(new QuoteAdviceClient(json,"https://token.vvaix.com/v1/chat/completions","test","test",true));
        var advice=new QuoteAdviceService(jdbc,json,rates,ai);
        try {
            jdbc.update("INSERT INTO crm_warehouse VALUES(10,1,3,'样例仓',?,0,500)",com.zhyq.park.marketing.service.MktWarehouseOnboardingService.JS_ONLINE);
            advice.processNext();
            assertEquals("waiting",advice.view(100,3L).get("status"));
            jdbc.update("""
                INSERT INTO crm_quote_rate_card(warehouse_id,tenant_id,project_id,version,card_json)
                VALUES(10,1,3,1,'{"scopeNote":"隔离测试一般地区，年度协议价，偏远另议","fields":{},"services":{},"carriers":[{"name":"圆通","rates":{"0-0.3":2}}]}')
                """);
            tx.execute(s->advice.retry(100,3L));
            org.mockito.Mockito.doAnswer(call->{
                List<QuoteAdviceClient.Candidate> candidates=call.getArgument(0);
                return QuoteAdviceClient.suggestions(candidates,json.readTree("{\"shortlist\":[1],\"checks\":[\"scope\"]}"));
            }).when(ai).assess(org.mockito.ArgumentMatchers.anyList());
            advice.processNext();
            assertEquals("ready",advice.view(100,3L).get("status"));
            assertTrue(advice.view(100,3L).get("suggestions").toString().contains("单票总价待补齐"));
            tx.execute(s->advice.retry(100,3L));
            assertEquals("ready",advice.view(100,3L).get("status"));
        } finally {advice.stop();}
    }

    @Test void ambiguousProjectNeedsExplicitAdminChoiceAndPreservesTheCustomersOriginalCode() {
        jdbc.update("UPDATE crm_customer SET project_id=NULL WHERE id=100");
        jdbc.update("UPDATE crm_promoter SET project_id=NULL WHERE id=1");
        String code=(String)tx.execute(s->intake.markIntent(1,100)).get("token");
        var body=Map.<String,Object>of("agreed",true,"submissionId","unscoped_confirm_request","requirements",Map.of("invoice","开普通发票"));
        tx.execute(s->intake.submit(code,body));
        assertEquals("project_required",intake.admin(100,3).get("stage"));
        jdbc.update("INSERT INTO crm_warehouse(id,tenant_id,project_id) VALUES(10,1,3)");
        jdbc.update("UPDATE crm_customer SET intended_warehouse_id=10 WHERE id=100");
        assertThrows(BizException.class,()->tx.execute(s->intake.confirmProject(100,4)));
        assertNotNull(tx.execute(s->intake.confirmProject(100,3)).get("submittedAt"));
        assertEquals(Map.of("stage","intent","submitted",true),intake.publicStatus(code));
        assertEquals(new QuoteDemandSubmitted(100,1,3L),events.get(events.size()-1));
    }

    @Test void transactionalPublicSubmissionQueuesOnceAfterCommitAndNotOnRollback() {
        try(var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext()){
            context.registerBean(org.springframework.transaction.event.TransactionalEventListenerFactory.class);
            context.registerBean(QuoteAdviceService.class,()->new QuoteAdviceService(jdbc,new ObjectMapper(),null,null));
            context.refresh();
            var service=new QuoteIntakeService(jdbc,new ObjectMapper(),context);
            jdbc.update("UPDATE biz_project SET deleted=1 WHERE id=4");
            jdbc.update("UPDATE crm_promoter SET project_id=NULL WHERE id=1");
            jdbc.update("UPDATE crm_customer SET project_id=NULL WHERE id=100");
            var token=(String)tx.execute(s->service.markIntent(1,100)).get("token");
            assertEquals(3L,jdbc.queryForObject("SELECT project_id FROM crm_quote_intake WHERE customer_id=100",Long.class));
            var body=Map.<String,Object>of("agreed",true,"submissionId","transaction_test_001","requirements",Map.of("invoice","不开票"));
            tx.execute(s->{service.submit(token,body);s.setRollbackOnly();return null;});
            assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM crm_quote_advice",Long.class));
            tx.execute(s->service.submit(token,body));
            tx.execute(s->service.submit(token,body));
            assertEquals(1L,jdbc.queryForObject("SELECT generation FROM crm_quote_advice WHERE customer_id=100 AND project_id=3 AND tenant_id=1 AND status='pending'",Long.class));
        }
    }
}
