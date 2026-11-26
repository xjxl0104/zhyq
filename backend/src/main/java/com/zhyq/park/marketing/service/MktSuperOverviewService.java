package com.zhyq.park.marketing.service;

import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.admin.MktAccountControl;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Read-only global reporting. Every entry point reloads the live administrator grant. */
@Service
@RequiredArgsConstructor
public class MktSuperOverviewService {
    private final MktAccountControl access;
    private final MktPromoterMapper promoters;
    private final NamedParameterJdbcTemplate jdbc;
    private final com.zhyq.park.marketing.quote.QuoteIntakeService quoteIntake;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    // Reuse the physical-shipment CTE: daily ERP snapshots take precedence over manual orders.
    static final String VOLUME_SQL = MktPartnerShipmentService.FACT_SQL.substring(0,
            MktPartnerShipmentService.FACT_SQL.lastIndexOf("SELECT promoter_id,contract_id")) + """
            SELECT promoter_id,contract_id,customer_id,SUM(volume) AS volume
            FROM facts GROUP BY promoter_id,contract_id,customer_id
            """;

    @Transactional(readOnly = true)
    public Map<String, Object> overview(String view, Long selectionId, String group, String keyword,
                                        String startDate, String endDate, int page, int size) {
        long tenant = requireTenant();
        if (!Set.of("partners", "warehouses").contains(view)) throw new BizException(400, "请选择伙伴或云仓总览");
        if (!Set.of("p4", "all").contains(group)) throw new BizException(400, "伙伴筛选无效");
        if (page < 1 || size < 1 || size > 50 || page > 100000) throw new BizException(400, "分页参数无效");
        if (keyword != null && keyword.length() > 100) throw new BizException(400, "搜索内容过长");
        Range range = range(startDate, endDate);
        boolean operational = view.equals("warehouses");
        Data data = load(tenant, range, operational);
        Scope scope = data.scope(view, selectionId);
        List<Map<String, Object>> rows;
        String kind;
        if (selectionId != null) {
            rows = data.customerRows(scope, operational);
            kind = "customers";
        } else if (view.equals("partners")) {
            rows = new ArrayList<>();
            for (Map<String, Object> p : data.partners) {
                if (group.equals("p4") && !"P4".equals(p.get("positionCode"))) continue;
                Scope branch = data.scope("partners", id(p));
                rows.add(map("id", id(p), "name", p.get("name"), "phone", mask(p.get("phone")),
                        "positionCode", p.get("positionCode"), "status", p.get("status"),
                        "summary", data.summary(branch), "searchPhone", p.get("phone")));
            }
            kind = "partners";
        } else {
            rows = new ArrayList<>();
            for (Map<String, Object> w : data.warehouses) rows.add(map("id", id(w), "name", w.get("name"),
                    "contact", w.get("contact"), "phone", mask(w.get("phone")), "joinStatus", w.get("joinStatus"),
                    "summary", data.operations(data.scope("warehouses", id(w))), "searchPhone", w.get("phone")));
            kind = "warehouses";
        }
        String search = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        rows.removeIf(row -> !search.isEmpty() && !String.valueOf(row.get("name")).toLowerCase(Locale.ROOT).contains(search)
                && !String.valueOf(row.get("searchPhone")).contains(search));
        rows.sort(Comparator.<Map<String, Object>, BigDecimal>comparing(row -> decimal(((Map<?, ?>) row.get("summary")).get("shipmentVolume")))
                .reversed().thenComparing(row -> id(row), Comparator.reverseOrder()));
        int total = rows.size();
        int from = Math.min(total, (page - 1) * size);
        List<Map<String, Object>> records = new ArrayList<>(rows.subList(from, Math.min(total, from + size)));
        records.forEach(row -> row.remove("searchPhone"));
        return map("view", view, "selectionId", selectionId, "selectionName", scope.name,
                "summary", operational ? data.operations(scope) : data.summary(scope), "kind", kind, "records", records, "total", total,
                "page", page, "size", size, "startDate", range.start.toString(), "endDate", range.end.toString());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> customer(Long customerId, String startDate, String endDate, Long warehouseId) {
        long tenant = requireTenant();
        if (customerId == null || customerId <= 0) throw new BizException(400, "客户参数无效");
        Range range = range(startDate, endDate);
        boolean operational = warehouseId != null;
        Data data = load(tenant, range, operational);
        if (operational && !data.scope("warehouses",warehouseId).customers.contains(customerId)) throw new BizException(404,"该云仓未关联此客户");
        Map<String, Object> customer = data.customerById.get(customerId);
        if (customer == null) throw new BizException(404, "客户不存在");
        List<Map<String, Object>> details = jdbc.queryForList("""
            SELECT id,name,brand_name AS brandName,contact,phone,status,project_id AS projectId,demand_area AS demandArea,
                   fulfillment_requirements AS requirements,intake_remark AS intakeRemark,
                   public_progress AS publicProgress,progress_percentage AS progressPercentage,
                   progress_updated_at AS progressUpdatedAt
            FROM crm_customer WHERE tenant_id=:tenantId AND deleted=0 AND id=:id
            """, Map.of("tenantId", tenant, "id", customerId));
        if (details.isEmpty()) throw new BizException(404, "客户不存在");
        Map<String, Object> detail = new LinkedHashMap<>(details.get(0));
        detail.put("requirements", MktReferralRequirements.parse((String) detail.get("requirements")));
        detail.put("quoteIntake",quoteIntake.submittedDetails(customerId,tenant,number(detail.get("projectId"))));
        if (operational && !Objects.equals(warehouseId,number(customer.get("assignedWarehouseId")))) {
            detail.remove("publicProgress"); detail.remove("progressUpdatedAt"); detail.remove("progressPercentage");
        }
        Scope scope = data.customerScope(Set.of(customerId), warehouseId, String.valueOf(customer.get("name")));
        List<Map<String, Object>> contracts = new ArrayList<>();
        for (Map<String, Object> c : data.contracts) if (scope.contracts.contains(id(c))) {
            Scope contractScope = new Scope(scope.members, scope.customers, Set.of(id(c)), null, false, true, "");
            var contract = map("id", id(c), "contractNo", c.get("contractNo"), "status", c.get("status"),
                    "warehouseName", data.warehouseName(number(c.get("warehouseId"))));
            if (!operational) contract.put("summary", data.summary(contractScope));
            contracts.add(contract);
        }
        return map("customer", detail, "summary", operational ? data.operations(scope) : data.summary(scope), "contracts", contracts,
                "startDate", range.start.toString(), "endDate", range.end.toString());
    }

    private long requireTenant() {
        Long actorId = access.requireSuperAdmin();
        MktPromoter actor = promoters.selectById(actorId);
        if (actor == null || !Integer.valueOf(1).equals(actor.getStatus())) throw new BizException(403, "管理员账号已停用");
        return actor.getTenantId() == null ? MyMetaObjectHandler.DEFAULT_TENANT_ID : actor.getTenantId();
    }

    private Data load(long tenant, Range range, boolean operational) {
        Map<String, Object> params = Map.of("tenantId", tenant, "startDate", range.start.toString(), "endExclusive", range.end.plusDays(1).toString());
        List<Map<String, Object>> partners = operational ? List.of() : jdbc.queryForList("SELECT id,name,phone,parent_id AS parentId,position_code AS positionCode,status FROM crm_promoter WHERE tenant_id=:tenantId AND deleted=0", params);
        List<Map<String, Object>> customers = jdbc.queryForList("SELECT id,name,brand_name AS brandName,referrer_id AS referrerId,assigned_warehouse_id AS assignedWarehouseId,warehouse_assignment_status AS assignmentStatus,warehouse_sales_stage AS salesStage,status FROM crm_customer WHERE tenant_id=:tenantId AND deleted=0", params);
        List<Map<String, Object>> contracts = jdbc.queryForList("SELECT id,contract_no AS contractNo,customer_id AS customerId,warehouse_id AS warehouseId,partner_id AS partnerId,status FROM crm_service_contract WHERE tenant_id=:tenantId AND deleted=0", params);
        List<Map<String, Object>> warehouses = jdbc.queryForList("SELECT id,name,contact,phone,join_status AS joinStatus FROM crm_warehouse WHERE tenant_id=:tenantId AND deleted=0", params);
        List<Map<String, Object>> volume = jdbc.queryForList(operational ? WarehouseShipmentFacts.overviewSql() : VOLUME_SQL, params);
        if (operational) return new Data(partners,customers,contracts,warehouses,volume,List.of(),List.of());
        List<Map<String, Object>> orders = jdbc.queryForList("""
            SELECT customer_id AS customerId,CASE WHEN source_type=2 THEN source_id ELSE NULL END AS contractId,
                   warehouse_id AS warehouseId,promoter_id AS promoterId,
                   SUM(CASE WHEN source_type=2 AND commission_unit_price IS NOT NULL
                       THEN commission_unit_price * COALESCE(commission_order_count,1) ELSE COALESCE(pool_amount,0) END) AS total
            FROM crm_referral_order WHERE tenant_id=:tenantId AND deleted=0 AND status=2
              AND event_time>=:startDate AND event_time<:endExclusive
            GROUP BY customer_id,CASE WHEN source_type=2 THEN source_id ELSE NULL END,warehouse_id,promoter_id
            """, params);
        List<Map<String, Object>> ledger = jdbc.queryForList("""
            SELECT o.customer_id AS customerId,CASE WHEN o.source_type=2 THEN o.source_id ELSE NULL END AS contractId,
                   o.warehouse_id AS warehouseId,c.promoter_id AS promoterId,c.status,
                   SUM(c.amount) AS amount,SUM(CASE WHEN c.sign=1 THEN COALESCE(c.withdrawn_amount,
                       CASE WHEN c.status=4 THEN c.amount ELSE 0 END) ELSE 0 END) AS withdrawn
            FROM crm_promoter_commission c
            LEFT JOIN crm_referral_order o ON o.id=c.referral_order_id AND o.tenant_id=c.tenant_id AND o.deleted=0
            WHERE c.tenant_id=:tenantId AND c.deleted=0 AND c.status IN (1,2,3,4)
              AND COALESCE(o.event_time,c.create_time)>=:startDate AND COALESCE(o.event_time,c.create_time)<:endExclusive
            GROUP BY o.customer_id,CASE WHEN o.source_type=2 THEN o.source_id ELSE NULL END,o.warehouse_id,c.promoter_id,c.status
            """, params);
        return new Data(partners, customers, contracts, warehouses, volume, orders, ledger);
    }

    record Range(LocalDate start, LocalDate end) {}
    static Range range(String start, String end) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        try {
            LocalDate first = start == null || start.isBlank() ? LocalDate.of(2000, 1, 1) : LocalDate.parse(start);
            LocalDate last = end == null || end.isBlank() ? today : LocalDate.parse(end);
            if (first.isBefore(LocalDate.of(2000, 1, 1)) || last.isAfter(today) || first.isAfter(last)) throw new IllegalArgumentException();
            return new Range(first, last);
        } catch (RuntimeException e) { throw new BizException(400, "请选择有效的统计日期范围，结束日期不能晚于今天"); }
    }
    record Scope(Set<Long> members, Set<Long> customers, Set<Long> contracts, Long warehouse, boolean all, boolean contractOnly, String name) {}

    /** Aggregate independent fact sets; never multiply commission rows by a shipment/contract join. */
    static final class Data {
        final List<Map<String, Object>> partners, customers, contracts, warehouses, volume, orders, ledger;
        final Map<Long, Map<String, Object>> partnerById, customerById, contractById, warehouseById;
        Data(List<Map<String, Object>> p, List<Map<String, Object>> c, List<Map<String, Object>> ct,
             List<Map<String, Object>> w, List<Map<String, Object>> v, List<Map<String, Object>> o, List<Map<String, Object>> l) {
            partners=p; customers=c; contracts=ct; warehouses=w; volume=v; orders=o; ledger=l;
            partnerById=index(p); customerById=index(c); contractById=index(ct); warehouseById=index(w);
        }
        Scope scope(String view, Long selected) {
            if (selected == null && view.equals("warehouses")) {
                Set<Long> ids = new LinkedHashSet<>(), contractIds = new LinkedHashSet<>();
                for (var c : customers) if (warehouseById.containsKey(number(c.get("assignedWarehouseId")))) ids.add(id(c));
                for (var c : contracts) if (warehouseById.containsKey(number(c.get("warehouseId")))) {
                    ids.add(number(c.get("customerId"))); contractIds.add(id(c));
                }
                for (var r : volume) if (warehouseById.containsKey(number(r.get("warehouseId")))) ids.add(number(r.get("customer_id")));
                ids.retainAll(customerById.keySet());
                return new Scope(Set.of(),ids,contractIds,null,true,false,"全部云仓");
            }
            if (selected == null) return new Scope(partnerById.keySet(), customerById.keySet(), contractById.keySet(), null, true, false, "全部");
            if (selected <= 0) throw new BizException(400, "总览对象无效");
            if (view.equals("partners")) {
                Map<String, Object> p = partnerById.get(selected);
                if (p == null) throw new BizException(404, "伙伴不存在");
                Set<Long> members = descendants(selected);
                Set<Long> ids = new LinkedHashSet<>();
                for (Map<String, Object> c : customers) if (members.contains(number(c.get("referrerId")))) ids.add(id(c));
                for (Map<String, Object> c : contracts) if (members.contains(number(c.get("partnerId")))) ids.add(number(c.get("customerId")));
                ids.retainAll(customerById.keySet());
                Scope base = customerScope(ids, null, String.valueOf(p.get("name")));
                return new Scope(members, ids, base.contracts, null, false, false, base.name);
            }
            Map<String, Object> w = warehouseById.get(selected);
            if (w == null) throw new BizException(404, "云仓不存在");
            Set<Long> ids = new LinkedHashSet<>();
            for (Map<String, Object> c : customers) if (Objects.equals(number(c.get("assignedWarehouseId")), selected)) ids.add(id(c));
            for (Map<String, Object> c : contracts) if (Objects.equals(number(c.get("warehouseId")), selected)) ids.add(number(c.get("customerId")));
            ids.retainAll(customerById.keySet());
            for (var r : volume) if (Objects.equals(number(r.get("warehouseId")),selected) && customerById.containsKey(number(r.get("customer_id")))) ids.add(number(r.get("customer_id")));
            return customerScope(ids, selected, String.valueOf(w.get("name")));
        }
        Scope customerScope(Set<Long> ids, Long warehouse, String name) {
            Set<Long> contractIds = new LinkedHashSet<>(), members = new LinkedHashSet<>();
            for (Long id : ids) { Map<String,Object> c=customerById.get(id); if(c!=null && number(c.get("referrerId"))!=null) members.add(number(c.get("referrerId"))); }
            for (Map<String,Object> c : contracts) if (ids.contains(number(c.get("customerId"))) && (warehouse == null || Objects.equals(warehouse,number(c.get("warehouseId"))))) {
                contractIds.add(id(c)); if(number(c.get("partnerId"))!=null) members.add(number(c.get("partnerId")));
            }
            return new Scope(members, ids, contractIds, warehouse, false, false, name);
        }
        Set<Long> descendants(Long root) {
            Set<Long> result = new LinkedHashSet<>(); result.add(root);
            boolean changed;
            do { changed=false; for(Map<String,Object> p:partners) if(result.contains(number(p.get("parentId"))) && result.add(id(p))) changed=true; } while(changed);
            return result;
        }
        boolean includes(Map<String,Object> row, Scope scope, boolean shipment) {
            if(scope.all) return true;
            Long customer=number(row.get(shipment?"customer_id":"customerId"));
            Long contract=number(row.get(shipment?"contract_id":"contractId"));
            if(!scope.customers.contains(customer)) return false;
            if(contract!=null) return scope.contracts.contains(contract);
            if(scope.contractOnly) return false;
            return scope.warehouse==null || Objects.equals(scope.warehouse, number(row.get("warehouseId")));
        }
        Map<String,Object> summary(Scope scope) {
            BigDecimal shipments=BigDecimal.ZERO, total=BigDecimal.ZERO, commission=BigDecimal.ZERO,
                    pending=BigDecimal.ZERO, settled=BigDecimal.ZERO, withdrawn=BigDecimal.ZERO;
            for(Map<String,Object> r:volume) if(includes(r,scope,true)) shipments=shipments.add(decimal(r.get("volume")));
            for(Map<String,Object> r:orders) if(includes(r,scope,false)) total=total.add(decimal(r.get("total")));
            for(Map<String,Object> r:ledger) if(includes(r,scope,false)) {
                BigDecimal amount=decimal(r.get("amount"));commission=commission.add(amount);
                if(number(r.get("status"))!=null && number(r.get("status"))<=2) pending=pending.add(amount); else settled=settled.add(amount);
                withdrawn=withdrawn.add(decimal(r.get("withdrawn")));
            }
            long p4=scope.members.stream().map(partnerById::get).filter(Objects::nonNull).filter(p->"P4".equals(p.get("positionCode"))).count();
            long signed=scope.contracts.stream().map(contractById::get).filter(Objects::nonNull).filter(c->signed(c.get("status"))).count();
            Set<Long> warehouseIds=new HashSet<>();
            for(Long c:scope.contracts) {Map<String,Object> ct=contractById.get(c); if(ct!=null && number(ct.get("warehouseId"))!=null)warehouseIds.add(number(ct.get("warehouseId")));}
            if(scope.warehouse!=null)warehouseIds.add(scope.warehouse);
            return map("partnerCount",scope.members.size(),"p4Count",p4,"customerCount",scope.customers.size(),
                    "warehouseCount",scope.all?warehouses.size():warehouseIds.size(),"contractCount",signed,
                    "shipmentVolume",shipments.toPlainString(),"commissionTotal",total.toPlainString(),"partnerCommission",commission.toPlainString(),
                    "pendingCommission",pending.toPlainString(),"settledCommission",settled.toPlainString(),"withdrawnAmount",withdrawn.toPlainString());
        }
        Map<String,Object> operations(Scope scope) {
            Set<Long> signedCustomers = new HashSet<>();
            long signedContracts = 0, accepted = 0, pending = 0, stored = 0;
            for (Long contractId : scope.contracts) {
                var c = contractById.get(contractId);
                if (signed(c.get("status"))) { signedCustomers.add(number(c.get("customerId"))); signedContracts++; }
            }
            for (Long customerId : scope.customers) {
                var c = customerById.get(customerId);
                Long assigned = number(c.get("assignedWarehouseId"));
                if (!warehouseById.containsKey(assigned) || (scope.warehouse != null && !scope.warehouse.equals(assigned))) continue;
                if (Objects.equals(number(c.get("assignmentStatus")),1L)) pending++;
                if (!Objects.equals(number(c.get("assignmentStatus")),2L)) continue;
                accepted++;
                Long stage = number(c.get("salesStage"));
                if (stage != null && stage >= 3) signedCustomers.add(customerId);
                if (stage != null && stage >= 4) stored++;
            }
            BigDecimal tickets = BigDecimal.ZERO;
            Set<Long> shippers = new HashSet<>();
            for (var r : volume) {
                Long id = number(r.get("customer_id"));
                if (!scope.customers.contains(id) || (scope.warehouse != null && !scope.warehouse.equals(number(r.get("warehouseId"))))) continue;
                BigDecimal value = decimal(r.get("volume"));
                tickets = tickets.add(value);
                if (value.signum() > 0) shippers.add(id);
            }
            return map("warehouseCount",scope.warehouse == null ? warehouses.size() : 1,
                    "customerCount",scope.customers.size(),"contractCount",signedContracts,"signedCustomerCount",signedCustomers.size(),
                    "acceptedCustomerCount",accepted,"pendingCustomerCount",pending,"storedCustomerCount",stored,
                    "shippingCustomerCount",shippers.size(),"shipmentVolume",tickets.toPlainString());
        }
        List<Map<String,Object>> customerRows(Scope scope, boolean operational) {
            List<Map<String,Object>> out=new ArrayList<>();
            for(Long id:scope.customers) {
                Map<String,Object> c=customerById.get(id); if(c==null)continue;
                Scope one=customerScope(Set.of(id),scope.warehouse,String.valueOf(c.get("name")));
                out.add(map("id",id,"name",c.get("name"),"brandName",c.get("brandName"),"status",c.get("status"),
                        "referrerName",name(partnerById.get(number(c.get("referrerId")))),"summary",operational ? operations(one) : summary(one)));
            }
            return out;
        }
        String warehouseName(Long id) {return name(warehouseById.get(id));}
        static String name(Map<String,Object> row) {return row==null?"未关联":String.valueOf(row.get("name"));}
        static Map<Long,Map<String,Object>> index(List<Map<String,Object>> rows) {Map<Long,Map<String,Object>> out=new LinkedHashMap<>();for(Map<String,Object> r:rows)out.put(id(r),r);return out;}
    }
    static boolean signed(Object status) {Long n=number(status);return n!=null&&n>=4&&n<=8;}
    static Long id(Map<String,?> row) {return number(row.get("id"));}
    static Long number(Object n) {return n==null?null:((Number)n).longValue();}
    static BigDecimal decimal(Object n) {return n==null?BigDecimal.ZERO:new BigDecimal(n.toString());}
    static String mask(Object value) {String s=value==null?"":value.toString();return s.matches("^1\\d{10}$")?s.substring(0,3)+"****"+s.substring(7):"";}
    static Map<String,Object> map(Object... pairs) {Map<String,Object> out=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)out.put((String)pairs[i],pairs[i+1]);return out;}
}
