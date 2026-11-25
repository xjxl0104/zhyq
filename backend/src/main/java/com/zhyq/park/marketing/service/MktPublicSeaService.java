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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 内部专属公海池核心服务（含广东产业带分区、高德地推坐标聚类、500m GPS 现场拍照打卡延保 15 天与三层下钻同源聚合）。
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
    private final MktExtDataGateway extGateway;
    private final MktAuditService audit;

    public int slaHours() { return Math.max(1, bizSettings.getInt(MODULE, KEY_SLA_HOURS, 72)); }
    public int whDailyCap() { return Math.max(1, bizSettings.getInt(MODULE, KEY_WH_DAILY_CAP, 5)); }
    public int whActiveCap() { return Math.max(1, bizSettings.getInt(MODULE, KEY_WH_ACTIVE_CAP, 10)); }
    public int checkinMaxMeters() { return Math.max(50, bizSettings.getInt(MODULE, "sea_checkin_max_meters", 500)); }
    public int visitProtectDays() { return Math.max(1, bizSettings.getInt(MODULE, "sea_visit_protect_days", 15)); }

    /** 顶部 4 张分析结论卡与侧滑 L2/L3 下钻共用同源聚合（零二次重算）。 */
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
            int cnt = toInt(r.get("cnt"));
            bySource.put(String.valueOf(r.get("src")), cnt);
            activeTotal += cnt;
        }

        // 广东战区分区户数分布
        List<Map<String, Object>> zoneRows = jdbc.queryForList("""
                SELECT COALESCE(m.zone_code, 'GZ') AS zc, COUNT(1) AS cnt
                  FROM crm_public_sea_meta m
                  JOIN crm_customer c ON c.id = m.customer_id AND c.deleted = 0
                 WHERE m.tenant_id = ?
                   AND (? IS NULL OR m.project_id = ? OR m.project_id IS NULL)
                   AND c.referrer_id IS NULL AND c.status = 1
                   AND m.claim_status <> 'ARCHIVED_COLD'
                 GROUP BY COALESCE(m.zone_code, 'GZ')
                """, tenantId, projectId, projectId);
        Map<String, Integer> zoneCounts = new LinkedHashMap<>();
        for (Map<String, Object> r : zoneRows) {
            zoneCounts.put(String.valueOf(r.get("zc")), toInt(r.get("cnt")));
        }

        // 5km 黄金圈、失信预警、15天地推打卡保护统计
        Map<String, Object> geoRiskAgg = jdbc.queryForMap("""
                SELECT COALESCE(SUM(CASE WHEN m.dist_nearest_wh_km IS NOT NULL AND m.dist_nearest_wh_km <= 5.0 THEN 1 ELSE 0 END), 0) AS within5kmCount,
                       COALESCE(SUM(CASE WHEN m.dishonest_count > 0 THEN 1 ELSE 0 END), 0) AS dishonestRiskCount,
                       COALESCE(SUM(CASE WHEN m.qcc_aligned = 1 THEN 1 ELSE 0 END), 0) AS qccAlignedCount,
                       COALESCE(SUM(CASE WHEN m.visit_protected_until IS NOT NULL AND m.visit_protected_until > NOW() THEN 1 ELSE 0 END), 0) AS visitProtectedCount
                  FROM crm_public_sea_meta m
                  JOIN crm_customer c ON c.id = m.customer_id AND c.deleted = 0
                 WHERE m.tenant_id = ?
                   AND (? IS NULL OR m.project_id = ? OR m.project_id IS NULL)
                   AND c.referrer_id IS NULL AND c.status = 1
                   AND m.claim_status <> 'ARCHIVED_COLD'
                """, tenantId, projectId, projectId);

        Integer clusterBuildingCount = jdbc.queryForObject("""
                SELECT COUNT(1) FROM (
                  SELECT m.building_name
                    FROM crm_public_sea_meta m
                    JOIN crm_customer c ON c.id = m.customer_id AND c.deleted = 0
                   WHERE m.tenant_id = ?
                     AND (? IS NULL OR m.project_id = ? OR m.project_id IS NULL)
                     AND c.referrer_id IS NULL AND c.status = 1
                     AND m.claim_status <> 'ARCHIVED_COLD'
                     AND m.building_name IS NOT NULL AND m.building_name <> ''
                   GROUP BY m.building_name
                  HAVING COUNT(1) >= 2
                ) b
                """, Integer.class, tenantId, projectId, projectId);

        Integer totalLinkedShops = jdbc.queryForObject("""
                SELECT COUNT(1)
                  FROM crm_public_sea_shop s
                  JOIN crm_public_sea_meta m ON m.customer_id = s.customer_id
                  JOIN crm_customer c ON c.id = m.customer_id AND c.deleted = 0
                 WHERE s.tenant_id = ?
                   AND (? IS NULL OR m.project_id = ? OR m.project_id IS NULL)
                   AND c.referrer_id IS NULL AND c.status = 1
                """, Integer.class, tenantId, projectId, projectId);

        Map<String, Object> washAgg = jdbc.queryForMap("""
                SELECT COALESCE(SUM(total_rows), 0) AS totalRows,
                       COALESCE(SUM(inserted_rows), 0) AS insertedRows,
                       COALESCE(SUM(blocked_private_lock), 0) AS blockedPrivateLock,
                       COALESCE(SUM(blocked_wh_hmac), 0) AS blockedWhHmac,
                       COALESCE(SUM(blocked_dup_agency), 0) AS blockedDupAgency,
                       COALESCE(SUM(skipped_out_province), 0) AS skippedOutProvince,
                       COALESCE(SUM(qcc_cache_hits), 0) AS qccCacheHits,
                       COALESCE(SUM(qcc_api_calls), 0) AS qccApiCalls
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
        out.put("calcHotCount", bySource.getOrDefault("CALC_HOT", 0) + bySource.getOrDefault("FEIYU_AD", 0));
        out.put("feiyuAdCount", bySource.getOrDefault("FEIYU_AD", 0));
        out.put("amapPoiCount", bySource.getOrDefault("AMAP_POI", 0));
        out.put("chanmamaCount", bySource.getOrDefault("CHANMAMA", 0));
        out.put("tungeeJobCount", bySource.getOrDefault("TUNGEE_JOB", 0));
        out.put("expireReturnCount", bySource.getOrDefault("EXPIRE_RETURN", 0));
        out.put("parkCrmCount", bySource.getOrDefault("PARK_CRM", 0) + bySource.getOrDefault("MANUAL_IMPORT", 0));
        out.put("zoneCounts", zoneCounts);
        out.put("within5kmCount", toInt(geoRiskAgg.get("within5kmCount")));
        out.put("clusterBuildingCount", Optional.ofNullable(clusterBuildingCount).orElse(0));
        out.put("totalLinkedShops", Optional.ofNullable(totalLinkedShops).orElse(0));
        out.put("dishonestRiskCount", toInt(geoRiskAgg.get("dishonestRiskCount")));
        out.put("qccAlignedCount", toInt(geoRiskAgg.get("qccAlignedCount")));
        out.put("visitProtectedCount", toInt(geoRiskAgg.get("visitProtectedCount")));
        out.put("washTotalRows", toInt(washAgg.get("totalRows")));
        out.put("washInsertedRows", toInt(washAgg.get("insertedRows")));
        out.put("blockedPrivateLock", toInt(washAgg.get("blockedPrivateLock")));
        out.put("blockedWhHmac", toInt(washAgg.get("blockedWhHmac")));
        out.put("blockedDupAgency", toInt(washAgg.get("blockedDupAgency")));
        out.put("skippedOutProvince", toInt(washAgg.get("skippedOutProvince")));
        out.put("qccCacheHits", toInt(washAgg.get("qccCacheHits")));
        out.put("qccApiCalls", toInt(washAgg.get("qccApiCalls")));
        out.put("adminClaimedTotal", adminClaimed);
        out.put("adminTouchedCount", adminTouched);
        out.put("whClaimedTotal", whClaimed);
        out.put("whTouchedCount", whTouched);
        out.put("nearExpireCount", nearExpire);
        out.put("slaHours", slaHours());
        out.put("whDailyCap", whDailyCap());
        out.put("whActiveCap", whActiveCap());
        out.put("checkinMaxMeters", checkinMaxMeters());
        out.put("visitProtectDays", visitProtectDays());
        out.put("connectors", extGateway.connectorStatus());
        return out;
    }

    /** 后台内部公海分页列表（支持广东分片区、距离圈、电商平台、工商风险多维过滤）。 */
    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> adminPage(Long projectId, String zoneCode, String seaSource,
                                                     String claimStatus, String distFilter, String platform,
                                                     String riskFilter, String keyword, int pageNo, int pageSize) {
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
        if (StringUtils.hasText(zoneCode) && !"ALL".equalsIgnoreCase(zoneCode)) {
            if ("ALL_GD".equalsIgnoreCase(zoneCode)) {
                where.append(" AND COALESCE(m.zone_code, 'GZ') <> 'OUT_GD'");
            } else {
                where.append(" AND COALESCE(m.zone_code, 'GZ') = ?");
                args.add(zoneCode.trim().toUpperCase(Locale.ROOT));
            }
        }
        if (StringUtils.hasText(seaSource) && !"ALL".equalsIgnoreCase(seaSource)) {
            where.append(" AND m.sea_source = ?");
            args.add(seaSource.trim());
        }
        if (StringUtils.hasText(claimStatus) && !"ALL".equalsIgnoreCase(claimStatus)) {
            if ("VISIT_15D".equalsIgnoreCase(claimStatus)) {
                where.append(" AND m.visit_protected_until IS NOT NULL AND m.visit_protected_until > NOW()");
            } else {
                where.append(" AND m.claim_status = ?");
                args.add(claimStatus.trim());
            }
        }
        if ("5KM".equalsIgnoreCase(distFilter)) {
            where.append(" AND m.dist_nearest_wh_km IS NOT NULL AND m.dist_nearest_wh_km <= 5.0");
        } else if ("CLUSTER".equalsIgnoreCase(distFilter)) {
            where.append("""
                     AND m.building_name IS NOT NULL AND m.building_name <> ''
                     AND m.building_name IN (
                       SELECT b.building_name FROM crm_public_sea_meta b
                        WHERE b.tenant_id = m.tenant_id AND b.claim_status <> 'ARCHIVED_COLD'
                          AND b.building_name IS NOT NULL AND b.building_name <> ''
                        GROUP BY b.building_name HAVING COUNT(1) >= 2
                     )
                    """);
        }
        if (StringUtils.hasText(platform) && !"ALL".equalsIgnoreCase(platform)) {
            where.append(" AND EXISTS (SELECT 1 FROM crm_public_sea_shop s WHERE s.customer_id = m.customer_id AND s.platform = ?)");
            args.add(platform.trim());
        }
        if ("CLEAN".equalsIgnoreCase(riskFilter)) {
            where.append(" AND COALESCE(m.dishonest_count, 0) = 0 AND COALESCE(m.insured_count, 0) >= 5");
        } else if ("RISK".equalsIgnoreCase(riskFilter)) {
            where.append(" AND COALESCE(m.dishonest_count, 0) > 0");
        }
        if (StringUtils.hasText(keyword)) {
            String kw = "%" + keyword.trim() + "%";
            where.append(" AND (c.name LIKE ? OR COALESCE(m.shop_name,'') LIKE ? OR COALESCE(m.category,'') LIKE ? OR COALESCE(m.address,'') LIKE ? OR COALESCE(c.phone,'') LIKE ?)");
            Collections.addAll(args, kw, kw, kw, kw, kw);
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
                       m.province, m.city, m.district, m.zone_code AS zoneCode, m.address, m.building_name AS buildingName,
                       m.lng, m.lat, m.nearest_wh_name AS nearestWhName, m.dist_nearest_wh_km AS distNearestWhKm,
                       m.credit_code AS creditCode, m.legal_person AS legalPerson, m.reg_capital AS regCapital,
                       m.insured_count AS insuredCount, m.dishonest_count AS dishonestCount, m.qcc_aligned AS qccAligned,
                       m.visit_protected_until AS visitProtectedUntil, m.last_checkin_dist_m AS lastCheckinDistM,
                       m.last_checkin_at AS lastCheckinAt, m.last_checkin_photo AS lastCheckinPhoto,
                       m.feed_warehouse_ids AS feedWarehouseIds, m.release_count AS releaseCount, m.updated_at AS updatedAt
                """ + where + " ORDER BY CASE m.urgency_level WHEN 'S' THEN 0 WHEN 'A' THEN 1 ELSE 2 END, m.updated_at DESC LIMIT ? OFFSET ?";

        List<Map<String, Object>> rawRows = jdbc.queryForList(sql, pageArgs.toArray());
        List<Long> cids = rawRows.stream().map(r -> asLong(r.get("customerId"))).filter(Objects::nonNull).toList();
        Map<Long, List<Map<String, Object>>> shopsByCust = loadShopsMap(cids);
        Map<String, Integer> buildingCounts = loadBuildingCounts(tenantId, projectId);

        String currentOp = MktAuditService.currentOperator();
        boolean isSuperAdmin = hasRole("ROLE_admin");
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> records = rawRows.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>(row);
            Long cid = asLong(row.get("customerId"));
            String status = String.valueOf(row.get("claimStatus"));
            String holderName = row.get("claimHolderName") == null ? "" : String.valueOf(row.get("claimHolderName"));
            boolean unlocked = isSuperAdmin || ("CLAIMED_ADMIN".equals(status) && holderName.equals(currentOp));
            String rawPhone = row.get("phone") == null ? "" : String.valueOf(row.get("phone"));
            item.put("phoneUnlocked", unlocked && StringUtils.hasText(rawPhone));
            item.put("phone", !StringUtils.hasText(rawPhone) ? "待扫楼/企查补录" : (unlocked ? rawPhone : maskPhone(rawPhone)));
            item.put("hoursLeft", calcHoursLeft(row.get("slaDeadlineAt"), row.get("firstTouchedAt"), now));
            item.put("visitDaysLeft", calcVisitDaysLeft(row.get("visitProtectedUntil"), now));
            List<Map<String, Object>> shops = shopsByCust.getOrDefault(cid, List.of());
            if (shops.isEmpty() && row.get("shopName") != null && !String.valueOf(row.get("shopName")).isBlank()) {
                shops = List.of(Map.of("platform", "抖音", "shopName", String.valueOf(row.get("shopName"))));
            }
            item.put("shops", shops);
            String bName = row.get("buildingName") == null ? "" : String.valueOf(row.get("buildingName"));
            item.put("buildingPeerCount", bName.isEmpty() ? 0 : buildingCounts.getOrDefault(bName, 1));
            return item;
        }).toList();
        return PageResult.of(total, records);
    }

    private Map<Long, List<Map<String, Object>>> loadShopsMap(List<Long> customerIds) {
        if (customerIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(customerIds.size(), "?"));
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT customer_id AS customerId, platform, shop_name AS shopName, rank_no AS rankNo, monthly_sales AS monthlySales, logistics_score AS logisticsScore "
                            + "FROM crm_public_sea_shop WHERE customer_id IN (" + placeholders + ") ORDER BY id",
                    customerIds.toArray());
            Map<Long, List<Map<String, Object>>> out = new HashMap<>();
            for (Map<String, Object> r : rows) {
                out.computeIfAbsent(asLong(r.get("customerId")), k -> new ArrayList<>()).add(r);
            }
            return out;
        } catch (Exception e) { return Map.of(); }
    }

    private Map<String, Integer> loadBuildingCounts(Long tenantId, Long projectId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT building_name AS bn, COUNT(1) AS cnt
                      FROM crm_public_sea_meta
                     WHERE tenant_id = ? AND (? IS NULL OR project_id = ? OR project_id IS NULL)
                       AND claim_status <> 'ARCHIVED_COLD'
                       AND building_name IS NOT NULL AND building_name <> ''
                     GROUP BY building_name
                    """, tenantId, projectId, projectId);
            Map<String, Integer> out = new HashMap<>();
            for (Map<String, Object> r : rows) out.put(String.valueOf(r.get("bn")), toInt(r.get("cnt")));
            return out;
        } catch (Exception e) { return Map.of(); }
    }

    /** 园区内部招商专员认领公海客户。 */
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

    /** 现场照片须在打卡前多少分钟内由本人上传。 */
    static final int CHECKIN_PHOTO_MAX_AGE_MINUTES = 30;

    /**
     * 外勤 500 米 GPS 定位 + 现场照片打卡（通过后解除 72h 弹回，授予 15 天保护期）。
     * 防作弊/防抢单约束：
     * 1. 客户必须已落图（有高德坐标），不再用打卡人位置当锚点；
     * 2. 照片必须是本人 30 分钟内刚上传的图片附件（file:ID），不接受任意文本路径；
     * 3. 只允许对待认领/投喂中的客户，或本人已认领的客户打卡；云仓已认领、冷宫、他人在跟的客户不得打卡接管（管理员除外）。
     * 注：浏览器定位本身可被伪造，网页端只能提高门槛；彻底防伪需在小程序内用相机直拍 + 服务端水印（待做）。
     */
    @Transactional
    public Map<String, Object> visitCheckin(Long customerId, BigDecimal checkinLng, BigDecimal checkinLat, String photoUrl, String note) {
        if (checkinLng == null || checkinLat == null) {
            throw new BizException(400, "缺少现场 GPS 定位，请允许浏览器定位后再打卡");
        }
        if (!StringUtils.hasText(note) || note.trim().length() < 4) {
            throw new BizException(400, "请填写至少 4 个字的现场拜访纪要");
        }
        MktPublicSeaMeta meta = seaMetas.selectById(customerId);
        if (meta == null) throw new BizException(404, "公海客户记录不存在");

        String operator = MktAuditService.currentOperator();
        boolean isAdmin = hasRole("ROLE_admin");
        String status = meta.getClaimStatus();
        if ("CLAIMED_WH".equals(status)) {
            throw new BizException(409, "该客户已由加盟云仓认领跟进，内部专员不能打卡接管");
        }
        if ("ARCHIVED_COLD".equals(status)) {
            throw new BizException(409, "该客户已归档冷宫，不能打卡");
        }
        if ("CLAIMED_ADMIN".equals(status) && !operator.equals(meta.getClaimHolderName()) && !isAdmin) {
            throw new BizException(403, "该客户正由「" + meta.getClaimHolderName() + "」跟进中，只有跟进人本人可以打卡");
        }
        if (meta.getLng() == null || meta.getLat() == null) {
            throw new BizException(400, "该客户还没有落图坐标，不能做距离校验；请先补全详细地址并开通高德完成落图后再打卡");
        }
        Long photoFileId = validateCheckinPhoto(photoUrl, operator);

        int maxMeters = checkinMaxMeters();
        int distMeters = MktExtDataGateway.haversineMeters(checkinLng, checkinLat, meta.getLng(), meta.getLat());
        if (distMeters > maxMeters) {
            throw new BizException(400, "GPS 超距拦截：您当前位置距客户落图坐标 " + distMeters
                    + " 米（超过允许阈值 " + maxMeters + " 米），请到达客户现场后再打卡");
        }

        LocalDateTime now = LocalDateTime.now();
        int protectDays = visitProtectDays();
        LocalDateTime protectedUntil = now.plusDays(protectDays);
        String photoRef = "file:" + photoFileId;

        int updated = seaMetas.update(null, new LambdaUpdateWrapper<MktPublicSeaMeta>()
                .eq(MktPublicSeaMeta::getCustomerId, customerId)
                .eq(MktPublicSeaMeta::getClaimStatus, status)
                .set(MktPublicSeaMeta::getClaimStatus, "CLAIMED_ADMIN")
                .set(MktPublicSeaMeta::getClaimHolderType, "ADMIN")
                .set(MktPublicSeaMeta::getClaimHolderName, operator)
                .set(MktPublicSeaMeta::getFirstTouchedAt, now)
                .set(MktPublicSeaMeta::getFirstTouchNote, note.trim())
                .set(MktPublicSeaMeta::getBlockNote, note.trim())
                .set(MktPublicSeaMeta::getSlaDeadlineAt, protectedUntil)
                .set(MktPublicSeaMeta::getVisitProtectedUntil, protectedUntil)
                .set(MktPublicSeaMeta::getLastCheckinDistM, distMeters)
                .set(MktPublicSeaMeta::getLastCheckinAt, now)
                .set(MktPublicSeaMeta::getLastCheckinPhoto, photoRef)
                .set(MktPublicSeaMeta::getUpdatedAt, now));
        if (updated != 1) throw new BizException(409, "客户状态刚刚发生变化，请刷新后重试");

        jdbc.update("""
                INSERT INTO crm_public_sea_checkin
                  (customer_id, tenant_id, project_id, operator, checkin_lng, checkin_lat, distance_m, photo_url, visit_note, protected_until)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, customerId, meta.getTenantId(), meta.getProjectId(), operator,
                checkinLng, checkinLat, distMeters, photoRef, note.trim(), protectedUntil);

        customers.update(null, new LambdaUpdateWrapper<Customer>()
                .eq(Customer::getId, customerId).isNull(Customer::getReferrerId)
                .set(Customer::getOwner, operator));
        audit.log("public_sea.visit_checkin", "customer", customerId,
                "现场 " + distMeters + "m 照片打卡通过（" + photoRef + "），授予 " + protectDays + " 天保护期"
                        + ("CLAIMED_ADMIN".equals(status) && !operator.equals(meta.getClaimHolderName()) ? "；管理员接管自 " + meta.getClaimHolderName() : ""));

        return Map.of("customerId", customerId, "distanceMeters", distMeters,
                "visitProtectDays", protectDays, "visitProtectedUntil", protectedUntil);
    }

    /** 照片必须是本人 30 分钟内上传的图片附件，返回附件 ID。 */
    private Long validateCheckinPhoto(String photoUrl, String operator) {
        String ref = photoUrl == null ? "" : photoUrl.trim();
        if (!ref.matches("^file:\\d{1,18}$")) {
            throw new BizException(400, "请先现场拍照上传门头或待发包裹照片后再打卡");
        }
        Long fileId = Long.parseLong(ref.substring(5));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT create_by, create_time, content_type FROM sys_file WHERE id = ? AND deleted = 0 LIMIT 1", fileId);
        if (rows.isEmpty()) throw new BizException(400, "现场照片不存在，请重新拍照上传");
        Map<String, Object> f = rows.get(0);
        String createdBy = f.get("create_by") == null ? "" : String.valueOf(f.get("create_by"));
        String contentType = f.get("content_type") == null ? "" : String.valueOf(f.get("content_type"));
        if (!operator.equals(createdBy)) throw new BizException(403, "只能使用本人刚拍摄上传的现场照片");
        if (!contentType.toLowerCase(Locale.ROOT).startsWith("image/")) throw new BizException(400, "现场凭证必须是照片");
        Object ct = f.get("create_time");
        if (ct != null && toLdt(ct).isBefore(LocalDateTime.now().minusMinutes(CHECKIN_PHOTO_MAX_AGE_MINUTES))) {
            throw new BizException(400, "照片上传已超过 " + CHECKIN_PHOTO_MAX_AGE_MINUTES + " 分钟，请在现场重新拍照");
        }
        return fileId;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCheckins(Long customerId) {
        return jdbc.queryForList("""
                SELECT id, operator, distance_m AS distanceMeters, photo_url AS photoUrl,
                       visit_note AS visitNote, protected_until AS protectedUntil, created_at AS createdAt
                  FROM crm_public_sea_checkin
                 WHERE customer_id = ?
                 ORDER BY id DESC LIMIT 20
                """, customerId);
    }

    /** 提交首通核实纪要：解除 72h 超时自动弹回。 */
    @Transactional
    public void recordFirstTouch(Long customerId, String note, Integer estDailyOrders, String category) {
        if (!StringUtils.hasText(note) || note.trim().length() < 4) {
            throw new BizException(400, "请如实填写至少 4 个字的首通电话核实纪要");
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

    /** 主动释放回待认领公海或冷宫。 */
    @Transactional
    public void releaseToSea(Long customerId, String reason, boolean archiveCold) {
        if (!StringUtils.hasText(reason)) throw new BizException(400, "请填写释放原因或客户未成卡点");
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
                .set(MktPublicSeaMeta::getVisitProtectedUntil, null)
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

    /** 云仓小程序端（主账号专享）：查看本仓可认领热单与剩余配额。 */
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
                        OR (m.claim_status = 'UNCLAIMED' AND m.sea_source IN ('CALC_HOT', 'FEIYU_AD'))
                        OR (m.claim_status = 'CLAIMED_WH' AND m.claim_holder_id = ?))
                 ORDER BY CASE WHEN m.claim_status = 'CLAIMED_WH' THEN 0 ELSE 1 END, m.updated_at DESC
                """, wh.getTenantId(), wh.getProjectId(), wh.getId());

        String myWhIdStr = String.valueOf(wh.getId());
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> matched = allRows.stream().filter(r -> {
            if ("CLAIMED_WH".equals(r.get("claimStatus"))) return true;
            String feedIds = r.get("feedWarehouseIds") == null ? "" : String.valueOf(r.get("feedWarehouseIds")).trim();
            return feedIds.isEmpty() || Arrays.asList(feedIds.split(",")).contains(myWhIdStr);
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
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("warehouseId", wh.getId());
        out.put("warehouseName", wh.getName());
        out.put("slaHours", slaHours());
        out.put("dailyCap", dailyCap);
        out.put("dailyUsed", todayUsed);
        out.put("dailyLeft", Math.max(0, dailyCap - todayUsed));
        out.put("activeCap", activeCap);
        out.put("activeUsed", activeUsed);
        out.put("activeLeft", Math.max(0, activeCap - activeUsed));
        out.put("total", matched.size());
        out.put("records", matched.subList(from, to));
        return out;
    }

    /** 云仓主账号限时认领公海热单。 */
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
        return Map.of("customerId", customerId,
                "name", c == null ? "" : Optional.ofNullable(c.getName()).orElse(""),
                "contact", c == null ? "" : Optional.ofNullable(c.getContact()).orElse(""),
                "phone", c == null ? "" : Optional.ofNullable(c.getPhone()).orElse(""),
                "hoursLeft", slaHours());
    }

    @Transactional
    public void recordWarehouseTouch(Long customerId, String note) {
        MktWarehouse wh = whWorkspace.owner().warehouse();
        MktPublicSeaMeta meta = seaMetas.selectById(customerId);
        if (meta == null || !"CLAIMED_WH".equals(meta.getClaimStatus()) || !Objects.equals(meta.getClaimHolderId(), wh.getId())) {
            throw new BizException(403, "仅可为您本仓已认领的公海客户登记接洽反馈");
        }
        recordFirstTouch(customerId, note, null, null);
    }

    /** 定时任务：自动将超 72h 未首触达且不在 15 天地推保护期内的认领单弹回公海。 */
    @Transactional
    public int expireOverdueClaims(LocalDateTime now) {
        List<Long> dueIds = jdbc.queryForList("""
                SELECT customer_id FROM crm_public_sea_meta
                 WHERE claim_status IN ('CLAIMED_ADMIN', 'FEED_WH', 'CLAIMED_WH')
                   AND first_touched_at IS NULL
                   AND (visit_protected_until IS NULL OR visit_protected_until <= ?)
                   AND sla_deadline_at IS NOT NULL AND sla_deadline_at <= ?
                """, Long.class, now, now);
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

    /** 外部伙伴锁客释放回流联动。 */
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
            meta.setZoneCode(MktExtDataGateway.classifyZone(c.getName(), null));
            meta.setProvince("广东省");
            meta.setShopName(c.getBrandName());
            meta.setCategory(StringUtils.hasText(c.getIndustry()) ? c.getIndustry() : "综合电商");
            meta.setUrgencyLevel("A");
            meta.setUrgencyReason("外部伙伴锁客期满或主动释放回流，已有前期接洽基础");
            meta.setBlockNote("上轮释放原因：" + (StringUtils.hasText(reason) ? reason : "锁定期满未签约"));
            meta.setClaimStatus("UNCLAIMED");
            meta.setReleaseCount(0);
            meta.setDishonestCount(0);
            meta.setQccAligned(0);
            meta.setCreatedAt(now);
            meta.setUpdatedAt(now);
            seaMetas.insert(meta);
        } else if (!"ARCHIVED_COLD".equals(meta.getClaimStatus())) {
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
        LocalDateTime deadline = toLdt(slaObj);
        return (int) Math.max(0, Duration.between(now, deadline).toHours());
    }

    private static Integer calcVisitDaysLeft(Object visitObj, LocalDateTime now) {
        if (visitObj == null) return null;
        LocalDateTime until = toLdt(visitObj);
        if (until.isBefore(now)) return null;
        return (int) Math.max(1, Duration.between(now, until).toDays() + 1);
    }

    private static LocalDateTime toLdt(Object obj) {
        return obj instanceof LocalDateTime ldt ? ldt : java.sql.Timestamp.valueOf(String.valueOf(obj)).toLocalDateTime();
    }

    private static String maskPhone(String p) {
        if (!StringUtils.hasText(p) || p.length() < 7) return "认领解锁";
        return p.substring(0, 3) + "****" + p.substring(p.length() - 4);
    }

    private static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> role.equals(a.getAuthority()));
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
