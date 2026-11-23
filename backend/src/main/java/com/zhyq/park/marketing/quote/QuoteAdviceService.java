package com.zhyq.park.marketing.quote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.marketing.quote.rate.QuoteRateService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteAdviceService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final QuoteRateService rates;
    private final QuoteAdviceClient ai;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r -> new Thread(r,"warehouse-quote-advice"));
    private final AtomicBoolean busy=new AtomicBoolean();

    // Commit the pending job together with the customer's submission. The worker can only see committed jobs.
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void submitted(QuoteDemandSubmitted event) {
        if(event.projectId()==null) return;
        db.update("""
            INSERT INTO crm_quote_advice(customer_id,tenant_id,project_id) VALUES(?,?,?)
            ON DUPLICATE KEY UPDATE tenant_id=VALUES(tenant_id), project_id=VALUES(project_id),
                generation=generation+1, status='pending', suggestions=NULL, updated_at=NOW()
            """,event.customerId(),event.tenantId(),event.projectId());
    }

    public Map<String,Object> view(long id,Long project) {
        // Check the caller's tenant, current customer attribution and active project before returning any cached advice.
        var comparison=rates.comparison(id,project);
        if(ai.candidates(comparison).isEmpty()) return reply("waiting","当前还没有可计算票价的方案，请先补齐客户需求或云仓价卡。",List.of());
        if(!ai.configured()) return reply("unconfigured","AI 评估服务尚未接通，可查看上方逐项报价。",List.of());
        var rows=db.queryForList("SELECT status,CAST(suggestions AS CHAR CHARACTER SET utf8mb4) AS suggestions FROM crm_quote_advice WHERE customer_id=? AND project_id=?",id,project);
        if(rows.isEmpty()) return reply("waiting","暂无本次需求的评估记录。",List.of());
        var row=rows.get(0);
        String state=String.valueOf(row.get("status"));
        if ("ready".equals(state)) {
            try { return reply("ready","按当前价卡评估，最终成交价以签约为准。",json.readValue(row.get("suggestions").toString(),new TypeReference<List<String>>(){})); }
            catch(Exception e) { return reply("failed","评估结果读取失败，请查看逐项报价。",List.of()); }
        }
        if ("failed".equals(state)) return reply(state,"本次 AI 调用未完成。可查看逐项报价，价卡更新后将重新评估。",List.of());
        if ("waiting".equals(state)) return reply(state,"等待云仓补齐可计算的报价。",List.of());
        return reply("pending","评估中，完成后会在这里显示。",List.of());
    }
    private static Map<String,Object> reply(String status,String message,List<String> suggestions) {
        return Map.of("status",status,"message",message,"suggestions",suggestions);
    }
    private record Job(long id,long tenant,long project,long generation) {}
    @Scheduled(initialDelay=20000,fixedDelay=5000)
    public void schedule() {
        if(ai.configured() && busy.compareAndSet(false,true)) worker.execute(() -> {
            try { processNext(); }
            catch(RuntimeException e) { log.warn("报价建议任务扫描失败 ({})",e.getClass().getSimpleName()); }
            finally { busy.set(false); }
        });
    }
    void processNext() {
        // A lost response may still be billable. Do not automatically repeat an ambiguous provider call.
        db.update("UPDATE crm_quote_advice SET status='failed',updated_at=NOW() WHERE status='running' AND updated_at < NOW() - INTERVAL 10 MINUTE");
        var jobs=db.query("SELECT customer_id,tenant_id,project_id,generation FROM crm_quote_advice WHERE status='pending' ORDER BY updated_at,customer_id LIMIT 1",
            (rs,i)->new Job(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4)));
        if(jobs.isEmpty()) return;
        var job=jobs.get(0);
        int claimed=db.update("UPDATE crm_quote_advice SET status='running',updated_at=NOW() WHERE customer_id=? AND generation=? AND status='pending'",job.id,job.generation);
        if(claimed!=1) return;
        try {
            var candidates=ai.candidates(rates.comparisonForSubmission(job.id,job.tenant,job.project));
            if(candidates.isEmpty()) { finish(job,"waiting",null); return; }
            finish(job,"ready",json.writeValueAsString(ai.assess(candidates)));
        } catch(Exception ex) {
            if(ex instanceof InterruptedException) Thread.currentThread().interrupt();
            finish(job,"failed",null);
            log.warn("报价建议任务未完成，客户编号 {} ({})",job.id,ex.getClass().getSimpleName());
        }
    }
    private void finish(Job job,String status,String suggestions) {
        db.update("UPDATE crm_quote_advice SET status=?,suggestions=?,updated_at=NOW() WHERE customer_id=? AND generation=? AND status='running'",
            status,suggestions,job.id,job.generation);
    }
    @PreDestroy public void stop(){ worker.shutdownNow(); }
}
