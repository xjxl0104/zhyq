package com.zhyq.park.marketing.quote.rate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.quote.QuoteProjectAccess;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktWarehouseOnboardingService;
import com.zhyq.park.marketing.wh.WhWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class QuoteRateService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final MktWarehouseMapper warehouses;
    private final WhWorkspaceService workspace;
    private final MktAuditService audit;

    public record Update(Long version,JsonNode card){}
    private record Stored(long version,QuoteCard card){}

    public Map<String,Object> adminCard(long id,Long projectId){
        long tenant = QuoteProjectAccess.requireTenant(jdbc,projectId);
        return view(checkWarehouse(warehouses.selectById(id),tenant,projectId));
    }
    @Transactional
    public Map<String,Object> saveAdmin(long id,Long projectId,Update update){
        long tenant=QuoteProjectAccess.requireTenant(jdbc,projectId);
        return save(checkWarehouse(warehouses.selectForUpdate(id),tenant,projectId),update);
    }
    public Map<String,Object> ownerCard(){
        MktWarehouse w=workspace.owner().warehouse();
        return view(checkWarehouse(w,w.getTenantId(),w.getProjectId()));
    }
    @Transactional
    public Map<String,Object> saveOwner(Update update){
        // Lock before checking persisted contact role/phone so a simultaneous owner change serializes.
        var w=warehouses.selectForUpdate(com.zhyq.park.marketing.wh.WhAuthContext.currentWarehouseId());
        var identity=workspace.owner().warehouse();
        return save(checkWarehouse(w,identity.getTenantId(),identity.getProjectId()),update);
    }
    private MktWarehouse checkWarehouse(MktWarehouse w,Long tenant,Long projectId){
        if(w==null||projectId==null||tenant==null||!tenant.equals(w.getTenantId())||!projectId.equals(w.getProjectId()))
            throw new BizException(404,"云仓未归属当前园区或无权访问");
        if(!Integer.valueOf(MktWarehouseOnboardingService.JS_ONLINE).equals(w.getJoinStatus()))
            throw new BizException(409,"仅已上线的加盟云仓可以维护报价");
        return w;
    }
    private Stored current(MktWarehouse w){
        List<Stored> cards=jdbc.query("SELECT version,card_json FROM crm_quote_rate_card WHERE warehouse_id=? AND tenant_id=? AND project_id=?",
                (rs,n)->new Stored(rs.getLong(1),parseStored(rs.getString(2))),w.getId(),w.getTenantId(),w.getProjectId());
        return cards.isEmpty()?new Stored(0,QuoteCard.empty()):cards.get(0);
    }
    private Map<String,Object> view(MktWarehouse w){return view(w,current(w));}
    private Map<String,Object> view(MktWarehouse w,Stored saved){
        return Map.of("warehouseId",w.getId(),"warehouseName",w.getName(),"version",saved.version(),"card",saved.card(),"schema",QuoteCard.schema());
    }
    private Map<String,Object> save(MktWarehouse w,Update update){
        if(update==null||update.version()==null||update.version()<0)throw QuoteCard.bad("缺少原价卡版本，请刷新");
        QuoteCard card=QuoteCard.parse(update.card());
        var prev=current(w);
        if(prev.version()!=update.version())throw stale();
        long version=prev.version()+1;
        String data;
        try{data=json.writeValueAsString(card);}catch(JsonProcessingException e){throw QuoteCard.bad("价格内容格式错误");}
        if(prev.version()==0){
            try{jdbc.update("INSERT INTO crm_quote_rate_card(warehouse_id,tenant_id,project_id,version,card_json) VALUES(?,?,?,?,?)",
                    w.getId(),w.getTenantId(),w.getProjectId(),version,data);}
            catch(org.springframework.dao.DuplicateKeyException e){throw stale();}
        }else if(jdbc.update("UPDATE crm_quote_rate_card SET card_json=?,version=? WHERE warehouse_id=? AND tenant_id=? AND project_id=? AND version=?",
                data,version,w.getId(),w.getTenantId(),w.getProjectId(),prev.version())!=1)throw stale();
        audit.logRequired("warehouse.quote.update",w,"更新加盟云仓报价",Map.of("version",prev.version()),Map.of("version",version));
        jdbc.update("""
                UPDATE crm_quote_advice
                SET status='pending', generation=generation+1, suggestions=NULL, updated_at=NOW()
                WHERE tenant_id=? AND project_id=?
                """, w.getTenantId(), w.getProjectId());
        return view(w,new Stored(version,card));
    }
    private static BizException stale(){return new BizException(409,"价卡已变更，请刷新后重试");}
    private JsonNode parseJson(String value){
        try{return json.readTree(value);}catch(JsonProcessingException e){throw new IllegalStateException("已保存的报价数据无法读取",e);}
    }
    private QuoteCard parseStored(String data){return QuoteCard.parse(parseJson(data));}

    @Transactional(readOnly=true)
    public Map<String,Object> comparison(long customerId,Long projectId){
        return comparisonForSubmission(customerId,QuoteProjectAccess.requireTenant(jdbc,projectId),projectId);
    }

    /** Also called by the internal after-commit assessment job using its persisted intake scope. */
    @Transactional(readOnly=true)
    public Map<String,Object> comparisonForSubmission(long customerId,long tenantId,Long projectId){
        if(projectId==null)throw new BizException(409,"客户尚未归属园区，请确认后比价");
        var demand=jdbc.queryForList("""
            SELECT CAST(q.requirements_json AS CHAR CHARACTER SET utf8mb4) AS requirementsJson,q.submitted_at AS submittedAt
            FROM crm_customer c JOIN biz_project p ON p.id=c.project_id AND p.tenant_id=c.tenant_id AND p.deleted=0
            LEFT JOIN crm_quote_intake q ON q.customer_id=c.id AND q.tenant_id=c.tenant_id AND q.project_id=c.project_id
            WHERE c.id=? AND c.project_id=? AND c.tenant_id=? AND c.deleted=0
            """,customerId,projectId,tenantId);
        if(demand.isEmpty())throw new BizException(404,"客户不存在或不属于当前园区");
        var submission=demand.get(0);
        if(submission.get("requirementsJson")==null||submission.get("submittedAt")==null)throw new BizException(409,"客户还未提交报价需求");
        var requirements=parseJson(submission.get("requirementsJson").toString());
        var rows=jdbc.query("""
            SELECT w.id,w.name,r.version,r.card_json,w.daily_capacity
              FROM crm_warehouse w LEFT JOIN crm_quote_rate_card r
                ON r.warehouse_id=w.id AND r.tenant_id=w.tenant_id AND r.project_id=w.project_id
             WHERE w.tenant_id=? AND w.project_id=? AND w.join_status=? AND w.deleted=0 ORDER BY w.name,w.id
            """,(rs,n)->{
                String raw=rs.getString(4);
                QuoteCard card=raw==null?QuoteCard.empty():parseStored(raw);
                return Map.of("warehouseId",rs.getLong(1),"warehouseName",rs.getString(2),"version",rs.getLong(3),
                        "offers",QuoteCalculator.offers(card,requirements,rs.getObject("daily_capacity",Integer.class)));
            },tenantId,projectId,MktWarehouseOnboardingService.JS_ONLINE);
        return Map.of("customerId",customerId,"submittedAt",submission.get("submittedAt"),"warehouses",rows);
    }
}
