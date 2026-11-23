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
          AND p.deleted=0 AND p.status=1 AND (p.project_id IS NULL OR p.project_id=c.project_id)
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

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> markIntent(long promoterId,long customerId) {
        // Lock the actual customer even if no quote row exists yet. Two clicks
        // on a new lead must yield the same code.
        one("SELECT c.id FROM crm_customer c "+ACTIVE_OWNER+
            " WHERE c.id=? AND c.referrer_id=? AND c.deleted=0 FOR UPDATE",customerId,promoterId);
        var row=one(PARTNER,customerId,promoterId);
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
        var row=one("""
            SELECT q.token,CAST(q.requirements_json AS CHAR CHARACTER SET utf8mb4) AS requirements_json,
              q.submitted_at FROM crm_customer c
            JOIN biz_project s ON s.id=c.project_id AND s.tenant_id=c.tenant_id AND s.deleted=0
            LEFT JOIN crm_quote_intake q ON
            """+SAME_SCOPE+" WHERE c.id=? AND c.project_id=? AND c.tenant_id=? AND c.deleted=0",
            customerId,projectId,QuoteProjectAccess.requireTenant(jdbc,projectId));
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
        jdbc.update("""
            UPDATE crm_quote_intake SET requirements_json=?,submission_id=?,request_hash=?,submitted_at=CURRENT_TIMESTAMP
            WHERE customer_id=? AND token=?
            """,demand,requestId,hash,cid,token);
        events.publishEvent(new QuoteDemandSubmitted(cid,id(row,"tenant_id"),id(row,"project_id")));
        return Map.of("submitted",true);
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
