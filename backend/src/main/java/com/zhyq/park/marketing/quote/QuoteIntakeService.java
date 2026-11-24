package com.zhyq.park.marketing.quote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class QuoteIntakeService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;

    private static final String ACTIVE_OWNER = """
        JOIN crm_promoter p ON p.id=c.referrer_id AND p.tenant_id=c.tenant_id
          AND p.deleted=0 AND p.status=1 AND (p.project_id IS NULL OR c.project_id IS NULL OR p.project_id=c.project_id)
        """;
    private static final String SAME_SCOPE = "q.customer_id=c.id AND q.tenant_id=c.tenant_id AND q.project_id <=> c.project_id";
    private static final String PARTNER = """
        SELECT c.id,c.tenant_id,c.project_id,q.promoter_id,q.token,
          CAST(q.requirements_json AS CHAR CHARACTER SET utf8mb4) AS requirements_json,q.submitted_at
        FROM crm_customer c
        """ + ACTIVE_OWNER + " LEFT JOIN crm_quote_intake q ON " + SAME_SCOPE
        + " WHERE c.id=? AND c.referrer_id=? AND c.deleted=0";

    public Map<String,Object> partner(long promoterId,long customerId) {
        var row = one(PARTNER,customerId,promoterId);
        return view(row,Objects.equals(id(row,"promoter_id"),promoterId));
    }

    /** Customer access is checked by the partner or warehouse detail endpoint before this projection. */
    public Map<String,Object> submittedDetails(long customerId, Long tenantId, Long projectId) {
        var rows=jdbc.queryForList("""
            SELECT q.submitted_at, CAST(q.requirements_json AS CHAR CHARACTER SET utf8mb4) AS requirements_json,
                   a.status AS assessment_status
              FROM crm_quote_intake q
              LEFT JOIN crm_quote_advice a ON a.customer_id=q.customer_id AND a.tenant_id=q.tenant_id AND a.project_id=q.project_id
             WHERE q.customer_id=? AND q.tenant_id=? AND q.project_id <=> ? AND q.submitted_at IS NOT NULL
            """,customerId,tenantId,projectId);
        if (rows.isEmpty()) return Map.of();
        var row=rows.get(0);
        Map<String,Object> result=view(row,false);
        result.remove("stage");
        result.put("assessmentStatus",projectId == null ? "project_required" : Objects.toString(row.get("assessment_status"),"pending"));
        return result;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> markIntent(long promoterId,long customerId) {
        // Lock the actual customer even if no quote row exists yet. Two clicks
        // on a new lead must yield the same code.
        one("SELECT c.id FROM crm_customer c "+ACTIVE_OWNER+
            " WHERE c.id=? AND c.referrer_id=? AND c.deleted=0 FOR UPDATE",customerId,promoterId);
        var row=one(PARTNER,customerId,promoterId);
        if (row.get("project_id")==null) {
            Long resolved=soleProject(id(row,"tenant_id"));
            if(resolved!=null && scopeCustomer(customerId,id(row,"tenant_id"),resolved)) row=one(PARTNER,customerId,promoterId);
        }
        if (row.get("token")==null) {
            var existing=jdbc.queryForList("SELECT tenant_id,project_id FROM crm_quote_intake WHERE customer_id=?",customerId);
            if (!existing.isEmpty()) throw new BizException(409,"该客户的园区已变更，请运营核对报价资料");
            jdbc.update("""
                INSERT INTO crm_quote_intake(customer_id,tenant_id,project_id,promoter_id,token)
                VALUES (?,?,?,?,?)
                """,customerId,id(row,"tenant_id"),id(row,"project_id"),promoterId,newToken());
        } else if (!Objects.equals(id(row,"promoter_id"),promoterId)) {
            // Rotate the previous referrer's capability when ownership changes.
            // The demand remains attached to this customer in the same project.
            jdbc.update("UPDATE crm_quote_intake SET token=?,promoter_id=? WHERE customer_id=?",newToken(),promoterId,customerId);
        }
        return partner(promoterId,customerId);
    }

    public String partnerToken(long promoterId,long customerId) {
        Map<String,Object> row=one(PARTNER,customerId,promoterId);
        if (row.get("token")==null || !Objects.equals(id(row,"promoter_id"),promoterId))
            throw new BizException(409,"请先标记为意向客户");
        return row.get("token").toString();
    }

    public Map<String,Object> admin(long customerId,long projectId) {
        long tenant=QuoteProjectAccess.requireTenant(jdbc,projectId);
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM crm_customer WHERE id=? AND tenant_id=? AND project_id IS NULL AND deleted=0)",Boolean.class,customerId,tenant)))
            return Map.of("stage","project_required");
        var row=one("""
            SELECT q.token,CAST(q.requirements_json AS CHAR CHARACTER SET utf8mb4) AS requirements_json,
              q.submitted_at FROM crm_customer c
            JOIN biz_project s ON s.id=c.project_id AND s.tenant_id=c.tenant_id AND s.deleted=0
            LEFT JOIN crm_quote_intake q ON
            """+SAME_SCOPE+" WHERE c.id=? AND c.project_id=? AND c.tenant_id=? AND c.deleted=0",
            customerId,projectId,tenant);
        return view(row,false);
    }

    public Map<String,Object> publicStatus(String token) {
        var row=publicRow(token,false);
        return Map.of("stage","intent","submitted",row.get("submitted_at")!=null);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> submit(String token,Map<String,Object> body) {
        if (!Boolean.TRUE.equals(body.get("agreed"))) throw new BizException(400,"请确认授权提交报价需求");
        if (!(body.get("submissionId") instanceof String requestId) || !requestId.matches("[A-Za-z0-9_-]{16,64}"))
            throw new BizException(400,"本次提交编号无效，请重新打开页面");
        String demand=serialize(QuoteRequirements.normalize(body.get("requirements")));
        String hash=digest(demand);
        var row=publicRow(token,true);
        if (row.get("submitted_at")!=null) {
            if (!(requestId.equals(row.get("submission_id")) && hash.equals(row.get("request_hash"))))
                throw new BizException(409,"本客户的报价需求已提交，请联系推荐伙伴");
            return Map.of("submitted",true);
        }
        long cid=id(row,"customer_id");
        Long project=id(row,"project_id");
        if(project==null) {
            Long resolved=soleProject(id(row,"tenant_id"));
            if(resolved!=null && scopeCustomer(cid,id(row,"tenant_id"),resolved)) project=resolved;
        }
        jdbc.update("""
            UPDATE crm_quote_intake SET requirements_json=?,submission_id=?,request_hash=?,submitted_at=CURRENT_TIMESTAMP
            WHERE customer_id=? AND token=?
            """,demand,requestId,hash,cid,token);
        events.publishEvent(new QuoteDemandSubmitted(cid,id(row,"tenant_id"),project));
        return Map.of("submitted",true);
    }

    private Long soleProject(long tenant) {
        return jdbc.queryForObject("SELECT IF(COUNT(*)=1,MIN(id),NULL) FROM biz_project WHERE tenant_id=? AND deleted=0",Long.class,tenant);
    }

    private boolean scopeCustomer(long customerId,long tenant,long project) {
        int changed=jdbc.update("""
            UPDATE crm_customer c
            LEFT JOIN crm_promoter p ON p.id=c.referrer_id
            SET c.project_id=?,c.version=COALESCE(c.version,0)+1
            WHERE c.id=? AND c.tenant_id=? AND c.deleted=0 AND c.project_id IS NULL
              AND (p.id IS NULL OR (p.tenant_id=c.tenant_id AND (p.project_id IS NULL OR p.project_id=?)))
              AND NOT EXISTS(SELECT 1 FROM crm_warehouse w WHERE w.id IN (c.assigned_warehouse_id,c.intended_warehouse_id)
                    AND (w.tenant_id<>c.tenant_id OR (w.project_id IS NOT NULL AND w.project_id<>?)))
              AND NOT EXISTS(SELECT 1 FROM crm_quote_intake old WHERE old.customer_id=c.id
                   AND (old.tenant_id<>c.tenant_id OR (old.project_id IS NOT NULL AND old.project_id<>?)))
            """,project,customerId,tenant,project,project,project);
        if(changed!=1) return false;
        jdbc.update("UPDATE crm_quote_intake SET project_id=? WHERE customer_id=? AND tenant_id=? AND project_id IS NULL",project,customerId,tenant);
        return true;
    }

    @Transactional
    public Map<String,Object> confirmProject(long customerId,long project) {
        long tenant=QuoteProjectAccess.requireTenant(jdbc,project);
        var row=one("SELECT project_id FROM crm_customer WHERE id=? AND tenant_id=? AND deleted=0 FOR UPDATE",customerId,tenant);
        if(row.get("project_id")==null) {
            if(!scopeCustomer(customerId,tenant,project)) throw new BizException(409,"客户已有归属与所选园区冲突");
            if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM crm_quote_intake WHERE customer_id=? AND project_id=? AND tenant_id=? AND submitted_at IS NOT NULL)",Boolean.class,customerId,project,tenant)))
                events.publishEvent(new QuoteDemandSubmitted(customerId,tenant,project));
        }
        return admin(customerId,project);
    }

    private Map<String,Object> publicRow(String token,boolean lock) {
        if (token==null || !token.matches("[a-f0-9]{32}")) throw unavailable();
        return one("""
            SELECT q.customer_id,q.tenant_id,q.project_id,q.submitted_at,q.submission_id,q.request_hash
            FROM crm_quote_intake q JOIN crm_customer c ON
            """+SAME_SCOPE+" AND c.referrer_id=q.promoter_id AND c.deleted=0 "+ACTIVE_OWNER+
            " WHERE q.token=?"+(lock?" FOR UPDATE":""),token);
    }

    private Map<String,Object> one(String sql,Object... args) {
        var rows=jdbc.queryForList(sql,args);
        if (rows.size()!=1) throw unavailable();
        return rows.get(0);
    }
    private Map<String,Object> view(Map<String,Object> row,boolean share) {
        Map<String,Object> out=new LinkedHashMap<>();
        String token=(String)row.get("token");
        out.put("stage",token==null?"lead":"intent");
        if (share && token!=null) out.put("token",token);
        if (row.get("submitted_at")!=null) {
            out.put("submittedAt",row.get("submitted_at").toString());
            try {
                if (!(row.get("requirements_json") instanceof String saved)) throw new BizException("报价需求暂不可读取");
                out.put("requirements",json.readValue(saved,new TypeReference<Map<String,Object>>(){}));
            } catch(JsonProcessingException e) {throw new BizException("报价需求暂不可读取");}
        }
        return out;
    }
    private String serialize(Object value) {
        try {return json.writeValueAsString(value);}
        catch(JsonProcessingException e) {throw new BizException(400,"报价资料格式不正确");}
    }
    private static Long id(Map<String,Object> row,String key) {
        Number n=(Number)row.get(key);
        return n==null?null:n.longValue();
    }
    private static String digest(String s) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    private static BizException unavailable() {return new BizException(404,"此客户不存在或报价入口不可用");}
    private static String newToken() {return UUID.randomUUID().toString().replace("-","");}
}
