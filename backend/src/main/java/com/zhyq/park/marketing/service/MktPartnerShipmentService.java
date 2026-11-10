package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Physical shipments, independent of pricing readiness and commission beneficiaries. */
@Service
@RequiredArgsConstructor
public class MktPartnerShipmentService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> TEAM_POSITIONS = Set.of("P2", "P3", "P4");
    private final MktPromoterMapper promoters;
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * One query keeps totals/details consistent. Renewals replace the current date range, so daily
     * snapshots also match complete historical terms. Explicit orders retain their recorded contract.
     * Termination includes its business date; older records without audit use the contract update date.
     */
    static final String FACT_SQL = """
        WITH historical_terms AS (
          SELECT contract_id,tenant_id,
                 NULLIF(JSON_UNQUOTE(JSON_EXTRACT(IF(JSON_VALID(snapshot),snapshot,'{}'),'$.startDate')),'null') AS start_date,
                 NULLIF(JSON_UNQUOTE(JSON_EXTRACT(IF(JSON_VALID(snapshot),snapshot,'{}'),'$.endDate')),'null') AS end_date
          FROM crm_service_contract_version
          WHERE tenant_id=:tenantId AND deleted=0 AND JSON_VALID(snapshot)
            AND JSON_CONTAINS_PATH(IF(JSON_VALID(snapshot),snapshot,'{}'),'all','$.startDate','$.endDate')
            AND JSON_UNQUOTE(JSON_EXTRACT(IF(JSON_VALID(snapshot),snapshot,'{}'),'$.status')) IN ('4','5','6','7','8')
        ), snapshot_candidates AS (
          SELECT v.id, v.stat_date, v.consign_num, v.fetched_at,v.warehouse_id,
                 COALESCE(v.data_as_of,v.fetched_at,v.update_time) AS data_at,
                 c.id AS contract_id, c.contract_no, c.customer_id, cu.name AS customer_name,
                 w.name AS warehouse_name, COALESCE(c.partner_id,cu.referrer_id) AS promoter_id,
                 ROW_NUMBER() OVER (PARTITION BY v.id ORDER BY c.id DESC) AS chosen
          FROM crm_yunshan_daily_volume v
          JOIN crm_service_contract c ON c.customer_id=v.customer_id AND c.warehouse_id=v.warehouse_id
            AND c.tenant_id=v.tenant_id AND c.deleted=0 AND c.status IN (4,5,6,7,8)
            AND (((c.start_date IS NULL OR c.start_date<=v.stat_date)
                  AND (c.end_date IS NULL OR c.end_date>=v.stat_date))
                 OR EXISTS (
                   SELECT 1 FROM historical_terms h
                   WHERE h.contract_id=c.id AND h.tenant_id=c.tenant_id
                     AND h.end_date REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
                     AND (h.start_date IS NULL OR (h.start_date REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
                          AND CAST(h.start_date AS DATE)<=v.stat_date))
                     AND CAST(h.end_date AS DATE)>=v.stat_date
                 ))
            AND (c.status<>8 OR v.stat_date<=DATE(COALESCE((
              SELECT MIN(a.create_time) FROM sys_audit a
              WHERE a.tenant_id=c.tenant_id AND a.deleted=0 AND a.module='crm.marketing'
                AND a.action='contract.terminate' AND a.biz_type='service_contract' AND a.biz_id=c.id
            ),c.update_time)))
          JOIN crm_customer cu ON cu.id=c.customer_id AND cu.tenant_id=c.tenant_id AND cu.deleted=0
          LEFT JOIN crm_warehouse w ON w.id=c.warehouse_id AND w.tenant_id=c.tenant_id
          WHERE v.tenant_id=:tenantId AND v.deleted=0 AND v.consign_num>=0
            AND v.stat_date>=:startDate AND v.stat_date<:endExclusive
        ), facts AS (
          SELECT contract_id,contract_no,customer_id,customer_name,warehouse_name,promoter_id,
                 consign_num AS volume,data_at FROM snapshot_candidates WHERE chosen=1
          UNION ALL
          SELECT c.id,c.contract_no,c.customer_id,cu.name,w.name,
                 COALESCE(o.promoter_id,c.partner_id,cu.referrer_id),
                 GREATEST(COALESCE(o.commission_order_count,1),0),
                 COALESCE(o.update_time,o.confirm_time,o.event_time)
          FROM crm_referral_order o
          JOIN crm_service_contract c ON c.id=o.source_id AND c.customer_id=o.customer_id
            AND c.tenant_id=o.tenant_id AND c.deleted=0 AND c.status IN (4,5,6,7,8)
            AND (o.warehouse_id IS NULL OR o.warehouse_id=c.warehouse_id)
          JOIN crm_customer cu ON cu.id=c.customer_id AND cu.tenant_id=c.tenant_id AND cu.deleted=0
          LEFT JOIN crm_warehouse w ON w.id=c.warehouse_id AND w.tenant_id=c.tenant_id
          WHERE o.tenant_id=:tenantId AND o.deleted=0 AND o.source_type=2 AND o.status=2
            AND COALESCE(o.source_no,'') NOT LIKE 'YS-%'
            AND o.event_time>=:startDate AND o.event_time<:endExclusive
            AND NOT EXISTS (
              SELECT 1 FROM snapshot_candidates s
              WHERE s.chosen=1 AND s.customer_id=o.customer_id AND s.warehouse_id=c.warehouse_id
                AND s.stat_date=DATE(o.event_time)
            )
        )
        SELECT promoter_id,contract_id,contract_no,customer_id,customer_name,warehouse_name,
               SUM(volume) AS volume,MAX(data_at) AS data_at
        FROM facts WHERE promoter_id IN (:memberIds)
        GROUP BY promoter_id,contract_id,contract_no,customer_id,customer_name,warehouse_name
        ORDER BY volume DESC,contract_id DESC,promoter_id
        """;

    public Map<String,Object> report(Long actorId, String scope, String start, String end, Long memberId) {
        MktPromoter actor = promoters.selectById(actorId);
        if (actor == null || Integer.valueOf(1).equals(actor.getDeleted()) || !Integer.valueOf(1).equals(actor.getStatus())) throw new BizException(403, "伙伴账号当前不可查看发货量");
        if (!Set.of("own", "team").contains(scope)) throw new BizException("发货量查询类型不正确");
        boolean canViewTeam = TEAM_POSITIONS.contains(Objects.toString(actor.getPositionCode(), ""));
        if ("team".equals(scope) && !canViewTeam) throw new BizException(403, "P2 及以上伙伴可查看团队发货量");
        if ("own".equals(scope) && memberId != null) throw new BizException(403, "我的发货量不能指定其他成员");
        DateRange range = dates(start, end, LocalDate.now(BUSINESS_ZONE));
        long tenantId = tenant(actor);
        Map<Long,MktPromoter> selected = new LinkedHashMap<>();
        if ("own".equals(scope)) selected.put(actorId, actor);
        else {
            Map<Long,MktPromoter> all = new HashMap<>();
            for (MktPromoter p : promoters.selectList(new LambdaQueryWrapper<MktPromoter>().eq(MktPromoter::getTenantId, tenantId)))
                if (p.getId() != null && tenant(p) == tenantId && !Integer.valueOf(1).equals(p.getDeleted())) all.put(p.getId(), p);
            for (MktPromoter p : all.values()) if (descendant(p, actorId, all)) selected.put(p.getId(), p);
            if (memberId != null) {
                MktPromoter member = selected.get(memberId);
                if (member == null) throw new BizException(403, "只能查看自己团队下级成员的发货量");
                selected.clear(); selected.put(memberId, member);
            }
        }
        List<Fact> facts = selected.isEmpty() ? List.of() : jdbc.query(FACT_SQL,
                Map.of("tenantId", tenantId, "memberIds", selected.keySet(), "startDate", range.start().toString(), "endExclusive", range.end().plusDays(1).toString()),
                (rs,n) -> new Fact(rs.getLong("promoter_id"), rs.getLong("contract_id"), rs.getString("contract_no"),
                        rs.getLong("customer_id"), rs.getString("customer_name"), rs.getString("warehouse_name"),
                        rs.getBigDecimal("volume").longValueExact(), rs.getTimestamp("data_at") == null ? null : rs.getTimestamp("data_at").toLocalDateTime()));
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("scope", scope); result.put("startDate", range.start()); result.put("endDate", range.end());
        result.put("canViewTeam", canViewTeam); result.put("memberId", memberId);
        result.put("memberName", memberId == null ? null : selected.get(memberId).getName());
        addTotals(result, facts);
        result.put("memberCount", "team".equals(scope) ? selected.size() : 1);
        result.put("hasData", !facts.isEmpty());
        LocalDateTime updated = facts.stream().map(Fact::dataAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        result.put("dataAsOf", updated); result.put("lastDataAt", updated);
        result.put("notice", "按已同步发货日统计；云仓日汇总优先，同日出库明细不重复累加。今日数据可能尚未同步完整，空白日期不代表已确认零发货。");
        List<Map<String,Object>> contracts = new ArrayList<>(), members = new ArrayList<>();
        if ("own".equals(scope) || memberId != null) {
            for (Fact f : facts) {
                Map<String,Object> row = new LinkedHashMap<>();
                row.put("contractId", f.contractId()); row.put("contractNo", f.contractNo());
                row.put("customerId", f.customerId()); row.put("customerName", f.customerName());
                row.put("warehouseName", f.warehouseName()); row.put("volume", Long.toString(f.volume()));
                contracts.add(row);
            }
        } else {
            for (MktPromoter p : selected.values()) {
                List<Fact> own = facts.stream().filter(f -> f.promoterId() == p.getId()).toList();
                Map<String,Object> row = new LinkedHashMap<>();
                row.put("memberId", p.getId()); row.put("name", p.getName()); row.put("positionCode", p.getPositionCode());
                addTotals(row, own); row.put("volume", row.remove("totalVolume")); row.put("hasData", !own.isEmpty());
                members.add(row);
            }
            members.sort(Comparator.<Map<String,Object>>comparingLong(r -> Long.parseLong(r.get("volume").toString())).reversed()
                    .thenComparingLong(r -> ((Number) r.get("memberId")).longValue()));
        }
        result.put("contracts", contracts); result.put("members", members);
        return result;
    }

    private static void addTotals(Map<String,Object> out, List<Fact> facts) {
        long total = 0;
        for (Fact fact : facts) total = Math.addExact(total, fact.volume());
        out.put("totalVolume", Long.toString(total));
        out.put("contractCount", facts.stream().map(Fact::contractId).distinct().count());
        out.put("customerCount", facts.stream().map(Fact::customerId).distinct().count());
    }
    static boolean descendant(MktPromoter child, Long actor, Map<Long,MktPromoter> all) {
        if (Objects.equals(child.getId(), actor)) return false;
        Set<Long> seen = new HashSet<>(); seen.add(child.getId());
        Long parent = child.getParentId();
        while (parent != null && seen.add(parent)) {
            if (parent.equals(actor)) return true;
            MktPromoter p = all.get(parent);
            if (p == null) return false;
            parent = p.getParentId();
        }
        return false;
    }
    static DateRange dates(String start, String end, LocalDate today) {
        try {
            LocalDate last = end == null || end.isBlank() ? today : LocalDate.parse(end);
            LocalDate first = start == null || start.isBlank() ? last.withDayOfMonth(1) : LocalDate.parse(start);
            if (last.isAfter(today) || first.isAfter(last) || ChronoUnit.DAYS.between(first,last) >= 366)
                throw new BizException("请选择不超过 366 天且不晚于今天的发货日期");
            return new DateRange(first, last);
        } catch (DateTimeParseException e) { throw new BizException("发货日期格式应为 YYYY-MM-DD"); }
    }
    private static long tenant(MktPromoter p) { return p.getTenantId() == null ? MyMetaObjectHandler.DEFAULT_TENANT_ID : p.getTenantId(); }
    record DateRange(LocalDate start, LocalDate end) {}
    record Fact(long promoterId, long contractId, String contractNo, long customerId, String customerName, String warehouseName, long volume, LocalDateTime dataAt) {}
}
