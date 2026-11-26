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
        var rows=db.queryForList("SELECT status,CAST(suggestions AS CHAR CHARACTER SET utf8mb4) AS suggestions FROM crm_quote_advice WHERE customer_id=? AND project_id=?",id,project);
        if(rows.isEmpty()) return reply("failed","该需求尚无评估任务，可重新发起。",List.of());
        var row=rows.get(0);
        String state=String.valueOf(row.get("status"));
        if("pending".equals(state) || "running".equals(state)) return reply("pending","评估中，完成后会在这里显示。",List.of());
        if(ai.candidates(comparison).isEmpty()) {
            com.fasterxml.jackson.databind.JsonNode warehouses=json.valueToTree(comparison.get("warehouses"));
            if(warehouses.isEmpty()) return reply("waiting","本园区尚无已上线的加盟云仓。",List.of());
            if(java.util.stream.StreamSupport.stream(warehouses.spliterator(),false).noneMatch(w->w.path("version").asLong()>0))
                return reply("waiting","已匹配本园区上线云仓，但均未录入价卡。云仓保存价卡后将自动重新评估。",List.of());
            return reply("waiting","当前没有与需求匹配的可比较价格。请按下方各仓缺项补充资料，暂缓推荐。",List.of());
        }
        if(!ai.configured()) return reply("unconfigured","AI 评估服务尚未接通，可查看上方逐项报价。",List.of());
        if ("ready".equals(state) || "partial".equals(state)) {
            try { return reply(state,"partial".equals(state)?"报告已生成；目前仅有条件报价、缺项或产能限制。请核实后说明人工分配原因。":"基于当前需求与云仓价卡生成，供运营手动分配时参考。",json.readValue(row.get("suggestions").toString(),new TypeReference<List<String>>(){})); }
            catch(Exception e) { return reply("failed","评估结果读取失败，请查看逐项报价。",List.of()); }
        }
        if ("failed".equals(state)) return reply(state,"本次 AI 评估未完成，可以重试。各仓逐项报价仍可查看。",List.of());
        if ("waiting".equals(state)) return reply(state,"等待云仓补齐可计算的报价。",List.of());
        return reply("pending","评估中，完成后会在这里显示。",List.of());
    }

    @org.springframework.transaction.annotation.Transactional
    public Map<String,Object> retry(long id,Long project) {
        long tenant=QuoteProjectAccess.requireTenant(db,project);
        rates.comparisonForSubmission(id,tenant,project);
        // No-op for concurrent clicks or a job already completed by another operator.
        db.update("""
            INSERT INTO crm_quote_advice(customer_id,tenant_id,project_id) VALUES(?,?,?)
            ON DUPLICATE KEY UPDATE status=IF(tenant_id=VALUES(tenant_id) AND project_id=VALUES(project_id)
                                               AND status IN ('failed','waiting'),'pending',status)
            """,id,tenant,project);
        return view(id,project);
    }
    private static Map<String,Object> reply(String status,String message,List<String> suggestions) {
        return Map.of("status",status,"message",message,"suggestions",suggestions);
    }
    private record Job(long id,long tenant,long project,long generation) {}
    @Scheduled(initialDelay=20000,fixedDelay=5000)
    public void schedule() {
        if(busy.compareAndSet(false,true)) worker.execute(() -> {
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
            if(!ai.configured()) { finish(job,"failed",null); return; }
            finish(job,QuoteAdviceClient.hasAssignableOffer(candidates)?"ready":"partial",json.writeValueAsString(ai.assess(candidates)));
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
