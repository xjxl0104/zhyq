package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktPublicSeaMeta;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktPublicSeaMetaMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.wh.WhWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 内部专属公海池核心服务：同源三层下钻汇总、招商认领、72h首触达保活、定向投喂与云仓配额认领。
 */
@Service
@RequiredArgsConstructor
public class MktPublicSeaService {

    public static final String MODULE = "marketing";
    public static final String KEY_SLA_HOURS = "sea_sla_hours";
    public static final String KEY_WH_DAILY_CAP = "sea_wh_daily_cap";
    public static final String KEY_WH_ACTIVE_CAP = "sea_wh_active_cap";

    private final JdbcTemplate jdbc;
    private final BizSettings bizSettings;
    private final CustomerMapper customers;
    private final MktPublicSeaMetaMapper seaMetas;
    private final MktWarehouseMapper warehouses;
    private final WhWorkspaceService whWorkspace;
    private final MktAuditService audit;

    public int slaHours() { return Math.max(1, bizSettings.getInt(MODULE, KEY_SLA_HOURS, 72)); }
    public int whDailyCap() { return Math.max(1, bizSettings.getInt(MODULE, KEY_WH_DAILY_CAP, 5)); }
    public int whActiveCap() { return Math.max(1, bizSettings.getInt(MODULE, KEY_WH_ACTIVE_CAP, 10)); }

    /** 顶部 3 张分析结论卡与侧滑 L2/L3 下钻共用同源聚合（零二次重算）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> summary(Long projectId) {
        Long tenantId = resolveTenantId(projectId);
        List<Map<String, Object>> srcRows = jdbc.queryForList("""
                SELECT m.sea_source AS src, COUNT(1) AS cnt
                  FROM crm_public_sea_meta m
                  JOIN crm_customer c ON c.id = m.customer_id AND c.deleted = 0
                 WHERE m.tenant_id = ?
                   AND (? IS NULL OR m.project_id = ? OR m.project_id IS NULL)
                   AND c.referrer_id IS NULL AND c.status = 1
                   AND m.claim_status <> 'ARCHIVED_COLD'
                 GROUP BY m.sea_source
                """, tenantId, projectId, projectId);
        Map<String, Integer> bySource = new HashMap<>();
        int activeTotal = 0;
        for (Map<String, Object> r : srcRows) {
            int cnt = ((Number) r.get("cnt")).intValue();
            bySource.put(String.valueOf(r.get("src")), cnt);
            activeTotal += cnt;
        }
        int calcHot = bySource.getOrDefault("CALC_HOT", 0);
        int chanmama = bySource.getOrDefault("CHANMAMA", 0);
        int tungeeJob = bySource.getOrDefault("TUNGEE_JOB", 0);
        int expireReturn = bySource.getOrDefault("EXPIRE_RETURN", 0);
        int parkCrm = bySource.getOrDefault("PARK_CRM", 0) + bySource.getOrDefault("MANUAL_IMPORT", 0);

        Map<String, Object> washAgg = jdbc.queryForMap("""
                SELECT COALESCE(SUM(total_rows), 0) AS totalRows,
                       COALESCE(SUM(inserted_rows), 0) AS insertedRows,
                       COALESCE(SUM(blocked_private_lock), 0) AS blockedPrivateLock,
                       COALESCE(SUM(blocked_wh_hmac), 0) AS blockedWhHmac,
                       COALESCE(SUM(blocked_dup_agency), 0) AS blockedDupAgency
                  FROM crm_public_sea_wash_batch
                 WHERE tenant_id = ?
                   AND (? IS NULL OR project_id = ? OR project_id IS NULL)
                   AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
                """, tenantId, projectId, projectId);

        List<Map<String, Object>> slaRows = jdbc.queryForList("""
                SELECT m.claim_holder_type AS holderType,
                       COUNT(1) AS totalClaimed,
                       SUM(CASE WHEN m.first_touched_at IS NOT NULL THEN 1 ELSE 0 END) AS touchedCount,
                       SUM(CASE WHEN m.first_touched_at IS NULL AND m.sla_deadline_at IS NOT NULL
                                 AND m.sla_deadline_at <= DATE_ADD(NOW(), INTERVAL 12 HOUR) THEN 1 ELSE 0 END) AS nearExpireCount
                  FROM crm_public_sea_meta m
                  JOIN crm_customer c ON c.id = m.customer_id AND c.deleted = 0
                 WHERE m.tenant_id = ?
                   AND (? IS NULL OR m.project_id = ? OR m.project_id IS NULL)
                   AND c.referrer_id IS NULL AND c.status = 1
                   AND m.claim_status IN ('CLAIMED_ADMIN', 'CLAIMED_WH')
                 GROUP BY m.claim_holder_type
                """, tenantId, projectId, projectId);
        int adminClaimed = 0, adminTouched = 0, whClaimed = 0, whTouched = 0, nearExpire = 0;
        for (Map<String, Object> r : slaRows) {
            String ht = String.valueOf(r.get("holderType"));
            int tot = toInt(r.get("totalClaimed"));
            int touched = toInt(r.get("touchedCount"));
            nearExpire += toInt(r.get("nearExpireCount"));
            if ("WAREHOUSE".equals(ht)) { whClaimed = tot; whTouched = touched; }
            else { adminClaimed += tot; adminTouched += touched; }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fetchedAt", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()));
        out.put("activeTotal", activeTotal);
        out.put("calcHotCount", calcHot);
        out.put("chanmamaCount", chanmama);
        out.put("tungeeJobCount", tungeeJob);
        out.put("expireReturnCount", expireReturn);
        out.put("parkCrmCount", parkCrm);
        out.put("washTotalRows", toInt(washAgg.get("totalRows")));
        out.put("washInsertedRows", toInt(washAgg.get("insertedRows")));
        out.put("blockedPrivateLock", toInt(washAgg.get("blockedPrivateLock")));
        out.put("blockedWhHmac", toInt(washAgg.get("blockedWhHmac")));
        out.put("blockedDupAgency", toInt(washAgg.get("blockedDupAgency")));
        out.put("adminClaimedTotal", adminClaimed);
        out.put("adminTouchedCount", adminTouched);
        out.put("whClaimedTotal", whClaimed);
        out.put("whTouchedCount", whTouched);
        out.put("nearExpireCount", nearExpire);
        out.put("slaHours", slaHours());
        out.put("whDailyCap", whDailyCap());
        out.put("whActiveCap", whActiveCap());
        return out;
    }

    /** 后台内部招商/运营分页列表。 */
    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> adminPage(Long projectId, String seaSource, String claimStatus,
                                                     String keyword, int pageNo, int pageSize) {
        Long tenantId = resolveTenantId(projectId);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                 FROM crm_public_sea_meta m
                 JOIN crm_customer c ON c.id = m.customer_id AND c.deleted = 0
                WHERE m.tenant_id = ?
                  AND c.referrer_id IS NULL AND c.status = 1
                  AND m.claim_status <> 'ARCHIVED_COLD'
                """);
        args.add(tenantId);
        if (projectId != null) {
            where.append(" AND (m.project_id = ? OR m.project_id IS NULL)");
            args.add(projectId);
        }
        if (StringUtils.hasText(seaSource) && !"ALL".equalsIgnoreCase(seaSource)) {
            where.append(" AND m.sea_source = ?");
            args.add(seaSource.trim());
        }
        if (StringUtils.hasText(claimStatus) && !"ALL".equalsIgnoreCase(claimStatus)) {
            where.append(" AND m.claim_status = ?");
            args.add(claimStatus.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String kw = "%" + keyword.trim() + "%";
            where.append(" AND (c.name LIKE ? OR COALESCE(m.shop_name,'') LIKE ? OR COALESCE(m.category,'') LIKE ? OR COALESCE(c.phone,'') LIKE ?)");
            Collections.addAll(args, kw, kw, kw, kw);
        }

        long total = Optional.ofNullable(jdbc.queryForObject("SELECT COUNT(1) " + where, Long.class, args.toArray())).orElse(0L);
        if (total == 0) return PageResult.of(0, List.of());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(Math.max(1, pageSize));
        pageArgs.add(Math.max(0, (Math.max(1, pageNo) - 1) * Math.max(1, pageSize)));
        String sql = """
                SELECT c.id AS customerId, c.name, c.contact, c.phone, c.assigned_warehouse_id AS assignedWarehouseId,
                       m.sea_source AS seaSource, m.shop_name AS shopName, m.category, m.est_daily_orders AS estDailyOrders,
                       m.avg_weight_kg AS avgWeightKg, m.calc_low_price AS calcLowPrice, m.calc_high_price AS calcHighPrice,
                       m.logistics_score AS logisticsScore, m.urgency_level AS urgencyLevel, m.urgency_reason AS urgencyReason,
                       m.block_note AS blockNote, m.claim_status AS claimStatus, m.claim_holder_type AS claimHolderType,
                       m.claim_holder_name AS claimHolderName, m.claimed_at AS claimedAt, m.sla_deadline_at AS slaDeadlineAt,
                       m.first_touched_at AS firstTouchedAt, m.first_touch_note AS firstTouchNote,
                       m.feed_warehouse_ids AS feedWarehouseIds, m.release_count AS releaseCount, m.updated_at AS updatedAt
                """ + where + " ORDER BY CASE m.urgency_level WHEN 'S' THEN 0 WHEN 'A' THEN 1 ELSE 2 END, m.updated_at DESC LIMIT ? OFFSET ?";

        String currentOp = MktAuditService.currentOperator();
        boolean isSuperAdmin = hasRole("ROLE_admin");
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> records = jdbc.queryForList(sql, pageArgs.toArray()).stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>(row);
            String status = String.valueOf(row.get("claimStatus"));
            String holderName = row.get("claimHolderName") == null ? "" : String.valueOf(row.get("claimHolderName"));
            boolean unlocked = isSuperAdmin || ("CLAIMED_ADMIN".equals(status) && holderName.equals(currentOp));
            String rawPhone = row.get("phone") == null ? "" : String.valueOf(row.get("phone"));
            item.put("phoneUnlocked", unlocked);
            item.put("phone", unlocked ? rawPhone : maskPhone(rawPhone));
            item.put("hoursLeft", calcHoursLeft(row.get("slaDeadlineAt"), row.get("firstTouchedAt"), now));
            return item;
        }).toList();
        return PageResult.of(total, records);
    }

    /** 园区内部招商专员认领公海客户（条件原子更新，防并发双抢）。 */
    @Transactional
    public Map<String, Object> claimByAdmin(Long customerId) {
        String operator = MktAuditService.currentOperator();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusHours(slaHours());
        int updated = seaMetas.update(null, new LambdaUpdateWrapper<MktPublicSeaMeta>()
                .eq(MktPublicSeaMeta::getCustomerId, customerId)
                .in(MktPublicSeaMeta::getClaimStatus, "UNCLAIMED", "FEED_WH")
                .set(MktPublicSeaMeta::getClaimStatus, "CLAIMED_ADMIN")
                .set(MktPublicSeaMeta::getClaimHolderType, "ADMIN")
                .set(MktPublicSeaMeta::getClaimHolderName, operator)
                .set(MktPublicSeaMeta::getClaimedAt, now)
                .set(MktPublicSeaMeta::getSlaDeadlineAt, deadline)
                .set(MktPublicSeaMeta::getFirstTouchedAt, null)
                .set(MktPublicSeaMeta::getUpdatedAt, now));
        if (updated != 1) throw new BizException(409, "该客户刚被他人认领或状态已变更，请刷新列表");
        customers.update(null, new LambdaUpdateWrapper<Customer>()
                .eq(Customer::getId, customerId).isNull(Customer::getReferrerId)
                .set(Customer::getOwner, operator));
        Customer c = customers.selectById(customerId);
        audit.log("public_sea.claim.admin", "customer", customerId, "内部招商认领公海客户，启动 " + slaHours() + "h 保活");
        return Map.of("customerId", customerId, "phone", c == null ? "" : Optional.ofNullable(c.getPhone()).orElse(""),
                "claimStatus", "CLAIMED_ADMIN", "slaDeadlineAt", deadline, "hoursLeft", slaHours());
    }

    /** 提交首通核实纪要：解除 72h 超时自动弹回，允许更新核实后的单量与卡点。 */
    @Transactional
    public void recordFirstTouch(Long customerId, String note, Integer estDailyOrders, String category) {
        if (!StringUtils.hasText(note) || note.trim().length() < 4) {
            throw new BizException(400, "请如实填写至少 4 个字的首通电话核实纪要（如实际日单量、换仓意向与卡点）");
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<MktPublicSeaMeta> uw = new LambdaUpdateWrapper<MktPublicSeaMeta>()
                .eq(MktPublicSeaMeta::getCustomerId, customerId)
                .in(MktPublicSeaMeta::getClaimStatus, "CLAIMED_ADMIN", "CLAIMED_WH")
                .set(MktPublicSeaMeta::getFirstTouchedAt, now)
                .set(MktPublicSeaMeta::getFirstTouchNote, note.trim())
                .set(MktPublicSeaMeta::getBlockNote, note.trim())
                .set(estDailyOrders != null && estDailyOrders > 0, MktPublicSeaMeta::getEstDailyOrders, estDailyOrders)
                .set(StringUtils.hasText(category), MktPublicSeaMeta::getCategory, category == null ? null : category.trim())
                .set(MktPublicSeaMeta::getUpdatedAt, now);
        if (seaMetas.update(null, uw) != 1) {
            throw new BizException(409, "仅已认领在跟进的公海客户可登记首通纪要");
        }
        audit.log("public_sea.first_touch", "customer", customerId, "登记首通核实纪要，解除超时弹回");
    }

    /** 园区运营将已核实的公海客户定向投喂至加盟云仓限时认领池。 */
    @Transactional
    public void feedToWarehouses(Long customerId, List<Long> warehouseIds, String note) {
        LocalDateTime now = LocalDateTime.now();
        String joinedIds = (warehouseIds == null || warehouseIds.isEmpty()) ? null
                : String.join(",", warehouseIds.stream().filter(Objects::nonNull).map(String::valueOf).toList());
        LambdaUpdateWrapper<MktPublicSeaMeta> uw = new LambdaUpdateWrapper<MktPublicSeaMeta>()
                .eq(MktPublicSeaMeta::getCustomerId, customerId)
                .in(MktPublicSeaMeta::getClaimStatus, "UNCLAIMED", "CLAIMED_ADMIN", "FEED_WH")
                .set(MktPublicSeaMeta::getClaimStatus, "FEED_WH")
                .set(MktPublicSeaMeta::getClaimHolderType, null)
                .set(MktPublicSeaMeta::getClaimHolderId, null)
                .set(MktPublicSeaMeta::getClaimHolderName, "待加盟云仓认领")
                .set(MktPublicSeaMeta::getClaimedAt, now)
                .set(MktPublicSeaMeta::getSlaDeadlineAt, now.plusHours(slaHours()))
                .set(MktPublicSeaMeta::getFirstTouchedAt, null)
                .set(MktPublicSeaMeta::getFeedWarehouseIds, joinedIds)
                .set(StringUtils.hasText(note), MktPublicSeaMeta::getBlockNote, note == null ? null : note.trim())
                .set(MktPublicSeaMeta::getUpdatedAt, now);
        if (seaMetas.update(null, uw) != 1) {
            throw new BizException(409, "当前客户已被云仓认领或不可投喂，请刷新核对");
        }
        audit.log("public_sea.feed_wh", "customer", customerId, "定向投喂至云仓认领池:" + (joinedIds == null ? "本园区全仓" : joinedIds));
    }

    /** 主动释放回待认领公海（满 3 次且标记死单则转入冷宫归档）。 */
    @Transactional
    public void releaseToSea(Long customerId, String reason, boolean archiveCold) {
        if (!StringUtils.hasText(reason)) throw new BizException(400, "请填写释放原因或客户未成卡点，以便后续换人/换仓攻坚");
        MktPublicSeaMeta current = seaMetas.selectById(customerId);
        if (current == null) throw new BizException(404, "公海记录不存在");
        int nextReleaseCount = Optional.ofNullable(current.getReleaseCount()).orElse(0) + 1;
        String nextStatus = (archiveCold || nextReleaseCount >= 3 && reason.contains("停业")) ? "ARCHIVED_COLD" : "UNCLAIMED";
        int updated = seaMetas.update(null, new LambdaUpdateWrapper<MktPublicSeaMeta>()
                .eq(MktPublicSeaMeta::getCustomerId, customerId)
                .eq(MktPublicSeaMeta::getClaimStatus, current.getClaimStatus())
                .set(MktPublicSeaMeta::getClaimStatus, nextStatus)
                .set(MktPublicSeaMeta::getClaimHolderType, null)
                .set(MktPublicSeaMeta::getClaimHolderId, null)
                .set(MktPublicSeaMeta::getClaimHolderName, null)
                .set(MktPublicSeaMeta::getSlaDeadlineAt, null)
                .set(MktPublicSeaMeta::getFirstTouchedAt, null)
                .set(MktPublicSeaMeta::getBlockNote, reason.trim())
                .set(MktPublicSeaMeta::getReleaseCount, nextReleaseCount)
                .set(MktPublicSeaMeta::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) throw new BizException(409, "状态已变化，请刷新重试");
        customers.update(null, new LambdaUpdateWrapper<Customer>()
                .eq(Customer::getId, customerId).isNull(Customer::getReferrerId).ne(Customer::getStatus, 2)
                .set(Customer::getAssignedWarehouseId, null)
                .set(Customer::getWarehouseAssignmentStatus, 0)
                .set(Customer::getOwner, "内部公海待领"));
        audit.log("public_sea.release", "customer", customerId, reason.trim());
    }

    /** 云仓小程序端（主账号专享）：查看本仓可认领热单与今日剩余配额。 */
    @Transactional(readOnly = true)
    public Map<String, Object> warehousePool(int pageNo, int pageSize) {
        MktWarehouse wh = whWorkspace.owner().warehouse();
        if (!Integer.valueOf(MktWarehouseOnboardingService.JS_ONLINE).equals(wh.getJoinStatus())) {
            throw new BizException(403, "仅已上线的加盟云仓可认领园区公海热单");
        }
        int dailyCap = whDailyCap();
        int activeCap = whActiveCap();
        int todayUsed = countWhTodayClaims(wh.getId());
        int activeUsed = countWhActiveClaims(wh.getId());

        List<Map<String, Object>> allRows = jdbc.queryForList("""
                SELECT c.id AS customerId, c.name, c.contact, c.phone,
                       m.sea_source AS seaSource, m.category, m.est_daily_orders AS estDailyOrders,
                       m.avg_weight_kg AS avgWeightKg, m.urgency_level AS urgencyLevel,
                       m.block_note AS blockNote, m.claim_status AS claimStatus,
                       m.claim_holder_id AS claimHolderId, m.sla_deadline_at AS slaDeadlineAt,
                       m.first_touched_at AS firstTouchedAt, m.feed_warehouse_ids AS feedWarehouseIds
                  FROM crm_public_sea_meta m
                  JOIN crm_customer c ON c.id = m.customer_id AND c.deleted = 0
                 WHERE m.tenant_id = ?
                   AND (m.project_id = ? OR m.project_id IS NULL)
                   AND c.referrer_id IS NULL AND c.status = 1
                   AND (m.claim_status = 'FEED_WH'
                        OR (m.claim_status = 'UNCLAIMED' AND m.sea_source = 'CALC_HOT')
                        OR (m.claim_status = 'CLAIMED_WH' AND m.claim_holder_id = ?))
                 ORDER BY CASE WHEN m.claim_status = 'CLAIMED_WH' THEN 0 ELSE 1 END, m.updated_at DESC
                """, wh.getTenantId(), wh.getProjectId(), wh.getId());

        String myWhIdStr = String.valueOf(wh.getId());
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> matched = allRows.stream().filter(r -> {
            if ("CLAIMED_WH".equals(r.get("claimStatus"))) return true;
            String feedIds = r.get("feedWarehouseIds") == null ? "" : String.valueOf(r.get("feedWarehouseIds")).trim();
            if (feedIds.isEmpty()) return true;
            return Arrays.asList(feedIds.split(",")).contains(myWhIdStr);
        }).map(r -> {
            boolean mine = "CLAIMED_WH".equals(r.get("claimStatus")) && Objects.equals(asLong(r.get("claimHolderId")), wh.getId());
            String cat = r.get("category") == null ? "电商货主" : String.valueOf(r.get("category"));
            String rawPhone = r.get("phone") == null ? "" : String.valueOf(r.get("phone"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("customerId", r.get("customerId"));
            item.put("displayName", mine ? r.get("name") : (cat + "准客户 #" + r.get("customerId")));
            item.put("contact", mine ? r.get("contact") : "认领后解锁负责人");
            item.put("phone", mine ? rawPhone : maskPhone(rawPhone));
            item.put("phoneUnlocked", mine);
            item.put("seaSource", r.get("seaSource"));
            item.put("category", cat);
            item.put("estDailyOrders", r.get("estDailyOrders"));
            item.put("avgWeightKg", r.get("avgWeightKg"));
            item.put("urgencyLevel", r.get("urgencyLevel"));
            item.put("blockNote", r.get("blockNote"));
            item.put("claimedByMe", mine);
            item.put("firstTouched", r.get("firstTouchedAt") != null);
            item.put("hoursLeft", calcHoursLeft(r.get("slaDeadlineAt"), r.get("firstTouchedAt"), now));
            return item;
        }).toList();

        int from = Math.min(matched.size(), Math.max(0, (Math.max(1, pageNo) - 1) * Math.max(1, pageSize)));
        int to = Math.min(matched.size(), from + Math.max(1, pageSize));
        return Map.of(
                "warehouseId", wh.getId(),
                "warehouseName", wh.getName(),
                "slaHours", slaHours(),
                "dailyCap", dailyCap,
                "dailyUsed", todayUsed,
                "dailyLeft", Math.max(0, dailyCap - todayUsed),
                "activeCap", activeCap,
                "activeUsed", activeUsed,
                "activeLeft", Math.max(0, activeCap - activeUsed),
                "total", matched.size(),
                "records", matched.subList(from, to)
        );
    }

    /** 云仓主账号限时认领公海热单：对云仓加悲观锁校验每日 5 户 / 在途 10 户配额，条件更新绑定本仓承接。 */
    @Transactional
    public Map<String, Object> claimByWarehouse(Long customerId) {
        MktWarehouse identity = whWorkspace.owner().warehouse();
        MktWarehouse wh = warehouses.selectForUpdate(identity.getId());
        if (wh == null || !Integer.valueOf(MktWarehouseOnboardingService.JS_ONLINE).equals(wh.getJoinStatus())) {
            throw new BizException(403, "仅已上线的加盟云仓主账号可认领公海热单");
        }
        int dailyCap = whDailyCap();
        int activeCap = whActiveCap();
        if (countWhTodayClaims(wh.getId()) >= dailyCap) {
            throw new BizException(429, "今日公海认领配额（" + dailyCap + " 户）已满，请先跟进已认领客户");
        }
        if (countWhActiveClaims(wh.getId()) >= activeCap) {
            throw new BizException(429, "本仓在途公海客户已达上限（" + activeCap + " 户），请先推进签约或释放无意向客户");
        }
        MktPublicSeaMeta meta = seaMetas.selectById(customerId);
        if (meta == null || !Objects.equals(meta.getTenantId(), wh.getTenantId())) {
            throw new BizException(404, "公海热单不存在");
        }
        if (StringUtils.hasText(meta.getFeedWarehouseIds())
                && !Arrays.asList(meta.getFeedWarehouseIds().split(",")).contains(String.valueOf(wh.getId()))) {
            throw new BizException(403, "该客户未定向开放给本云仓认领");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusHours(slaHours());
        int updated = seaMetas.update(null, new LambdaUpdateWrapper<MktPublicSeaMeta>()
                .eq(MktPublicSeaMeta::getCustomerId, customerId)
                .in(MktPublicSeaMeta::getClaimStatus, "FEED_WH", "UNCLAIMED")
                .set(MktPublicSeaMeta::getClaimStatus, "CLAIMED_WH")
                .set(MktPublicSeaMeta::getClaimHolderType, "WAREHOUSE")
                .set(MktPublicSeaMeta::getClaimHolderId, wh.getId())
                .set(MktPublicSeaMeta::getClaimHolderName, wh.getName())
                .set(MktPublicSeaMeta::getClaimedAt, now)
                .set(MktPublicSeaMeta::getSlaDeadlineAt, deadline)
                .set(MktPublicSeaMeta::getFirstTouchedAt, null)
                .set(MktPublicSeaMeta::getUpdatedAt, now));
        if (updated != 1) throw new BizException(409, "手慢一步，该热单刚被认领，请刷新列表");

        customers.update(null, new LambdaUpdateWrapper<Customer>()
                .eq(Customer::getId, customerId).isNull(Customer::getReferrerId)
                .set(Customer::getAssignedWarehouseId, wh.getId())
                .set(Customer::getWarehouseAssignmentStatus, 2)
                .set(Customer::getOwner, wh.getName()));
        Customer c = customers.selectById(customerId);
        audit.log("public_sea.claim.wh", "customer", customerId, "云仓[" + wh.getName() + "]认领公海热单，启动 " + slaHours() + "h 保活");
        return Map.of(
                "customerId", customerId,
                "name", c == null ? "" : Optional.ofNullable(c.getName()).orElse(""),
                "contact", c == null ? "" : Optional.ofNullable(c.getContact()).orElse(""),
                "phone", c == null ? "" : Optional.ofNullable(c.getPhone()).orElse(""),
                "hoursLeft", slaHours()
        );
    }

    /** 云仓端回填首通接洽反馈，解除 72h 超时回收。 */
    @Transactional
    public void recordWarehouseTouch(Long customerId, String note) {
        MktWarehouse wh = whWorkspace.owner().warehouse();
        MktPublicSeaMeta meta = seaMetas.selectById(customerId);
        if (meta == null || !"CLAIMED_WH".equals(meta.getClaimStatus()) || !Objects.equals(meta.getClaimHolderId(), wh.getId())) {
            throw new BizException(403, "仅可为您本仓已认领的公海客户登记接洽反馈");
        }
        recordFirstTouch(customerId, note, null, null);
    }

    /** 定时任务：自动将超过 72h 未登记首通核实纪要的认领单弹回公海。 */
    @Transactional
    public int expireOverdueClaims(LocalDateTime now) {
        List<Long> dueIds = jdbc.queryForList("""
                SELECT customer_id FROM crm_public_sea_meta
                 WHERE claim_status IN ('CLAIMED_ADMIN', 'FEED_WH', 'CLAIMED_WH')
                   AND first_touched_at IS NULL
                   AND sla_deadline_at IS NOT NULL AND sla_deadline_at <= ?
                """, Long.class, now);
        int count = 0;
        for (Long cid : dueIds) {
            int u = seaMetas.update(null, new LambdaUpdateWrapper<MktPublicSeaMeta>()
                    .eq(MktPublicSeaMeta::getCustomerId, cid)
                    .in(MktPublicSeaMeta::getClaimStatus, "CLAIMED_ADMIN", "FEED_WH", "CLAIMED_WH")
                    .isNull(MktPublicSeaMeta::getFirstTouchedAt)
                    .le(MktPublicSeaMeta::getSlaDeadlineAt, now)
                    .set(MktPublicSeaMeta::getClaimStatus, "UNCLAIMED")
                    .set(MktPublicSeaMeta::getClaimHolderType, null)
                    .set(MktPublicSeaMeta::getClaimHolderId, null)
                    .set(MktPublicSeaMeta::getClaimHolderName, null)
                    .set(MktPublicSeaMeta::getSlaDeadlineAt, null)
                    .setSql("release_count = release_count + 1")
                    .set(MktPublicSeaMeta::getUpdatedAt, now));
            if (u == 1) {
                count++;
                customers.update(null, new LambdaUpdateWrapper<Customer>()
                        .eq(Customer::getId, cid).isNull(Customer::getReferrerId).ne(Customer::getStatus, 2)
                        .set(Customer::getAssignedWarehouseId, null)
                        .set(Customer::getWarehouseAssignmentStatus, 0)
                        .set(Customer::getOwner, "超时弹回公海"));
                audit.log("public_sea.sla_reclaim", "customer", cid, "超 " + slaHours() + "h 未首触达自动弹回内部公海");
            }
        }
        return count;
    }

    /** 外部伙伴锁客满 90 天或手工释放回公海时联动归集进内部公海池。 */
    @Transactional
    public void onLockReleased(Long customerId, String reason) {
        if (customerId == null) return;
        Customer c = customers.selectById(customerId);
        if (c == null || c.getReferrerId() != null || Integer.valueOf(2).equals(c.getStatus())) return;
        MktPublicSeaMeta meta = seaMetas.selectById(customerId);
        LocalDateTime now = LocalDateTime.now();
        if (meta == null) {
            meta = new MktPublicSeaMeta();
            meta.setCustomerId(customerId);
            meta.setTenantId(c.getTenantId() == null ? MyMetaObjectHandler.DEFAULT_TENANT_ID : c.getTenantId());
            meta.setProjectId(c.getProjectId());
            meta.setSeaSource("EXPIRE_RETURN");
            meta.setShopName(c.getBrandName());
            meta.setCategory(StringUtils.hasText(c.getIndustry()) ? c.getIndustry() : "综合电商");
            meta.setUrgencyLevel("A");
            meta.setUrgencyReason("外部伙伴锁客期满或主动释放回流，已有前期接洽基础，换专员/换云仓二次跟进易成单");
            meta.setBlockNote("上轮释放原因：" + (StringUtils.hasText(reason) ? reason : "锁定期满未签约"));
            meta.setClaimStatus("UNCLAIMED");
            meta.setReleaseCount(0);
            meta.setCreatedAt(now);
            meta.setUpdatedAt(now);
            seaMetas.insert(meta);
        } else if ("ARCHIVED_COLD".equals(meta.getClaimStatus())) {
            // 冷宫库保持不动
        } else {
            meta.setSeaSource("EXPIRE_RETURN");
            meta.setClaimStatus("UNCLAIMED");
            meta.setBlockNote("上轮释放原因：" + (StringUtils.hasText(reason) ? reason : "锁定期满未签约"));
            meta.setUpdatedAt(now);
            seaMetas.updateById(meta);
        }
    }

    private int countWhTodayClaims(Long warehouseId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        return Optional.ofNullable(jdbc.queryForObject(
                "SELECT COUNT(1) FROM crm_public_sea_meta WHERE claim_holder_type = 'WAREHOUSE' AND claim_holder_id = ? AND claimed_at >= ?",
                Integer.class, warehouseId, startOfDay)).orElse(0);
    }

    private int countWhActiveClaims(Long warehouseId) {
        return Optional.ofNullable(jdbc.queryForObject("""
                SELECT COUNT(1) FROM crm_public_sea_meta m
                  JOIN crm_customer c ON c.id = m.customer_id AND c.deleted = 0
                 WHERE m.claim_status = 'CLAIMED_WH' AND m.claim_holder_id = ? AND c.status = 1
                """, Integer.class, warehouseId)).orElse(0);
    }

    private static Integer calcHoursLeft(Object slaObj, Object touchedObj, LocalDateTime now) {
        if (touchedObj != null || slaObj == null) return null;
        LocalDateTime deadline = slaObj instanceof LocalDateTime ldt ? ldt
                : java.sql.Timestamp.valueOf(String.valueOf(slaObj)).toLocalDateTime();
        long h = Duration.between(now, deadline).toHours();
        return (int) Math.max(0, h);
    }

    private static String maskPhone(String p) {
        if (!StringUtils.hasText(p) || p.length() < 7) return "认领解锁";
        return p.substring(0, 3) + "****" + p.substring(p.length() - 4);
    }

    private static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> role.equals(a.getAuthority()));
    }

    private Long resolveTenantId(Long projectId) {
        if (projectId != null) {
            List<Long> t = jdbc.queryForList("SELECT tenant_id FROM biz_project WHERE id = ? AND deleted = 0 LIMIT 1", Long.class, projectId);
            if (!t.isEmpty() && t.get(0) != null) return t.get(0);
        }
        return MyMetaObjectHandler.DEFAULT_TENANT_ID;
    }

    private static int toInt(Object v) { return v instanceof Number n ? n.intValue() : 0; }
    private static Long asLong(Object v) { return v instanceof Number n ? n.longValue() : null; }
}
