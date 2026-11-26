package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktPublicSeaMeta;
import com.zhyq.park.marketing.entity.MktPublicSeaWashBatch;
import com.zhyq.park.marketing.mapper.MktPublicSeaMetaMapper;
import com.zhyq.park.marketing.mapper.MktPublicSeaWashBatchMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 公海池三道防撞洗数、类目 TOP500 店铺榜自动企查对齐（广东定向省钱锁+30天缓存）、高德落图与算价留资服务。
 */
@Service
@RequiredArgsConstructor
public class MktPublicSeaWashService {

    private static final int MAX_IMPORT_ROWS = 1000;
    private static final Set<String> VALID_SOURCES = Set.of(
            "CALC_HOT", "FEIYU_AD", "AMAP_POI", "CHANMAMA", "TUNGEE_JOB", "EXPIRE_RETURN", "PARK_CRM", "MANUAL_IMPORT");
    /** 广东各战区产业带基准轴心坐标（当加盟云仓未填详细门牌或未开高德时，按云仓所在城市/区域匹配最近仓） */
    private static final BigDecimal DEFAULT_GD_WH_LNG = new BigDecimal("113.348620");
    private static final BigDecimal DEFAULT_GD_WH_LAT = new BigDecimal("23.028540");
    private static final Map<String, BigDecimal[]> ZONE_WH_CENTROIDS = Map.of(
            "GZ", new BigDecimal[]{new BigDecimal("113.318500"), new BigDecimal("23.128600")},
            "DG", new BigDecimal[]{new BigDecimal("113.673120"), new BigDecimal("22.821950")},
            "FS", new BigDecimal[]{new BigDecimal("113.216840"), new BigDecimal("22.932180")},
            "SZ", new BigDecimal[]{new BigDecimal("114.065210"), new BigDecimal("22.658420")},
            "YD", new BigDecimal[]{new BigDecimal("116.165400"), new BigDecimal("23.302500")},
            "OTHER_GD", new BigDecimal[]{DEFAULT_GD_WH_LNG, DEFAULT_GD_WH_LAT}
    );
    private final Map<String, BigDecimal[]> whGeoCache = new java.util.concurrent.ConcurrentHashMap<>();

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final CustomerMapper customers;
    private final MktPublicSeaMetaMapper seaMetas;
    private final MktPublicSeaWashBatchMapper washBatches;
    private final MktCompetitionProtectionService protectionService;
    private final MktExtDataGateway extGateway;
    private final MktAuditService audit;
    private final PlatformTransactionManager txManager;
    /** 超过该行数的正式导入转后台执行。 */
    static final int ASYNC_THRESHOLD = 50;
    private final ExecutorService importWorker = new DelegatingSecurityContextExecutorService(
            Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "public-sea-import");
                t.setDaemon(true);
                return t;
            }));

    public record RawLeadRow(String name, String shopName, String platform, Integer rankNo,
                             String contact, String phone, String category, Integer estDailyOrders,
                             BigDecimal avgWeightKg, BigDecimal logisticsScore, String regionHint,
                             String address, BigDecimal presetLng, BigDecimal presetLat, String blockNote) {}

    /** 公开 30 秒云仓底价测算。 */
    @Transactional(readOnly = true)
    public Map<String, Object> estimatePublicQuote(Long projectId, String category, Integer dailyOrders, BigDecimal avgWeightKg) {
        Long effProject = resolveProjectId(projectId);
        Long tenantId = resolveTenantId(effProject);
        int daily = dailyOrders == null ? 500 : Math.max(10, Math.min(200_000, dailyOrders));
        BigDecimal weight = avgWeightKg == null ? new BigDecimal("0.50")
                : avgWeightKg.max(new BigDecimal("0.05")).min(new BigDecimal("30.00")).setScale(2, RoundingMode.HALF_UP);
        String cat = StringUtils.hasText(category) ? category.trim() : "女装服饰";

        List<BigDecimal> realTotals = loadRealWarehouseTotals(tenantId, effProject, weight);
        BigDecimal low;
        BigDecimal high;
        String rateSource;
        int matchedCount;
        if (!realTotals.isEmpty()) {
            Collections.sort(realTotals);
            low = realTotals.get(0).setScale(2, RoundingMode.HALF_UP);
            high = realTotals.get(realTotals.size() - 1).max(low.add(new BigDecimal("0.20"))).setScale(2, RoundingMode.HALF_UP);
            matchedCount = realTotals.size();
            rateSource = "REAL_RATE_CARD";
        } else {
            BigDecimal base = switch (cat) {
                case "个人洗护", "美妆个护" -> new BigDecimal("2.50");
                case "休闲食品" -> new BigDecimal("2.48");
                case "鞋靴箱包", "数码小家电" -> new BigDecimal("2.85");
                default -> new BigDecimal("2.25");
            };
            BigDecimal weightExtra = weight.subtract(new BigDecimal("0.50")).max(BigDecimal.ZERO).multiply(new BigDecimal("0.90"));
            BigDecimal volDisc = daily >= 1000 ? new BigDecimal("-0.18")
                    : daily >= 500 ? new BigDecimal("-0.08") : new BigDecimal("0.12");
            low = base.add(weightExtra).add(volDisc).max(new BigDecimal("1.92")).setScale(2, RoundingMode.HALF_UP);
            high = low.add(new BigDecimal("0.32")).setScale(2, RoundingMode.HALF_UP);
            Integer onlineWh = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM crm_warehouse WHERE tenant_id = ? AND (project_id = ? OR project_id IS NULL) AND join_status = 5 AND deleted = 0",
                    Integer.class, tenantId, effProject);
            matchedCount = onlineWh != null && onlineWh > 0 ? onlineWh : 3;
            rateSource = "PARK_BENCHMARK";
        }

        int packers = Math.max(1, (int) Math.ceil(daily / 350.0));
        BigDecimal selfMonthly = BigDecimal.valueOf(packers * 5800L)
                .add(BigDecimal.valueOf(daily * 30L).multiply(low.add(new BigDecimal("0.35"))));
        BigDecimal cloudMonthly = BigDecimal.valueOf(daily * 30L).multiply(low);
        long saveYearly = selfMonthly.subtract(cloudMonthly).multiply(BigDecimal.valueOf(12))
                .max(BigDecimal.valueOf(12000)).setScale(0, RoundingMode.HALF_UP).longValue();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectId", effProject);
        out.put("category", cat);
        out.put("dailyOrders", daily);
        out.put("avgWeightKg", weight);
        out.put("lowPrice", low);
        out.put("highPrice", high);
        out.put("matchedWarehouseCount", matchedCount);
        out.put("packersEquivalent", packers);
        out.put("estimatedYearlySaving", saveYearly);
        out.put("rateSource", rateSource);
        return out;
    }

    private List<BigDecimal> loadRealWarehouseTotals(Long tenantId, Long projectId, BigDecimal weightKg) {
        String weightKey = weightKg.compareTo(new BigDecimal("0.5")) <= 0 ? "0-0.5"
                : weightKg.compareTo(BigDecimal.ONE) <= 0 ? "0.5-1"
                : weightKg.compareTo(new BigDecimal("2")) <= 0 ? "1-2" : "2-3";
        List<String> cards = jdbc.queryForList("""
                SELECT CAST(r.card_json AS CHAR CHARACTER SET utf8mb4)
                  FROM crm_quote_rate_card r
                  JOIN crm_warehouse w ON w.id = r.warehouse_id AND w.tenant_id = r.tenant_id AND w.deleted = 0
                 WHERE r.tenant_id = ? AND r.project_id = ? AND w.join_status = 5
                """, String.class, tenantId, projectId);
        List<BigDecimal> totals = new ArrayList<>();
        for (String raw : cards) {
            try {
                JsonNode root = json.readTree(raw);
                JsonNode fields = root.path("fields");
                BigDecimal pack = decimalOr(fields.get("packBatch"), fields.get("packSingle"));
                BigDecimal mat = decimalOr(fields.get("material"), BigDecimal.ZERO);
                if (pack == null) continue;
                for (JsonNode carrier : root.path("carriers")) {
                    JsonNode rateNode = carrier.path("rates").get(weightKey);
                    if (rateNode != null && rateNode.isNumber()) totals.add(pack.add(mat).add(rateNode.decimalValue()));
                }
            } catch (Exception ignored) {}
        }
        return totals;
    }

    private static BigDecimal decimalOr(JsonNode primary, Object fallback) {
        if (primary != null && primary.isNumber()) return primary.decimalValue();
        if (fallback instanceof JsonNode jn && jn.isNumber()) return jn.decimalValue();
        if (fallback instanceof BigDecimal bd) return bd;
        return null;
    }

    /** 公开 30 秒算价器主动授权手机号入池。 */
    @Transactional
    public Map<String, Object> submitPublicCalcLead(Map<String, Object> body) {
        String shopOrName = cleanText(body.get("shopName"), 100);
        String phone = cleanPhone(body.get("phone"));
        if (!StringUtils.hasText(shopOrName)) throw new BizException(400, "请填写您的店铺名或企业简称");
        if (!StringUtils.hasText(phone) || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BizException(400, "请填写有效的 11 位联系手机号");
        }
        Long projectId = resolveProjectId(asLong(body.get("projectId")));
        String cat = StringUtils.hasText(cleanText(body.get("category"), 64)) ? cleanText(body.get("category"), 64) : "女装服饰";
        Integer daily = asInt(body.get("dailyOrders"), 500);
        BigDecimal weight = asDecimal(body.get("avgWeightKg"), new BigDecimal("0.50"));
        Map<String, Object> quote = estimatePublicQuote(projectId, cat, daily, weight);
        BigDecimal low = (BigDecimal) quote.get("lowPrice");
        BigDecimal high = (BigDecimal) quote.get("highPrice");

        RawLeadRow row = new RawLeadRow(shopOrName, shopOrName, "抖音", null,
                cleanText(body.get("contact"), 64), phone, cat, daily, weight, null,
                cleanText(body.get("region"), 64), cleanText(body.get("address"), 200), null, null,
                "30秒算价器主动留资（测算一口价 ¥" + low + "~" + high + "/单，申领300单免打包费试发券）");
        Map<String, Object> wash = washRowsInternal(projectId, "CALC_HOT", "30秒算价器线上留资",
                List.of(row), false, low, high, MktExtDataGateway.AlignPolicy.INBOUND, "public-calc");
        Map<String, Object> resp = new LinkedHashMap<>(quote);
        resp.put("accepted", true);
        resp.put("inserted", ((Number) wash.get("insertedRows")).intValue() > 0);
        return resp;
    }

    /** 后台上传类目店铺榜 / 拓客名单 Excel 或 CSV 洗数对齐入池。 */
    @Transactional
    public Map<String, Object> washFile(Long projectId, String seaSource, MultipartFile file, boolean dryRun) {
        if (file == null || file.isEmpty()) throw new BizException(400, "请选择要清洗导入的榜单或名单文件");
        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("shop-rank.xlsx");
        List<RawLeadRow> rows = parseFileRows(file, fileName);
        if (rows.isEmpty()) throw new BizException(400, "文件中未识别到有效店铺或企业行，请核对表头");
        if (!dryRun && rows.size() > ASYNC_THRESHOLD) {
            return startAsyncWash(projectId, normalizeSource(seaSource), fileName, rows, MktAuditService.currentOperator());
        }
        return washRowsInternal(resolveProjectId(projectId), normalizeSource(seaSource), fileName,
                rows, dryRun, null, null, MktExtDataGateway.AlignPolicy.BATCH, MktAuditService.currentOperator());
    }

    /** 后台提交 JSON 榜单或文本行洗数对齐入池。 */
    @Transactional
    public Map<String, Object> washJsonRows(Long projectId, String seaSource, String fileName, List<Map<String, Object>> rawList, boolean dryRun) {
        if (rawList == null || rawList.isEmpty()) throw new BizException(400, "请提供至少 1 条待清洗店铺或企业名单");
        if (rawList.size() > MAX_IMPORT_ROWS) throw new BizException(400, "单次最多支持 " + MAX_IMPORT_ROWS + " 条");
        List<RawLeadRow> rows = rawList.stream().map(m -> new RawLeadRow(
                cleanText(firstNonNull(m, "name", "companyName"), 100),
                cleanText(firstNonNull(m, "shopName", "shop", "name"), 120),
                Optional.ofNullable(cleanText(m.get("platform"), 24)).orElse("抖音"),
                asInt(m.get("rankNo"), null),
                cleanText(m.get("contact"), 64),
                cleanPhone(m.get("phone")),
                cleanText(firstNonNull(m, "category", "industry"), 64),
                asInt(firstNonNull(m, "estDailyOrders", "dailyOrders", "monthlySalesDiv30"), null),
                asDecimal(firstNonNull(m, "avgWeightKg", "weight"), null),
                asDecimal(m.get("logisticsScore"), null),
                cleanText(firstNonNull(m, "region", "province", "city"), 64),
                cleanText(m.get("address"), 220),
                asDecimalScale(m.get("lng"), 6),
                asDecimalScale(m.get("lat"), 6),
                cleanText(firstNonNull(m, "blockNote", "remark"), 450)
        )).toList();
        String effFileName = StringUtils.hasText(fileName) ? fileName.trim() : "类目榜单批量对齐";
        if (!dryRun && rows.size() > ASYNC_THRESHOLD) {
            return startAsyncWash(projectId, normalizeSource(seaSource), effFileName, rows, MktAuditService.currentOperator());
        }
        return washRowsInternal(resolveProjectId(projectId), normalizeSource(seaSource),
                effFileName,
                rows, dryRun, null, null, MktExtDataGateway.AlignPolicy.BATCH, MktAuditService.currentOperator());
    }

    /** 单店名反查背后广东公司+地址并过三道防撞闸写入公海。 */
    @Transactional
    public Map<String, Object> lookupShopAndIngest(Long projectId, String shopName, String platform, String category, boolean forceUnlockRegion) {
        if (!StringUtils.hasText(shopName)) throw new BizException(400, "请输入要反查的网店名称或企业名称");
        RawLeadRow row = new RawLeadRow(null, shopName.trim(), StringUtils.hasText(platform) ? platform.trim() : "抖音",
                null, null, null, StringUtils.hasText(category) ? category.trim() : "综合电商",
                null, null, null, null, null, null, null, "单店名称企查对齐入池：" + shopName.trim());
        return washRowsInternal(resolveProjectId(projectId), "CHANMAMA", "单店反查:" + shopName.trim(),
                List.of(row), false, null, null,
                forceUnlockRegion ? MktExtDataGateway.AlignPolicy.FORCE : MktExtDataGateway.AlignPolicy.SINGLE,
                MktAuditService.currentOperator());
    }

    /** 高德周边 10km 商圈 POI 扫楼雷达：搜出园区周边电商园/服饰美妆企业并过防撞入公海。 */
    @Transactional
    public Map<String, Object> ingestAmapRadar(Long projectId, BigDecimal centerLng, BigDecimal centerLat, Integer radiusMeters, String keywords) {
        Long effProject = resolveProjectId(projectId);
        int radius = radiusMeters == null ? 10_000 : radiusMeters;
        List<MktExtDataGateway.PoiCandidate> pois = extGateway.scanAroundPois(centerLng, centerLat, radius, keywords);
        if (pois.isEmpty()) {
            return Map.of("scannedPois", 0, "insertedRows", 0, "message", "指定半径内未检索到匹配的电商园区或企业 POI");
        }
        List<RawLeadRow> rows = pois.stream().map(p -> new RawLeadRow(
                p.name(), null, "线下产业带", null, null, cleanPhone(p.tel()),
                p.typeDesc(), null, null, null, p.city() + p.district(), p.address(),
                p.lng(), p.lat(), "高德周边 " + (radius / 1000) + "km 扫楼雷达发现：" + p.address()
        )).toList();
        Map<String, Object> res = washRowsInternal(effProject, "AMAP_POI", "高德周边POI扫楼雷达",
                rows, false, null, null, MktExtDataGateway.AlignPolicy.BATCH, MktAuditService.currentOperator());
        Map<String, Object> out = new LinkedHashMap<>(res);
        out.put("scannedPois", pois.size());
        return out;
    }

    /** 巨量引擎飞鱼 / 腾讯广告免费 Webhook 单条实时清洗入池。 */
    @Transactional
    public Map<String, Object> ingestAdWebhookLead(Long projectId, String channel, String shopOrName, String phone,
                                                   String category, Integer estDaily, String region, String note) {
        RawLeadRow row = new RawLeadRow(shopOrName, shopOrName, "抖音", null, "广告留资负责人", cleanPhone(phone),
                StringUtils.hasText(category) ? category : "女装服饰", estDaily, null, null,
                region, region, null, null, note);
        return washRowsInternal(resolveProjectId(projectId), "FEIYU_AD", channel + "免费Webhook直推",
                List.of(row), false, null, null, MktExtDataGateway.AlignPolicy.INBOUND, "webhook-" + channel.toLowerCase(Locale.ROOT));
    }

    /** 免费防撞闸结果：type 为空表示通过；existing 为命中的已有（可更新）客户。 */
    private record GateResult(String type, String reason, Customer existing) {
        static GateResult pass(Customer existing) { return new GateResult(null, null, existing); }
        boolean blocked() { return type != null; }
    }

    /** 单批洗数的共享上下文。 */
    private record WashContext(Long projectId, Long tenantId, String seaSource, boolean dryRun,
                               BigDecimal calcLow, BigDecimal calcHigh, MktExtDataGateway.AlignPolicy policy,
                               boolean regionSkippable, boolean lockOn, Map<String, Set<String>> batchPhoneCompanies) {}

    /** 通过全部检查、待写入的一行。 */
    private record RowPlan(RawLeadRow row, MktExtDataGateway.CompanyProfile aligned, String finalName,
                           String finalPhone, String finalAddr, String zoneCode, Customer existing) {}

    /** 批次累计统计。 */
    private static final class WashStats {
        int inserted, blockedPrivate, blockedHmac, blockedAgency, skippedOutProv, cacheHits, apiCalls, processed, errors;
        Long lastCustomerId;
        final List<Map<String, Object>> details = new ArrayList<>();
    }

    /**
     * 本地免费防撞闸（不产生任何接口费用）：
     * ① 同号码关联 >3 家企业 → 代账号剔除；② 命中加盟云仓保密老客 → 跳过；
     * ③ 按名称/电话、再按统一社会信用代码查已有客户：在锁/已签约 → 跳过，冷宫 → 跳过。
     */
    private GateResult freeGates(Long tenantId, String name, String shopName, String phone, String creditCode,
                                 Map<String, Set<String>> batchPhoneCompanies) {
        if (StringUtils.hasText(phone)) {
            int dbSamePhoneDiffName = Optional.ofNullable(jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT name) FROM crm_customer WHERE tenant_id = ? AND phone = ? AND name <> ? AND deleted = 0",
                    Integer.class, tenantId, phone, name)).orElse(0);
            int batchDistinct = batchPhoneCompanies.getOrDefault(phone, Set.of()).size();
            if (dbSamePhoneDiffName + batchDistinct > 3) {
                return new GateResult("AGENCY_PHONE", "同一号码关联超 3 家企业，识别为代理记账号剔除", null);
            }
        }
        if (isWarehouseHmacProtected(tenantId, name, shopName)) {
            return new GateResult("WH_HMAC", "命中加盟云仓竞争保护白名单，自动跳过护客", null);
        }
        LambdaQueryWrapper<Customer> existQuery = new LambdaQueryWrapper<Customer>().eq(Customer::getTenantId, tenantId);
        if (StringUtils.hasText(phone)) {
            existQuery.and(q -> q.eq(Customer::getName, name).or().eq(Customer::getPhone, phone));
        } else {
            existQuery.eq(Customer::getName, name);
        }
        Customer existing = customers.selectOne(existQuery.last("LIMIT 1"));
        if (existing == null && StringUtils.hasText(creditCode)) {
            List<Long> ids = jdbc.queryForList(
                    "SELECT customer_id FROM crm_public_sea_meta WHERE tenant_id = ? AND credit_code = ? LIMIT 1",
                    Long.class, tenantId, creditCode.trim());
            if (ids != null && !ids.isEmpty() && ids.get(0) != null) existing = customers.selectById(ids.get(0));
        }
        if (existing != null) {
            if (isPrivateProtected(existing)) {
                return new GateResult("PRIVATE_LOCK", "客户处于外部伙伴保护期或已签约，严禁冲单", null);
            }
            MktPublicSeaMeta meta = seaMetas.selectById(existing.getId());
            if (meta != null && "ARCHIVED_COLD".equals(meta.getClaimStatus())) {
                return new GateResult("COLD_ARCHIVE", "命中冷宫归档库，跳过重复外呼", null);
            }
        }
        return GateResult.pass(existing);
    }

    private static void countBlocked(WashStats st, String type) {
        switch (type) {
            case "WH_HMAC" -> st.blockedHmac++;
            case "PRIVATE_LOCK" -> st.blockedPrivate++;
            default -> st.blockedAgency++;
        }
    }

    private WashContext newContext(Long projectId, String seaSource, List<RawLeadRow> rows, boolean dryRun,
                                   BigDecimal calcLow, BigDecimal calcHigh, MktExtDataGateway.AlignPolicy policy) {
        MktExtDataGateway.AlignPolicy effective = dryRun ? MktExtDataGateway.AlignPolicy.PREVIEW : policy;
        boolean regionSkippable = effective == MktExtDataGateway.AlignPolicy.BATCH
                || effective == MktExtDataGateway.AlignPolicy.SINGLE || effective == MktExtDataGateway.AlignPolicy.PREVIEW;
        Map<String, Set<String>> batchPhoneCompanies = new HashMap<>();
        for (RawLeadRow r : rows) {
            String nm = StringUtils.hasText(r.name()) ? r.name() : r.shopName();
            if (StringUtils.hasText(r.phone()) && StringUtils.hasText(nm)) {
                batchPhoneCompanies.computeIfAbsent(r.phone(), k -> new HashSet<>()).add(nm);
            }
        }
        return new WashContext(projectId, resolveTenantId(projectId), seaSource, dryRun, calcLow, calcHigh,
                effective, regionSkippable, extGateway.isGuangdongLockEnabled(), batchPhoneCompanies);
    }

    /** 单行检查（不写库）：免费地区判断 → 免费防撞闸 → 付费工商对齐 → 对齐后再过一次防撞闸。被拦截返回 null。 */
    private RowPlan planRow(WashContext ctx, RawLeadRow r, int lineNo, WashStats st) {
        String seedName = StringUtils.hasText(r.name()) ? r.name() : r.shopName();
        if (!StringUtils.hasText(seedName)) {
            st.blockedAgency++;
            addDetail(st.details, lineNo, "空行", "INVALID", "店铺名与企业名均为空，已跳过");
            return null;
        }
        String regionText = (r.regionHint() == null ? "" : r.regionHint()) + " " + (r.address() == null ? "" : r.address());

        if (ctx.regionSkippable() && ctx.lockOn()
                && "OUT_GD".equals(MktExtDataGateway.classifyZone(regionText + " " + seedName, null))) {
            st.skippedOutProv++;
            addDetail(st.details, lineNo, maskName(seedName), "OUT_GUANGDONG", "广东定向省钱锁：识别为外省主体，跳过且不扣接口费");
            return null;
        }

        GateResult gate = freeGates(ctx.tenantId(), seedName, r.shopName(), r.phone(), null, ctx.batchPhoneCompanies());
        if (gate.blocked()) {
            countBlocked(st, gate.type());
            addDetail(st.details, lineNo, maskName(seedName), gate.type(), gate.reason());
            return null;
        }

        MktExtDataGateway.CompanyProfile aligned = extGateway.alignCompany(ctx.tenantId(), seedName, regionText, ctx.policy());
        if ("SKIPPED_OUT_PROVINCE".equals(aligned.status())) {
            st.skippedOutProv++;
            addDetail(st.details, lineNo, maskName(seedName), "OUT_GUANGDONG", "广东定向省钱锁：识别为外省主体，跳过且不扣接口费");
            return null;
        }
        if ("CACHE_HIT".equals(aligned.status()) || "CACHE_NOT_FOUND".equals(aligned.status())) st.cacheHits++;
        else if ("API_ALIGNED".equals(aligned.status()) || "NOT_FOUND".equals(aligned.status())) st.apiCalls++;

        String finalName = StringUtils.hasText(aligned.companyName()) ? aligned.companyName() : seedName;
        String finalPhone = StringUtils.hasText(r.phone()) ? r.phone() : cleanPhone(aligned.phone());
        String finalAddr = StringUtils.hasText(r.address()) ? r.address() : aligned.address();
        String zoneCode = MktExtDataGateway.classifyZone(
                (finalAddr == null ? "" : finalAddr) + " " + regionText + " " + finalName, aligned.creditCode());
        if (ctx.regionSkippable() && ctx.lockOn() && "OUT_GD".equals(zoneCode)) {
            st.skippedOutProv++;
            addDetail(st.details, lineNo, maskName(finalName), "OUT_GUANGDONG", "工商信用代码/地址属外省，省钱锁跳过");
            return null;
        }

        Customer existing = gate.existing();
        boolean needRegate = !Objects.equals(finalName, seedName) || !Objects.equals(finalPhone, r.phone())
                || StringUtils.hasText(aligned.creditCode());
        if (needRegate) {
            GateResult gate2 = freeGates(ctx.tenantId(), finalName, r.shopName(), finalPhone, aligned.creditCode(), ctx.batchPhoneCompanies());
            if (gate2.blocked()) {
                countBlocked(st, gate2.type());
                addDetail(st.details, lineNo, maskName(finalName), gate2.type(), gate2.reason());
                return null;
            }
            if (gate2.existing() != null) existing = gate2.existing();
        }

        if (!StringUtils.hasText(finalPhone) && !"AMAP_POI".equals(ctx.seaSource()) && !StringUtils.hasText(r.shopName())) {
            st.blockedAgency++;
            addDetail(st.details, lineNo, maskName(finalName), "NO_PHONE", "缺少手机号且未对齐到公开年报电话");
            return null;
        }
        return new RowPlan(r, aligned, finalName, finalPhone, finalAddr, zoneCode, existing);
    }

    /** 写入一行（客户主档 + 公海画像 + 店铺矩阵）。预检模式只计数不写。 */
    private void applyRow(WashContext ctx, RowPlan p, WashStats st) {
        RawLeadRow r = p.row();
        if (ctx.dryRun()) { st.inserted++; return; }
        if (p.existing() != null) {
            upsertSeaMetaAndShop(p.existing().getId(), ctx.tenantId(), ctx.projectId(), ctx.seaSource(), r, p.aligned(),
                    p.finalAddr(), p.zoneCode(), ctx.calcLow(), ctx.calcHigh());
            st.lastCustomerId = p.existing().getId();
            st.inserted++;
            return;
        }
        Customer c = new Customer();
        c.setTenantId(ctx.tenantId());
        c.setProjectId(ctx.projectId());
        c.setName(p.finalName());
        c.setBrandName(r.shopName());
        c.setContact(StringUtils.hasText(r.contact()) ? r.contact()
                : StringUtils.hasText(p.aligned().legalPerson()) ? p.aligned().legalPerson() : "电商负责人");
        c.setPhone(StringUtils.hasText(p.finalPhone()) ? p.finalPhone() : "");
        c.setIndustry(StringUtils.hasText(r.category()) ? r.category() : "综合电商");
        c.setDemandArea(r.estDailyOrders() != null ? "日估" + r.estDailyOrders() + "单" : "榜单/扫楼待核");
        c.setIntakeRemark(r.blockNote());
        c.setRemark(r.blockNote());
        c.setServiceType(3);
        c.setBizLine(2);
        c.setStatus(1);
        c.setReferrerId(null);
        c.setWarehouseAssignmentStatus(0);
        c.setOwner("内部公海待领");
        c.setAttributionNote("内部公海入池:" + ctx.seaSource());
        customers.insert(c);
        upsertSeaMetaAndShop(c.getId(), ctx.tenantId(), ctx.projectId(), ctx.seaSource(), r, p.aligned(),
                p.finalAddr(), p.zoneCode(), ctx.calcLow(), ctx.calcHigh());
        st.lastCustomerId = c.getId();
        st.inserted++;
    }

    private static String newBatchNo() {
        return "WASH-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + "-" + (100 + new Random().nextInt(900));
    }

    /** 同步洗数（小批量 / 单条 / 预检）：在调用方事务内完成。 */
    private Map<String, Object> washRowsInternal(Long projectId, String seaSource, String fileName, List<RawLeadRow> rows,
                                                 boolean dryRun, BigDecimal calcLow, BigDecimal calcHigh,
                                                 MktExtDataGateway.AlignPolicy policy, String operator) {
        WashContext ctx = newContext(projectId, seaSource, rows, dryRun, calcLow, calcHigh, policy);
        WashStats st = new WashStats();
        int lineNo = 0;
        for (RawLeadRow r : rows) {
            lineNo++;
            RowPlan plan = planRow(ctx, r, lineNo, st);
            if (plan != null) applyRow(ctx, plan, st);
            st.processed++;
        }
        String batchNo = newBatchNo();
        if (!dryRun) {
            MktPublicSeaWashBatch batch = new MktPublicSeaWashBatch();
            batch.setTenantId(ctx.tenantId());
            batch.setProjectId(projectId);
            batch.setBatchNo(batchNo);
            batch.setSeaSource(seaSource);
            batch.setFileName(fileName);
            batch.setTotalRows(rows.size());
            fillBatchStats(batch, st);
            batch.setOperator(operator);
            batch.setStatus("DONE");
            batch.setCreatedAt(LocalDateTime.now());
            batch.setFinishedAt(LocalDateTime.now());
            washBatches.insert(batch);
            audit.log("public_sea.wash_import", "public_sea", batch.getId(),
                    "公海洗数对齐 " + batchNo + " 净入 " + st.inserted + " 户，外省跳过 " + st.skippedOutProv + " 行");
        }
        return resultMap(batchNo, seaSource, fileName, rows.size(), dryRun, st, "DONE");
    }

    private void fillBatchStats(MktPublicSeaWashBatch batch, WashStats st) {
        batch.setInsertedRows(st.inserted);
        batch.setBlockedPrivateLock(st.blockedPrivate);
        batch.setBlockedWhHmac(st.blockedHmac);
        batch.setBlockedDupAgency(st.blockedAgency);
        batch.setSkippedOutProvince(st.skippedOutProv);
        batch.setQccCacheHits(st.cacheHits);
        batch.setQccApiCalls(st.apiCalls);
        batch.setProcessedRows(st.processed);
        try { batch.setBlockedDetailsJson(json.writeValueAsString(st.details)); } catch (Exception ignored) {}
    }

    private static Map<String, Object> resultMap(String batchNo, String seaSource, String fileName, int total,
                                                 boolean dryRun, WashStats st, String status) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dryRun", dryRun);
        out.put("async", false);
        out.put("status", status);
        out.put("batchNo", batchNo);
        out.put("seaSource", seaSource);
        out.put("fileName", fileName);
        out.put("totalRows", total);
        out.put("processedRows", st.processed);
        out.put("insertedRows", st.inserted);
        out.put("blockedPrivateLock", st.blockedPrivate);
        out.put("blockedWhHmac", st.blockedHmac);
        out.put("blockedDupAgency", st.blockedAgency);
        out.put("skippedOutProvince", st.skippedOutProv);
        out.put("qccCacheHits", st.cacheHits);
        out.put("qccApiCalls", st.apiCalls);
        out.put("errorRows", st.errors);
        out.put("lastCustomerId", st.lastCustomerId);
        out.put("blockedDetails", st.details);
        return out;
    }

    /**
     * 大批量导入改后台执行：先在独立事务里登记 RUNNING 批次并立即返回批次号；
     * 后台逐行处理——外部接口调用不占数据库事务，每行写库单独一个短事务，单行失败不影响其它行；每 20 行回写一次进度。
     */
    Map<String, Object> startAsyncWash(Long projectId, String seaSource, String fileName, List<RawLeadRow> rows, String operator) {
        Long effProject = resolveProjectId(projectId);
        Long tenantId = resolveTenantId(effProject);
        String batchNo = newBatchNo();
        MktPublicSeaWashBatch batch = new MktPublicSeaWashBatch();
        batch.setTenantId(tenantId);
        batch.setProjectId(effProject);
        batch.setBatchNo(batchNo);
        batch.setSeaSource(seaSource);
        batch.setFileName(fileName);
        batch.setTotalRows(rows.size());
        fillBatchStats(batch, new WashStats());
        batch.setOperator(operator);
        batch.setStatus("RUNNING");
        batch.setCreatedAt(LocalDateTime.now());
        TransactionTemplate requiresNew = new TransactionTemplate(txManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNew.executeWithoutResult(s -> washBatches.insert(batch));
        Long batchId = batch.getId();
        List<RawLeadRow> snapshot = List.copyOf(rows);
        importWorker.submit(() -> runAsyncWash(batchId, batchNo, effProject, seaSource, snapshot));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("async", true);
        out.put("status", "RUNNING");
        out.put("batchNo", batchNo);
        out.put("totalRows", rows.size());
        out.put("processedRows", 0);
        out.put("message", "共 " + rows.size() + " 行，已转为后台处理，可在本页查看进度");
        return out;
    }

    void runAsyncWash(Long batchId, String batchNo, Long projectId, String seaSource, List<RawLeadRow> rows) {
        WashStats st = new WashStats();
        TransactionTemplate tx = new TransactionTemplate(txManager);
        try {
            WashContext ctx = newContext(projectId, seaSource, rows, false, null, null, MktExtDataGateway.AlignPolicy.BATCH);
            int lineNo = 0;
            for (RawLeadRow r : rows) {
                lineNo++;
                try {
                    RowPlan plan = planRow(ctx, r, lineNo, st);
                    if (plan != null) tx.executeWithoutResult(s -> applyRow(ctx, plan, st));
                } catch (Exception rowError) {
                    st.errors++;
                    addDetail(st.details, lineNo, "第" + lineNo + "行", "ERROR", "处理失败已跳过：" + rowError.getMessage());
                }
                st.processed++;
                if (st.processed % 20 == 0) writeProgress(batchId, st, "RUNNING", null);
            }
            writeProgress(batchId, st, "DONE", null);
            audit.log("public_sea.wash_import", "public_sea", batchId,
                    "后台洗数 " + batchNo + " 完成：净入 " + st.inserted + " 户，外省跳过 " + st.skippedOutProv + " 行，失败 " + st.errors + " 行");
        } catch (Exception e) {
            writeProgress(batchId, st, "FAILED", e.getMessage());
        }
    }

    private void writeProgress(Long batchId, WashStats st, String status, String errorMsg) {
        String detailsJson = null;
        try { detailsJson = json.writeValueAsString(st.details); } catch (Exception ignored) {}
        jdbc.update("""
                UPDATE crm_public_sea_wash_batch
                   SET processed_rows = ?, inserted_rows = ?, blocked_private_lock = ?, blocked_wh_hmac = ?,
                       blocked_dup_agency = ?, skipped_out_province = ?, qcc_cache_hits = ?, qcc_api_calls = ?,
                       blocked_details_json = ?, status = ?, error_msg = ?,
                       finished_at = CASE WHEN ? = 'RUNNING' THEN NULL ELSE NOW() END
                 WHERE id = ?
                """, st.processed, st.inserted, st.blockedPrivate, st.blockedHmac, st.blockedAgency, st.skippedOutProv,
                st.cacheHits, st.apiCalls, detailsJson, status,
                errorMsg == null ? null : (errorMsg.length() > 250 ? errorMsg.substring(0, 250) : errorMsg),
                status, batchId);
    }

    /** 查询批次进度（后台导入轮询用；自动将超过 30 分钟无响应的僵死 RUNNING 批次标为中断）。 */
    @Transactional
    public Map<String, Object> getWashBatch(String batchNo) {
        recoverStaleRunningBatches();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT batch_no AS batchNo, sea_source AS seaSource, file_name AS fileName, status,
                       total_rows AS totalRows, processed_rows AS processedRows, inserted_rows AS insertedRows,
                       blocked_private_lock AS blockedPrivateLock, blocked_wh_hmac AS blockedWhHmac,
                       blocked_dup_agency AS blockedDupAgency, skipped_out_province AS skippedOutProvince,
                       qcc_cache_hits AS qccCacheHits, qcc_api_calls AS qccApiCalls, error_msg AS errorMsg,
                       CAST(blocked_details_json AS CHAR CHARACTER SET utf8mb4) AS blockedDetailsJson,
                       created_at AS createdAt, finished_at AS finishedAt
                  FROM crm_public_sea_wash_batch
                 WHERE batch_no = ? LIMIT 1
                """, batchNo);
        if (rows.isEmpty()) throw new BizException(404, "洗数批次不存在");
        Map<String, Object> out = new LinkedHashMap<>(rows.get(0));
        Object raw = out.remove("blockedDetailsJson");
        try {
            out.put("blockedDetails", raw == null ? List.of() : json.readValue(String.valueOf(raw), List.class));
        } catch (Exception e) {
            out.put("blockedDetails", List.of());
        }
        return out;
    }

    /** 最近 20 条洗数导入批次历史（刷新页面后仍可查看后台导入进度与结果）。 */
    @Transactional
    public List<Map<String, Object>> listWashBatches(Long projectId) {
        recoverStaleRunningBatches();
        Long effProject = resolveProjectId(projectId);
        Long tenantId = resolveTenantId(effProject);
        try {
            return jdbc.queryForList("""
                    SELECT batch_no AS batchNo, sea_source AS seaSource, file_name AS fileName, status,
                           total_rows AS totalRows, processed_rows AS processedRows, inserted_rows AS insertedRows,
                           skipped_out_province AS skippedOutProvince, qcc_cache_hits AS qccCacheHits,
                           qcc_api_calls AS qccApiCalls, error_msg AS errorMsg, operator,
                           created_at AS createdAt, finished_at AS finishedAt
                      FROM crm_public_sea_wash_batch
                     WHERE tenant_id = ? AND (? IS NULL OR project_id = ? OR project_id IS NULL)
                     ORDER BY id DESC LIMIT 20
                    """, tenantId, effProject, effProject);
        } catch (Exception e) {
            return List.of();
        }
    }

    private void recoverStaleRunningBatches() {
        try {
            jdbc.update("""
                    UPDATE crm_public_sea_wash_batch
                       SET status = 'FAILED',
                           error_msg = '服务重启或后台任务超过30分钟中断，可重新上传未入池名单（已有记录自动去重）',
                           finished_at = NOW()
                     WHERE status = 'RUNNING' AND created_at < DATE_SUB(NOW(), INTERVAL 30 MINUTE)
                    """);
        } catch (Exception ignored) { /* 兼容单测 Mock */ }
    }

    @PreDestroy
    void shutdownImportWorker() {
        importWorker.shutdown();
    }

    private void upsertSeaMetaAndShop(Long customerId, Long tenantId, Long projectId, String seaSource,
                                      RawLeadRow r, MktExtDataGateway.CompanyProfile aligned,
                                      String finalAddr, String zoneCode, BigDecimal calcLow, BigDecimal calcHigh) {
        MktPublicSeaMeta existingMeta = seaMetas.selectById(customerId);
        boolean isNew = (existingMeta == null);
        final MktPublicSeaMeta meta = isNew ? new MktPublicSeaMeta() : existingMeta;
        if (isNew) {
            meta.setCustomerId(customerId);
            meta.setTenantId(tenantId);
            meta.setProjectId(projectId);
            meta.setClaimStatus("UNCLAIMED");
            meta.setReleaseCount(0);
            meta.setDishonestCount(0);
            meta.setQccAligned(0);
            meta.setCreatedAt(LocalDateTime.now());
        }
        boolean activelyFollowed = !isNew
                && Set.of("CLAIMED_ADMIN", "CLAIMED_WH", "SIGNED").contains(meta.getClaimStatus());
        if (!activelyFollowed) {
            meta.setSeaSource(seaSource);
        }
        meta.setZoneCode(zoneCode);
        meta.setProvince(StringUtils.hasText(aligned.province()) ? aligned.province()
                : (MktExtDataGateway.GD_ZONES.contains(zoneCode) ? "广东省" : null));
        if (StringUtils.hasText(aligned.city())) meta.setCity(aligned.city());
        if (StringUtils.hasText(aligned.district())) meta.setDistrict(aligned.district());
        if (StringUtils.hasText(r.shopName())) meta.setShopName(r.shopName());
        if (StringUtils.hasText(r.category())) meta.setCategory(r.category());
        if (r.estDailyOrders() != null) meta.setEstDailyOrders(r.estDailyOrders());
        if (r.avgWeightKg() != null) meta.setAvgWeightKg(r.avgWeightKg());
        if (calcLow != null) meta.setCalcLowPrice(calcLow);
        if (calcHigh != null) meta.setCalcHighPrice(calcHigh);
        if (r.logisticsScore() != null) meta.setLogisticsScore(r.logisticsScore());
        if (StringUtils.hasText(r.blockNote()) && (!activelyFollowed || !StringUtils.hasText(meta.getBlockNote()))) {
            meta.setBlockNote(r.blockNote());
        }

        if ("CACHE_HIT".equals(aligned.status()) || "API_ALIGNED".equals(aligned.status())) {
            meta.setQccAligned(1);
            meta.setCreditCode(aligned.creditCode());
            meta.setLegalPerson(aligned.legalPerson());
            meta.setRegCapital(aligned.regCapital());
            meta.setInsuredCount(aligned.insuredCount());
            meta.setDishonestCount(aligned.dishonestCount());
        }

        // 高德地理编码落坐标并测算距最近云仓距离
        if (r.presetLng() != null && r.presetLat() != null) {
            meta.setLng(r.presetLng());
            meta.setLat(r.presetLat());
        } else if (StringUtils.hasText(finalAddr) && (meta.getLng() == null || meta.getLat() == null)) {
            extGateway.geocodeAddress(finalAddr, meta.getCity()).ifPresent(geo -> {
                meta.setLng(geo.lng());
                meta.setLat(geo.lat());
                meta.setAddress(geo.formattedAddress());
                if (StringUtils.hasText(geo.city())) meta.setCity(geo.city());
                if (StringUtils.hasText(geo.district())) meta.setDistrict(geo.district());
                if (StringUtils.hasText(geo.buildingName())) meta.setBuildingName(geo.buildingName());
            });
        }
        if (StringUtils.hasText(finalAddr) && !StringUtils.hasText(meta.getAddress())) {
            meta.setAddress(finalAddr);
        }
        if (!StringUtils.hasText(meta.getBuildingName()) && StringUtils.hasText(meta.getAddress())) {
            meta.setBuildingName(MktExtDataGateway.extractBuildingName(meta.getAddress()));
        }
        if (meta.getLng() != null && meta.getLat() != null) {
            NearestWh nearest = resolveNearestWarehouse(tenantId, projectId, meta.getLng(), meta.getLat());
            meta.setNearestWhId(nearest.id());
            meta.setNearestWhName(nearest.name());
            meta.setDistNearestWhKm(nearest.distKm());
        }

        computeUrgency(meta, meta.getSeaSource());
        meta.setUpdatedAt(LocalDateTime.now());
        if (isNew) seaMetas.insert(meta);
        else seaMetas.updateById(meta);

        // 幂等挂载 1 客多店矩阵记录
        if (StringUtils.hasText(r.shopName())) {
            saveShopMatrixRow(customerId, tenantId, r.platform(), r.shopName(), meta.getCategory(),
                    r.rankNo(), r.estDailyOrders() == null ? null : r.estDailyOrders() * 30, r.logisticsScore());
        }
        for (Map<String, String> extra : aligned.extraShops()) {
            if (StringUtils.hasText(extra.get("shopName"))) {
                saveShopMatrixRow(customerId, tenantId, extra.getOrDefault("platform", "天猫"), extra.get("shopName"), meta.getCategory(), null, null, null);
            }
        }
    }

    private void saveShopMatrixRow(Long customerId, Long tenantId, String platform, String shopName,
                                   String category, Integer rankNo, Integer monthlySales, BigDecimal score) {
        try {
            jdbc.update("""
                    INSERT INTO crm_public_sea_shop (customer_id, tenant_id, platform, shop_name, category, rank_no, monthly_sales, logistics_score)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      category = COALESCE(VALUES(category), category),
                      rank_no = COALESCE(VALUES(rank_no), rank_no),
                      monthly_sales = COALESCE(VALUES(monthly_sales), monthly_sales),
                      logistics_score = COALESCE(VALUES(logistics_score), logistics_score)
                    """, customerId, tenantId, StringUtils.hasText(platform) ? platform : "抖音",
                    shopName.trim(), category, rankNo, monthlySales, score);
        } catch (Exception ignored) { /* 兼容尚未跑 V132 的单测 Mock */ }
    }

    private record NearestWh(Long id, String name, BigDecimal distKm) {}

    /**
     * 遍历本园区全部已上线加盟云仓，按真实地址地理编码（缓存）或所属城市产业带坐标求最近一仓里程，
     * 避免把东莞/深圳/佛山客户统一按广州番禺单点算偏。
     */
    private NearestWh resolveNearestWarehouse(Long tenantId, Long projectId, BigDecimal custLng, BigDecimal custLat) {
        List<Map<String, Object>> whRows = List.of();
        try {
            whRows = jdbc.queryForList("""
                    SELECT id, name, region, address
                      FROM crm_warehouse
                     WHERE tenant_id = ? AND (project_id = ? OR project_id IS NULL)
                       AND join_status = 5 AND deleted = 0
                     ORDER BY id
                    """, tenantId, projectId);
        } catch (Exception ignored) {}
        if (whRows == null || whRows.isEmpty()) {
            int m = MktExtDataGateway.haversineMeters(custLng, custLat, DEFAULT_GD_WH_LNG, DEFAULT_GD_WH_LAT);
            return new NearestWh(null, "广东园区主力云仓",
                    BigDecimal.valueOf(m * 1.28 / 1000.0).setScale(1, RoundingMode.HALF_UP));
        }
        Long bestId = null;
        String bestName = "广东园区主力云仓";
        int bestMeters = Integer.MAX_VALUE;
        for (Map<String, Object> w : whRows) {
            Long id = w.get("id") instanceof Number n ? n.longValue() : null;
            String name = w.get("name") == null ? "加盟云仓" : String.valueOf(w.get("name"));
            String addr = w.get("address") == null ? "" : String.valueOf(w.get("address")).trim();
            String region = w.get("region") == null ? "" : String.valueOf(w.get("region")).trim();
            BigDecimal[] pt = resolveWarehouseCoord(name, region, addr);
            int meters = MktExtDataGateway.haversineMeters(custLng, custLat, pt[0], pt[1]);
            if (meters < bestMeters) {
                bestMeters = meters;
                bestId = id;
                bestName = name;
            }
        }
        BigDecimal km = BigDecimal.valueOf(bestMeters * 1.28 / 1000.0).setScale(1, RoundingMode.HALF_UP);
        return new NearestWh(bestId, bestName, km);
    }

    private BigDecimal[] resolveWarehouseCoord(String name, String region, String address) {
        String cacheKey = (region + "|" + address + "|" + name).trim();
        BigDecimal[] hit = whGeoCache.get(cacheKey);
        if (hit != null) return hit;
        if (StringUtils.hasText(address)) {
            Optional<MktExtDataGateway.GeoPoint> geo = extGateway.geocodeAddress(address, region);
            if (geo.isPresent()) {
                BigDecimal[] pt = new BigDecimal[]{geo.get().lng(), geo.get().lat()};
                whGeoCache.put(cacheKey, pt);
                return pt;
            }
        }
        String zone = MktExtDataGateway.classifyZone(address + " " + region + " " + name, null);
        BigDecimal[] fallback = ZONE_WH_CENTROIDS.getOrDefault(zone, new BigDecimal[]{DEFAULT_GD_WH_LNG, DEFAULT_GD_WH_LAT});
        whGeoCache.put(cacheKey, fallback);
        return fallback;
    }

    private static void computeUrgency(MktPublicSeaMeta meta, String seaSource) {
        if ("CALC_HOT".equals(seaSource) || "FEIYU_AD".equals(seaSource)) {
            meta.setUrgencyLevel("S");
            meta.setUrgencyReason("主动测算底价或巨量飞鱼广东投流实时留资热询盘，转化窗口极短");
        } else if (meta.getLogisticsScore() != null && meta.getLogisticsScore().compareTo(new BigDecimal("4.60")) < 0) {
            meta.setUrgencyLevel("S");
            meta.setUrgencyReason("类目榜单店铺物流体验分 " + meta.getLogisticsScore() + " (<4.60 飘绿)，存在发货时效痛点");
        } else if ("AMAP_POI".equals(seaSource) || "TUNGEE_JOB".equals(seaSource)
                || (meta.getEstDailyOrders() != null && meta.getEstDailyOrders() >= 500)) {
            meta.setUrgencyLevel("A");
            meta.setUrgencyReason("高德产业带扫楼命中或日估单量 ≥500 单，适合外勤集中陌拜算账");
        } else {
            meta.setUrgencyLevel("B");
            meta.setUrgencyReason("广东公海标准储备货主，待首通核实单量与看仓意向");
        }
    }

    private boolean isPrivateProtected(Customer c) {
        if (c.getReferrerId() != null || Integer.valueOf(2).equals(c.getStatus())) return true;
        Integer activeLocks = jdbc.queryForObject(
                "SELECT COUNT(1) FROM crm_customer_lock WHERE customer_id = ? AND status IN (1, 2, 3) AND deleted = 0",
                Integer.class, c.getId());
        if (activeLocks != null && activeLocks > 0) return true;
        Integer activeContracts = jdbc.queryForObject(
                "SELECT COUNT(1) FROM crm_service_contract WHERE customer_id = ? AND status IN (2, 3, 4) AND deleted = 0",
                Integer.class, c.getId());
        return activeContracts != null && activeContracts > 0;
    }

    private boolean isWarehouseHmacProtected(Long tenantId, String companyName, String shopName) {
        try {
            protectionService.assertUnprotected(tenantId, companyName, shopName);
            return false;
        } catch (BizException e) { return true; }
    }

    private List<RawLeadRow> parseFileRows(MultipartFile file, String fileName) {
        List<RawLeadRow> out = new ArrayList<>();
        try {
            if (fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                    String headerLine = br.readLine();
                    if (headerLine == null) return out;
                    String[] headers = parseCsvLine(headerLine.replace("\uFEFF", ""));
                    Map<String, Integer> idx = mapHeaderIndexes(Arrays.asList(headers));
                    String line;
                    while ((line = br.readLine()) != null && out.size() < MAX_IMPORT_ROWS) {
                        if (!line.isBlank()) out.add(rowFromArray(parseCsvLine(line), idx));
                    }
                }
                return out;
            }
            try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
                Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
                if (sheet == null) return out;
                DataFormatter fmt = new DataFormatter();
                Row headerRow = sheet.getRow(sheet.getFirstRowNum());
                if (headerRow == null) return out;
                List<String> headers = new ArrayList<>();
                for (int c = 0; c < headerRow.getLastCellNum(); c++) headers.add(fmt.formatCellValue(headerRow.getCell(c)));
                Map<String, Integer> idx = mapHeaderIndexes(headers);
                for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum() && out.size() < MAX_IMPORT_ROWS; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    String[] cols = new String[headers.size()];
                    boolean any = false;
                    for (int c = 0; c < headers.size(); c++) {
                        cols[c] = fmt.formatCellValue(row.getCell(c)).trim();
                        if (!cols[c].isEmpty()) any = true;
                    }
                    if (any) out.add(rowFromArray(cols, idx));
                }
            }
        } catch (BizException e) { throw e; }
        catch (Exception e) { throw new BizException(400, "榜单文件解析失败，请上传标准 .xlsx 或 UTF-8 .csv 文件"); }
        return out;
    }

    /** 标准 RFC-4180 CSV 单行解析：正确处理双引号包裹的英文逗号（如地址）与 "" 转义，避免串列。 */
    static String[] parseCsvLine(String line) {
        if (line == null) return new String[0];
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else {
                if (ch == '"') {
                    inQuotes = true;
                } else if (ch == ',') {
                    fields.add(cur.toString().trim());
                    cur.setLength(0);
                } else {
                    cur.append(ch);
                }
            }
        }
        fields.add(cur.toString().trim());
        return fields.toArray(String[]::new);
    }

    private static Map<String, Integer> mapHeaderIndexes(List<String> headers) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i) == null ? "" : headers.get(i).trim();
            if (h.contains("公司") || h.contains("企业") || h.contains("主体") || h.contains("执照")) m.putIfAbsent("name", i);
            else if (h.contains("店铺") || h.contains("小店") || h.contains("网店") || h.equals("名称")) m.putIfAbsent("shop", i);
            else if (h.contains("平台")) m.putIfAbsent("plat", i);
            else if (h.contains("排名") || h.contains("名次") || h.equalsIgnoreCase("rank")) m.putIfAbsent("rank", i);
            else if (h.contains("联系人") || h.contains("法人") || h.contains("姓名")) m.putIfAbsent("contact", i);
            else if (h.contains("手机") || h.contains("电话") || h.contains("联系方式")) m.putIfAbsent("phone", i);
            else if (h.contains("类目") || h.contains("行业") || h.contains("品类")) m.putIfAbsent("cat", i);
            else if (h.contains("日均") || h.contains("日发") || h.contains("日销")) m.putIfAbsent("daily", i);
            else if (h.contains("月销") || h.contains("30天销量") || h.contains("单量")) m.putIfAbsent("monthly", i);
            else if (h.contains("重")) m.putIfAbsent("weight", i);
            else if (h.contains("物流分") || h.contains("体验分") || h.contains("评分")) m.putIfAbsent("score", i);
            else if (h.contains("地址")) m.putIfAbsent("addr", i);
            else if (h.contains("省") || h.contains("市") || h.contains("地区") || h.contains("发货地")) m.putIfAbsent("region", i);
            else if (h.contains("痛点") || h.contains("卡点") || h.contains("备注")) m.putIfAbsent("note", i);
        }
        return m;
    }

    private static RawLeadRow rowFromArray(String[] cols, Map<String, Integer> idx) {
        String name = col(cols, idx.get("name"), 100);
        String shop = col(cols, idx.get("shop"), 120);
        Integer daily = asInt(col(cols, idx.get("daily"), 24), null);
        if (daily == null) {
            Integer monthly = asInt(col(cols, idx.get("monthly"), 24), null);
            if (monthly != null) daily = Math.max(1, monthly / 30);
        }
        return new RawLeadRow(name, shop,
                Optional.ofNullable(col(cols, idx.get("plat"), 24)).orElse("抖音"),
                asInt(col(cols, idx.get("rank"), 16), null),
                col(cols, idx.get("contact"), 64),
                cleanPhone(col(cols, idx.get("phone"), 32)),
                col(cols, idx.get("cat"), 64), daily,
                asDecimal(col(cols, idx.get("weight"), 20), null),
                asDecimal(col(cols, idx.get("score"), 20), null),
                col(cols, idx.get("region"), 64),
                col(cols, idx.get("addr"), 220),
                null, null, col(cols, idx.get("note"), 450));
    }

    private static String col(String[] cols, Integer i, int max) {
        return (i == null || i < 0 || i >= cols.length) ? null : cleanText(cols[i], max);
    }
    private static void addDetail(List<Map<String, Object>> details, int rowNo, String maskedName, String type, String reason) {
        if (details.size() < 50) details.add(Map.of("rowNo", rowNo, "target", maskedName == null ? "—" : maskedName, "type", type, "reason", reason));
    }
    private static String maskName(String s) {
        if (!StringUtils.hasText(s)) return "未知主体";
        String t = s.trim();
        return t.length() <= 3 ? t.charAt(0) + "**" : t.substring(0, 2) + "***" + t.substring(t.length() - 2);
    }
    private Long resolveProjectId(Long projectId) {
        if (projectId != null && projectId > 0) return projectId;
        List<Long> ids = jdbc.queryForList("SELECT id FROM biz_project WHERE deleted = 0 ORDER BY id LIMIT 1", Long.class);
        return ids.isEmpty() ? 1L : ids.get(0);
    }
    private Long resolveTenantId(Long projectId) {
        if (projectId != null) {
            List<Long> t = jdbc.queryForList("SELECT tenant_id FROM biz_project WHERE id = ? AND deleted = 0 LIMIT 1", Long.class, projectId);
            if (!t.isEmpty() && t.get(0) != null) return t.get(0);
        }
        return MyMetaObjectHandler.DEFAULT_TENANT_ID;
    }
    private static String normalizeSource(String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return VALID_SOURCES.contains(s) ? s : "CHANMAMA";
    }
    private static Object firstNonNull(Map<String, Object> m, String... keys) {
        for (String k : keys) if (m.get(k) != null && !String.valueOf(m.get(k)).isBlank()) return m.get(k);
        return null;
    }
    private static String cleanText(Object v, int max) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : (s.length() <= max ? s : s.substring(0, max));
    }
    private static String cleanPhone(Object v) {
        if (v == null) return null;
        String digits = String.valueOf(v).replaceAll("[^0-9]", "");
        if (digits.startsWith("86") && digits.length() == 13) digits = digits.substring(2);
        return digits.isEmpty() ? null : digits;
    }
    private static Long asLong(Object v) {
        try { return v == null ? null : Long.valueOf(String.valueOf(v).trim()); } catch (Exception e) { return null; }
    }
    private static Integer asInt(Object v, Integer def) {
        if (v == null) return def;
        try {
            String clean = String.valueOf(v).replaceAll("[^0-9]", "");
            return clean.isEmpty() ? def : Integer.parseInt(clean);
        } catch (Exception e) { return def; }
    }
    private static BigDecimal asDecimal(Object v, BigDecimal def) {
        return asDecimalScale(v, 2) != null ? asDecimalScale(v, 2) : def;
    }
    private static BigDecimal asDecimalScale(Object v, int scale) {
        if (v == null) return null;
        try {
            String clean = String.valueOf(v).replaceAll("[^0-9.-]", "");
            return clean.isEmpty() ? null : new BigDecimal(clean).setScale(scale, RoundingMode.HALF_UP);
        } catch (Exception e) { return null; }
    }
}
