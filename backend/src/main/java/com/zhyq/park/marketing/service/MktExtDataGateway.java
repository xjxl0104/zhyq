package com.zhyq.park.marketing.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 外部数据网关：
 * 1. 广东战区识别与付费接口省钱锁（外省跳过；地区不明的批量行不付费）；
 * 2. 工商/联系方式 30 天本地缓存（含"查无此企业"的负缓存，避免重复付费）；
 * 3. 多源工商查询：企查查 → 天眼查 → 百元轻量包，仅在"未配置/熔断/接口异常"时降级到下一家，"查无此企业"直接终止；
 * 4. 高德地理编码、周边 POI 雷达与球面测距。
 * 所有密钥只从连接器中心（crm_ext_connector_config）读取，不再读 biz_setting 明文副本。
 */
@Slf4j
@Service
public class MktExtDataGateway {

    private static final String MODULE = "marketing";
    private static final Pattern BUILDING_PAT = Pattern.compile(
            "([\\u4e00-\\u9fa5A-Za-z0-9]{2,30}?(?:电商大厦|直播基地|电商园|产业园|科创园|创业园|电商城|服装城|美妆园|大厦|广场|中心))");
    /** 行政区/门牌前缀：逐段剥离，避免把"广东省广州市白云区…"整串当成大厦名。 */
    private static final Pattern ADMIN_PREFIX = Pattern.compile(
            "^[\\u4e00-\\u9fa5]{1,8}?(?:自治区|特别行政区|省|市(?!场)|区|县|镇|乡|街道)");
    private static final Pattern ROAD_PREFIX = Pattern.compile(
            "^[\\u4e00-\\u9fa5A-Za-z0-9]{1,14}?(?:大道|路|街|巷)[0-9A-Za-z\\-之]*号?");
    private static final Pattern OUT_PROVINCE_PAT = Pattern.compile(
            "(北京|上海|天津|重庆|浙江|杭州|宁波|温州|义乌|嘉兴|湖州|绍兴|金华|江苏|南京|苏州|无锡|山东|济南|青岛|临沂|福建|福州|厦门|泉州|河北|石家庄|保定|河南|郑州|湖北|武汉|湖南|长沙|四川|成都|安徽|合肥|江西|南昌|辽宁|陕西|西安)");
    public static final Set<String> GD_ZONES = Set.of("GZ", "DG", "SZ", "FS", "YD", "OTHER_GD");

    private final JdbcTemplate jdbc;
    private final BizSettings bizSettings;
    private final ObjectMapper json;
    private final MktConnectorHubService connectorHub;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

    /**
     * 付费对齐策略：
     * BATCH  批量导入/雷达：外省整行跳过；地区不明不付费（只查本地缓存）
     * SINGLE 单店人工反查：外省跳过；地区不明允许付费（人工主动发起）
     * FORCE  人工特批：不看地区，允许付费
     * INBOUND 主动留资/广告推送：从不跳过整行，只查本地缓存不付费
     * PREVIEW 导入前预检：同 BATCH 的地区跳过规则，但只查本地缓存，绝不付费
     */
    public enum AlignPolicy { BATCH, SINGLE, FORCE, INBOUND, PREVIEW }

    private enum LookupOutcome { FOUND, NOT_FOUND, SKIPPED, ERROR }
    private record Lookup(LookupOutcome outcome, CompanyProfile profile) {
        static Lookup skipped() { return new Lookup(LookupOutcome.SKIPPED, null); }
        static Lookup error() { return new Lookup(LookupOutcome.ERROR, null); }
        static Lookup notFound() { return new Lookup(LookupOutcome.NOT_FOUND, null); }
    }

    public MktExtDataGateway(JdbcTemplate jdbc, BizSettings bizSettings, ObjectMapper json) {
        this(jdbc, bizSettings, json, null);
    }

    @Autowired
    public MktExtDataGateway(JdbcTemplate jdbc, BizSettings bizSettings, ObjectMapper json, MktConnectorHubService connectorHub) {
        this.jdbc = jdbc;
        this.bizSettings = bizSettings;
        this.json = json;
        this.connectorHub = connectorHub;
    }

    public record CompanyProfile(
            // CACHE_HIT / CACHE_NOT_FOUND / API_ALIGNED / NOT_FOUND / SKIPPED_OUT_PROVINCE / SKIPPED_UNKNOWN_REGION / UNCONFIGURED
            String status,
            String companyName, String creditCode, String legalPerson, String regCapital,
            Integer insuredCount, String phone, String province, String city, String district,
            String address, int dishonestCount, List<Map<String, String>> extraShops) {
        static CompanyProfile empty(String status) {
            return new CompanyProfile(status, null, null, null, null, null, null, null, null, null, null, 0, List.of());
        }
        public boolean aligned() { return "CACHE_HIT".equals(status) || "API_ALIGNED".equals(status); }
    }

    public record GeoPoint(BigDecimal lng, BigDecimal lat, String province, String city, String district,
                           String formattedAddress, String buildingName) {}

    public record PoiCandidate(String name, String address, String city, String district,
                               BigDecimal lng, BigDecimal lat, String tel, String typeDesc) {}

    /** 广东战区分区码：GZ / DG / SZ / FS / YD / OTHER_GD / OUT_GD / UNKNOWN（无法判断时不再默认算广州）。 */
    public static String classifyZone(String text, String creditCode) {
        String s = text == null ? "" : text;
        if (StringUtils.hasText(creditCode) && creditCode.length() >= 4) {
            String provCode = creditCode.substring(2, 4);
            if (provCode.matches("\\d{2}") && !"44".equals(provCode)) return "OUT_GD";
        }
        if (s.matches(".*(广州|白云|番禺|天河|海珠|荔湾|黄埔|花都|增城|南沙|从化|十三行|沙河|嘉禾).*")) return "GZ";
        if (s.matches(".*(东莞|虎门|大朗|厚街|常平|寮步).*")) return "DG";
        if (s.matches(".*(深圳|龙岗|宝安|福田|南山|罗湖|龙华|坂田|华强北).*")) return "SZ";
        if (s.matches(".*(佛山|顺德|南海|禅城|三水|高明|北滘|里水|大沥).*")) return "FS";
        if (s.matches(".*(揭阳|普宁|汕头|澄海|潮南|潮阳|潮州|汕尾).*")) return "YD";
        if (s.matches(".*(广东|中山|珠海|惠州|江门|湛江|茂名|肇庆|清远|韶关|河源|梅州|阳江|云浮).*")) return "OTHER_GD";
        if (OUT_PROVINCE_PAT.matcher(s).find()) return "OUT_GD";
        if (StringUtils.hasText(creditCode) && creditCode.length() >= 4 && "44".equals(creditCode.substring(2, 4))) return "OTHER_GD";
        return "UNKNOWN";
    }

    /** 提取大厦/园区名，先剥离省市区镇与路牌门号前缀，保证同楼多户能聚到同一个名字上。 */
    public static String extractBuildingName(String address) {
        if (!StringUtils.hasText(address)) return null;
        Matcher m = BUILDING_PAT.matcher(address.replaceAll("\\s+", ""));
        if (!m.find()) return null;
        String name = m.group(1);
        boolean changed = true;
        while (changed) {
            changed = false;
            Matcher admin = ADMIN_PREFIX.matcher(name);
            if (admin.find() && name.length() - admin.end() >= 4) {
                name = name.substring(admin.end());
                changed = true;
                continue;
            }
            Matcher road = ROAD_PREFIX.matcher(name);
            if (road.find() && name.length() - road.end() >= 4) {
                name = name.substring(road.end());
                changed = true;
            }
        }
        return name;
    }

    public boolean isGuangdongLockEnabled() {
        String lockProv = bizSettings.raw(MODULE, "sea_region_lock_province");
        return lockProv == null || lockProv.isBlank() || lockProv.contains("广东");
    }

    /**
     * 对齐企业或店铺背后的工商/电话/地址（顺序：地区判断 → 本地缓存 → 付费多源）。
     * 未配置任何付费源时诚实返回 UNCONFIGURED，绝不编造法人/信用代码。
     */
    public CompanyProfile alignCompany(Long tenantId, String companyOrShopName, String regionHint, AlignPolicy policy) {
        if (!StringUtils.hasText(companyOrShopName)) return CompanyProfile.empty("UNCONFIGURED");
        AlignPolicy p = policy == null ? AlignPolicy.BATCH : policy;
        String key = MktCompetitionProtectionService.normalize(companyOrShopName);
        String zone = classifyZone((regionHint == null ? "" : regionHint) + " " + companyOrShopName, null);
        boolean lockOn = isGuangdongLockEnabled();

        if (lockOn && (p == AlignPolicy.BATCH || p == AlignPolicy.SINGLE || p == AlignPolicy.PREVIEW) && "OUT_GD".equals(zone)) {
            return new CompanyProfile("SKIPPED_OUT_PROVINCE", companyOrShopName.trim(), null, null, null, null, null,
                    "外省", null, null, regionHint, 0, List.of());
        }

        CompanyProfile cached = readCache(tenantId, key);
        if (cached != null) return cached;

        if (p == AlignPolicy.INBOUND) return CompanyProfile.empty("UNCONFIGURED");
        if (p == AlignPolicy.PREVIEW) return CompanyProfile.empty("PREVIEW_NOT_QUERIED");
        if (lockOn && p == AlignPolicy.BATCH && "UNKNOWN".equals(zone)) {
            return CompanyProfile.empty("SKIPPED_UNKNOWN_REGION");
        }

        Lookup result = null;
        for (int i = 0; i < 3; i++) {
            Lookup step = switch (i) {
                case 0 -> tryQccOfficial(tenantId, companyOrShopName);
                case 1 -> tryTianyanchaOfficial(tenantId, companyOrShopName);
                default -> tryLightCorpPack(tenantId, companyOrShopName);
            };
            if (step.outcome() == LookupOutcome.FOUND || step.outcome() == LookupOutcome.NOT_FOUND) {
                result = step;
                break;
            }
        }
        if (result == null) return CompanyProfile.empty("UNCONFIGURED");
        if (result.outcome() == LookupOutcome.NOT_FOUND) {
            writeNegativeCache(tenantId, key, companyOrShopName);
            return CompanyProfile.empty("NOT_FOUND");
        }

        CompanyProfile fetched = result.profile();
        String enrichedPhone = fetched.phone();
        if (!StringUtils.hasText(enrichedPhone)) enrichedPhone = tryTungeeBossPhone(tenantId, fetched.companyName());
        writeCache(tenantId, key, fetched, enrichedPhone);
        return new CompanyProfile("API_ALIGNED", fetched.companyName(), fetched.creditCode(), fetched.legalPerson(),
                fetched.regCapital(), fetched.insuredCount(), enrichedPhone, fetched.province(),
                fetched.city(), fetched.district(), fetched.address(), fetched.dishonestCount(), fetched.extraShops());
    }

    private CompanyProfile readCache(Long tenantId, String key) {
        List<Map<String, Object>> cached = jdbc.queryForList("""
                SELECT company_name, credit_code, legal_person, reg_capital, insured_count, phone,
                       province, city, district, address, dishonest_count, shops_json, raw_source
                  FROM crm_ext_company_cache
                 WHERE tenant_id = ? AND lookup_key = ? AND expires_at > NOW()
                 LIMIT 1
                """, tenantId, key);
        if (cached == null || cached.isEmpty()) return null;
        Map<String, Object> c = cached.get(0);
        if ("NOT_FOUND".equals(str(c.get("raw_source")))) return CompanyProfile.empty("CACHE_NOT_FOUND");
        return new CompanyProfile("CACHE_HIT",
                str(c.get("company_name")), str(c.get("credit_code")), str(c.get("legal_person")),
                str(c.get("reg_capital")), numInt(c.get("insured_count")), str(c.get("phone")),
                str(c.get("province")), str(c.get("city")), str(c.get("district")),
                str(c.get("address")), Optional.ofNullable(numInt(c.get("dishonest_count"))).orElse(0),
                parseShopsJson(str(c.get("shops_json"))));
    }

    private void writeCache(Long tenantId, String key, CompanyProfile f, String phone) {
        String shopsJson = "[]";
        try { shopsJson = json.writeValueAsString(f.extraShops()); } catch (Exception ignored) {}
        jdbc.update("""
                INSERT INTO crm_ext_company_cache
                  (tenant_id, lookup_key, company_name, credit_code, legal_person, reg_capital, insured_count,
                   phone, province, city, district, address, dishonest_count, shops_json, raw_source, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'API', DATE_ADD(NOW(), INTERVAL 30 DAY))
                ON DUPLICATE KEY UPDATE
                  company_name=VALUES(company_name), credit_code=VALUES(credit_code), legal_person=VALUES(legal_person),
                  reg_capital=VALUES(reg_capital), insured_count=VALUES(insured_count), phone=VALUES(phone),
                  address=VALUES(address), dishonest_count=VALUES(dishonest_count), shops_json=VALUES(shops_json),
                  raw_source='API', expires_at=DATE_ADD(NOW(), INTERVAL 30 DAY)
                """, tenantId, key, f.companyName(), f.creditCode(), f.legalPerson(), f.regCapital(),
                f.insuredCount(), phone, f.province(), f.city(), f.district(), f.address(), f.dishonestCount(), shopsJson);
    }

    /** 查无此企业也缓存 30 天，避免同名重复付费查询。 */
    private void writeNegativeCache(Long tenantId, String key, String keyword) {
        try {
            jdbc.update("""
                    INSERT INTO crm_ext_company_cache (tenant_id, lookup_key, company_name, raw_source, expires_at)
                    VALUES (?, ?, ?, 'NOT_FOUND', DATE_ADD(NOW(), INTERVAL 30 DAY))
                    ON DUPLICATE KEY UPDATE raw_source='NOT_FOUND', expires_at=DATE_ADD(NOW(), INTERVAL 30 DAY)
                    """, tenantId, key, keyword.trim().length() > 120 ? keyword.trim().substring(0, 120) : keyword.trim());
        } catch (Exception ignored) {}
    }

    private Lookup tryQccOfficial(Long tenantId, String keyword) {
        if (connectorHub == null) return Lookup.skipped();
        Optional<Map<String, String>> credsOpt = connectorHub.acquireActiveCredentials(tenantId, "QCC_PRO", false);
        if (credsOpt.isEmpty()) return Lookup.skipped();
        Map<String, String> creds = credsOpt.get();
        String appKey = trimNull(creds.get("appKey"));
        String secretKey = trimNull(creds.get("secretKey"));
        if (!StringUtils.hasText(appKey) || !StringUtils.hasText(secretKey)) return Lookup.skipped();
        try {
            String timespan = String.valueOf(System.currentTimeMillis() / 1000);
            String token = DigestUtil.md5Hex(appKey + timespan + secretKey).toUpperCase(Locale.ROOT);
            String url = "https://api.qichacha.com/ECIV4/GetBasicDetailsByName?key=" + URLEncoder.encode(appKey, StandardCharsets.UTF_8)
                    + "&keyword=" + URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                    .header("Token", token).header("Timespan", timespan).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) return Lookup.error();
            JsonNode root = json.readTree(resp.body());
            String status = root.path("Status").asText("");
            if ("201".equals(status)) return Lookup.notFound();
            if (!"200".equals(status)) return Lookup.error();
            CompanyProfile p = parseStandardCompanyNode(root.path("Result"));
            return p == null ? Lookup.notFound() : new Lookup(LookupOutcome.FOUND, p);
        } catch (Exception e) {
            log.warn("[qcc-pro] 企查查官方接口调用异常: {}", e.getMessage());
            return Lookup.error();
        }
    }

    private Lookup tryTianyanchaOfficial(Long tenantId, String keyword) {
        if (connectorHub == null) return Lookup.skipped();
        Optional<Map<String, String>> credsOpt = connectorHub.acquireActiveCredentials(tenantId, "TYC_PRO", false);
        if (credsOpt.isEmpty()) return Lookup.skipped();
        String token = trimNull(credsOpt.get().get("token"));
        if (!StringUtils.hasText(token)) return Lookup.skipped();
        try {
            String url = "https://open.api.tianyancha.com/services/open/ic/baseinfoV2/2.0?keyword="
                    + URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                    .header("Authorization", token).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) return Lookup.error();
            JsonNode root = json.readTree(resp.body());
            int code = root.path("error_code").asInt(-1);
            if (code == 300000) return Lookup.notFound();
            if (code != 0) return Lookup.error();
            CompanyProfile p = parseStandardCompanyNode(root.path("result"));
            return p == null ? Lookup.notFound() : new Lookup(LookupOutcome.FOUND, p);
        } catch (Exception e) {
            log.warn("[tyc-pro] 天眼查接口调用异常: {}", e.getMessage());
            return Lookup.error();
        }
    }

    private Lookup tryLightCorpPack(Long tenantId, String keyword) {
        if (connectorHub == null) return Lookup.skipped();
        Optional<Map<String, String>> opt = connectorHub.acquireActiveCredentials(tenantId, "LIGHT_CORP", false);
        if (opt.isEmpty()) return Lookup.skipped();
        String appCode = trimNull(opt.get().get("appCode"));
        String endpoint = trimNull(opt.get().get("endpoint"));
        if (!StringUtils.hasText(appCode) || !StringUtils.hasText(endpoint)) return Lookup.skipped();
        try {
            String url = endpoint + (endpoint.contains("?") ? "&" : "?") + "keyword=" + URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                    .header("Authorization", "APPCODE " + appCode).header("Token", appCode).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) return Lookup.error();
            JsonNode root = json.readTree(resp.body());
            JsonNode data = root.has("Result") ? root.path("Result") : root.has("result") ? root.path("result") : root.path("data");
            CompanyProfile p = parseStandardCompanyNode(data);
            return p == null ? Lookup.notFound() : new Lookup(LookupOutcome.FOUND, p);
        } catch (Exception e) {
            log.warn("[light-corp] 轻量工商网关异常: {}", e.getMessage());
            return Lookup.error();
        }
    }

    private String tryTungeeBossPhone(Long tenantId, String companyName) {
        if (connectorHub == null || !StringUtils.hasText(companyName)) return null;
        Optional<Map<String, String>> opt = connectorHub.acquireActiveCredentials(tenantId, "TUNGEE_API", false);
        if (opt.isEmpty() || !StringUtils.hasText(opt.get().get("endpoint"))) return null;
        try {
            Map<String, String> c = opt.get();
            String url = c.get("endpoint") + (c.get("endpoint").contains("?") ? "&" : "?")
                    + "companyName=" + URLEncoder.encode(companyName, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4))
                    .header("X-App-Key", Optional.ofNullable(c.get("appKey")).orElse("")).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) return null;
            return firstText(json.readTree(resp.body()).path("data"), "mobile", "phone", "bossPhone");
        } catch (Exception e) { return null; }
    }

    private CompanyProfile parseStandardCompanyNode(JsonNode data) {
        if (data == null || data.isMissingNode() || data.isNull()) return null;
        if (data.isArray()) {
            if (data.isEmpty()) return null;
            data = data.get(0);
        }
        String corpName = firstText(data, "Name", "companyName", "entName", "name");
        if (!StringUtils.hasText(corpName)) return null;
        String credit = firstText(data, "CreditCode", "creditCode", "regNo");
        String legal = firstText(data, "OperName", "legalPerson", "legalPersonName", "frName");
        String cap = firstText(data, "RegistCapi", "regCapital", "regCap");
        Integer insured = firstInt(data, "InsuredCount", "insuredCount", "socialStaffNum");
        String phone = firstText(data, "ContactNumber", "phoneNumber", "tel", "phone");
        if (!StringUtils.hasText(phone) && data.has("ContactInfo")) {
            phone = firstText(data.path("ContactInfo"), "PhoneNumber", "tel");
        }
        String prov = firstText(data, "Province", "province", "base");
        String city = firstText(data, "City", "city");
        String dist = firstText(data, "District", "district", "county");
        String addr = firstText(data, "Address", "address", "regLocation");
        int dishonest = Optional.ofNullable(firstInt(data, "DishonestCount", "dishonestCount")).orElse(0);
        List<Map<String, String>> shops = new ArrayList<>();
        if (data.path("ShopList").isArray()) {
            for (JsonNode s : data.path("ShopList")) {
                String sn = firstText(s, "ShopName", "shopName", "name");
                String plat = firstText(s, "Platform", "platform");
                if (StringUtils.hasText(sn)) shops.add(Map.of("platform", plat != null ? plat : "天猫", "shopName", sn));
            }
        }
        return new CompanyProfile("API_ALIGNED", corpName, credit, legal, cap, insured, phone, prov, city, dist, addr, dishonest, shops);
    }

    /** 高德地理编码：广东地址 → GCJ-02 坐标与大厦名；未开通高德时返回 empty 诚实降级。 */
    public Optional<GeoPoint> geocodeAddress(String address, String cityHint) {
        if (!StringUtils.hasText(address)) return Optional.empty();
        String amapKey = resolveAmapWebKey();
        if (!StringUtils.hasText(amapKey)) return Optional.empty();
        try {
            String url = "https://restapi.amap.com/v3/geocode/geo?key=" + URLEncoder.encode(amapKey, StandardCharsets.UTF_8)
                    + "&address=" + URLEncoder.encode(address.trim(), StandardCharsets.UTF_8)
                    + "&city=" + URLEncoder.encode(StringUtils.hasText(cityHint) ? cityHint.trim() : "广东", StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = json.readTree(resp.body());
            JsonNode geocodes = root.path("geocodes");
            if (!"1".equals(root.path("status").asText()) || !geocodes.isArray() || geocodes.isEmpty()) return Optional.empty();
            JsonNode g = geocodes.get(0);
            String[] loc = g.path("location").asText("").split(",");
            if (loc.length != 2) return Optional.empty();
            BigDecimal lng = new BigDecimal(loc[0]).setScale(6, RoundingMode.HALF_UP);
            BigDecimal lat = new BigDecimal(loc[1]).setScale(6, RoundingMode.HALF_UP);
            String formatted = g.path("formatted_address").asText(address);
            String building = g.path("building").path("name").isTextual()
                    ? g.path("building").path("name").asText() : extractBuildingName(formatted);
            return Optional.of(new GeoPoint(lng, lat,
                    g.path("province").asText("广东省"),
                    g.path("city").isTextual() ? g.path("city").asText() : cityHint,
                    g.path("district").isTextual() ? g.path("district").asText() : null,
                    formatted, building));
        } catch (Exception e) {
            log.warn("[ext-amap] 高德地理编码异常: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 高德周边商圈 POI 扫楼雷达。 */
    public List<PoiCandidate> scanAroundPois(BigDecimal centerLng, BigDecimal centerLat, int radiusMeters, String keywords) {
        String amapKey = resolveAmapWebKey();
        if (!StringUtils.hasText(amapKey)) {
            throw new BizException(400, "尚未在「外部数据连接器中心」开启高德并填写 Web服务 Key，暂不能启动扫楼雷达");
        }
        BigDecimal lng = centerLng != null ? centerLng : new BigDecimal("113.324520");
        BigDecimal lat = centerLat != null ? centerLat : new BigDecimal("23.106414");
        int radius = Math.max(500, Math.min(50_000, radiusMeters));
        String kw = StringUtils.hasText(keywords) ? keywords.trim() : "电子商务园|直播基地|化妆品有限公司|服饰供应链";
        try {
            String url = "https://restapi.amap.com/v3/place/around?key=" + URLEncoder.encode(amapKey, StandardCharsets.UTF_8)
                    + "&location=" + lng.toPlainString() + "," + lat.toPlainString()
                    + "&radius=" + radius
                    + "&keywords=" + URLEncoder.encode(kw, StandardCharsets.UTF_8)
                    + "&offset=25&page=1&extensions=all";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = json.readTree(resp.body());
            if (!"1".equals(root.path("status").asText())) {
                throw new BizException(502, "高德 POI 搜索返回异常：" + root.path("info").asText("未知错误"));
            }
            List<PoiCandidate> list = new ArrayList<>();
            for (JsonNode p : root.path("pois")) {
                String[] loc = p.path("location").asText("").split(",");
                if (loc.length != 2) continue;
                String telRaw = p.path("tel").isTextual() ? p.path("tel").asText() : "";
                String firstTel = telRaw.contains(";") ? telRaw.split(";")[0] : telRaw;
                list.add(new PoiCandidate(
                        p.path("name").asText(""),
                        p.path("address").isTextual() ? p.path("address").asText() : p.path("pname").asText("") + p.path("cityname").asText(""),
                        p.path("cityname").isTextual() ? p.path("cityname").asText() : "",
                        p.path("adname").isTextual() ? p.path("adname").asText() : null,
                        new BigDecimal(loc[0]).setScale(6, RoundingMode.HALF_UP),
                        new BigDecimal(loc[1]).setScale(6, RoundingMode.HALF_UP),
                        firstTel,
                        p.path("type").asText("电商相关主体")));
            }
            return list;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(502, "请求高德开放平台超时，请检查网络或 Key 配额");
        }
    }

    /** 两个 GCJ-02 坐标点之间的球面直线米数。 */
    public static int haversineMeters(BigDecimal lng1, BigDecimal lat1, BigDecimal lng2, BigDecimal lat2) {
        if (lng1 == null || lat1 == null || lng2 == null || lat2 == null) return Integer.MAX_VALUE;
        double radLat1 = Math.toRadians(lat1.doubleValue());
        double radLat2 = Math.toRadians(lat2.doubleValue());
        double a = radLat1 - radLat2;
        double b = Math.toRadians(lng1.doubleValue() - lng2.doubleValue());
        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2)
                + Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
        return (int) Math.round(s * 6_378_137.0);
    }

    /** 连接器状态回显（供公海页读取）；只反映连接器中心的真实开关。 */
    public Map<String, Object> connectorStatus() {
        String amapWeb = resolveAmapWebKey();
        String amapJs = resolveAmapJsKey();
        boolean anyCorp = false;
        int active = 0;
        int total = MktConnectorHubService.DEFAULT_SEEDS.size();
        if (connectorHub != null) {
            try {
                Map<String, Object> hub = connectorHub.hubOverview();
                Map<?, ?> sum = (Map<?, ?>) hub.get("summary");
                active = sum.get("activeConnectors") instanceof Number n ? n.intValue() : 0;
                List<?> list = (List<?>) hub.get("connectors");
                if (list != null) {
                    anyCorp = list.stream().filter(Map.class::isInstance).map(Map.class::cast)
                            .anyMatch(m -> "CORP".equals(m.get("categoryCode"))
                                    && Boolean.TRUE.equals(m.get("enabled")) && Boolean.TRUE.equals(m.get("configured")));
                }
            } catch (Exception ignored) {}
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("regionLockProvince", Optional.ofNullable(trimNull(bizSettings.raw(MODULE, "sea_region_lock_province"))).orElse("广东省"));
        out.put("checkinMaxMeters", bizSettings.getInt(MODULE, "sea_checkin_max_meters", 500));
        out.put("visitProtectDays", bizSettings.getInt(MODULE, "sea_visit_protect_days", 15));
        out.put("amapWebConfigured", StringUtils.hasText(amapWeb));
        out.put("amapJsConfigured", StringUtils.hasText(amapJs));
        out.put("amapJsKey", StringUtils.hasText(amapJs) ? amapJs : "");
        out.put("qccConfigured", anyCorp);
        out.put("activeConnectors", active);
        out.put("totalConnectors", total);
        return out;
    }

    private String resolveAmapWebKey() {
        if (connectorHub == null) return null;
        return trimNull(connectorHub.activeCredentials(MyMetaObjectHandler.DEFAULT_TENANT_ID, "AMAP_LBS")
                .getOrDefault("webServiceKey", null));
    }

    private String resolveAmapJsKey() {
        if (connectorHub == null) return null;
        return trimNull(connectorHub.activeCredentials(MyMetaObjectHandler.DEFAULT_TENANT_ID, "AMAP_LBS")
                .getOrDefault("webJsKey", null));
    }

    private List<Map<String, String>> parseShopsJson(String raw) {
        if (!StringUtils.hasText(raw)) return List.of();
        try {
            JsonNode arr = json.readTree(raw);
            if (!arr.isArray()) return List.of();
            List<Map<String, String>> out = new ArrayList<>();
            for (JsonNode n : arr) {
                out.add(Map.of("platform", n.path("platform").asText("抖音"), "shopName", n.path("shopName").asText("")));
            }
            return out;
        } catch (Exception e) { return List.of(); }
    }

    private static String firstText(JsonNode node, String... keys) {
        if (node == null) return null;
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && v.isTextual() && !v.asText().isBlank()) return v.asText().trim();
        }
        return null;
    }

    private static Integer firstInt(JsonNode node, String... keys) {
        if (node == null) return null;
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && v.isNumber()) return v.intValue();
            if (v != null && v.isTextual()) {
                String clean = v.asText().replaceAll("[^0-9]", "");
                if (!clean.isEmpty() && clean.length() < 10) return Integer.parseInt(clean);
            }
        }
        return null;
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static String trimNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static Integer numInt(Object o) { return o instanceof Number n ? n.intValue() : null; }
}
