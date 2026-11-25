package com.zhyq.park.marketing.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量外部数据网关：
 * 1. 广东省主力战区分区识别与外省付费 API 硬拦截锁（省 85%+ 采买费）；
 * 2. 企业工商/联系方式/网店关联 30 天本地缓存（crm_ext_company_cache）+ 阿里云市场/APISpace 兼容网关；
 * 3. 高德开放平台免费地理编码（地址→GCJ02坐标+大厦聚类名）、周边 10km 电商园 POI 扫楼雷达与米级球面测距。
 */
@Slf4j
@Service
public class MktExtDataGateway {

    private static final String MODULE = "marketing";
    private static final Pattern BUILDING_PAT = Pattern.compile(
            "([\\u4e00-\\u9fa5A-Za-z0-9]{2,18}?(?:电商大厦|直播基地|电商园|产业园|科创园|创业园|电商城|服装城|美妆园|大厦|广场|中心))");
    private static final Pattern OUT_PROVINCE_PAT = Pattern.compile(
            "(北京|上海|天津|重庆|浙江|杭州|宁波|温州|义乌|嘉兴|湖州|绍兴|金华|江苏|南京|苏州|无锡|山东|济南|青岛|临沂|福建|福州|厦门|泉州|河北|石家庄|保定|河南|郑州|湖北|武汉|湖南|长沙|四川|成都|安徽|合肥|江西|南昌|辽宁|陕西|西安)");

    private final JdbcTemplate jdbc;
    private final BizSettings bizSettings;
    private final ObjectMapper json;
    private final MktConnectorHubService connectorHub;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

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
            String status, // CACHE_HIT / API_ALIGNED / SKIPPED_OUT_PROVINCE / UNCONFIGURED
            String companyName, String creditCode, String legalPerson, String regCapital,
            Integer insuredCount, String phone, String province, String city, String district,
            String address, int dishonestCount, List<Map<String, String>> extraShops) {}

    public record GeoPoint(BigDecimal lng, BigDecimal lat, String province, String city, String district,
                           String formattedAddress, String buildingName) {}

    public record PoiCandidate(String name, String address, String city, String district,
                               BigDecimal lng, BigDecimal lat, String tel, String typeDesc) {}

    /** 判断广东核心产业带分区码：GZ / DG / SZ / FS / YD / OTHER_GD / OUT_GD */
    public static String classifyZone(String text, String creditCode) {
        String s = text == null ? "" : text;
        if (StringUtils.hasText(creditCode) && creditCode.length() >= 4) {
            String provCode = creditCode.substring(2, 4);
            if (!"44".equals(provCode) && provCode.matches("\\d{2}")) return "OUT_GD";
        }
        if (s.matches(".*(广州|白云|番禺|天河|海珠|荔湾|黄埔|花都|增城|南沙|从化|十三行|沙河|嘉禾).*")) return "GZ";
        if (s.matches(".*(东莞|虎门|大朗|厚街|长安|南城|东城|常平|寮步).*")) return "DG";
        if (s.matches(".*(深圳|龙岗|宝安|福田|南山|罗湖|龙华|坂田|华强北).*")) return "SZ";
        if (s.matches(".*(佛山|顺德|南海|禅城|三水|高明|北滘|里水|大沥).*")) return "FS";
        if (s.matches(".*(揭阳|普宁|汕头|澄海|潮南|潮阳|潮州|汕尾).*")) return "YD";
        if (s.matches(".*(广东|中山|珠海|惠州|江门|湛江|茂名|肇庆|清远|韶关|河源|梅州|阳江|云浮).*")) return "OTHER_GD";
        if (OUT_PROVINCE_PAT.matcher(s).find()) return "OUT_GD";
        return "GZ"; // 未显式带外省词时默认归入本园区所在广东主战区待核
    }

    public static String extractBuildingName(String address) {
        if (!StringUtils.hasText(address)) return null;
        Matcher m = BUILDING_PAT.matcher(address);
        return m.find() ? m.group(1) : null;
    }

    public boolean isGuangdongLockEnabled() {
        String lockProv = bizSettings.raw(MODULE, "sea_region_lock_province");
        return lockProv == null || lockProv.isBlank() || lockProv.contains("广东");
    }

    /**
     * 对齐企业或店铺背后的工商/电话/地址：
     * 1) 若开启广东锁且识别为外省 -> 立即跳过返回 SKIPPED_OUT_PROVINCE（0扣费）；
     * 2) 优先读 crm_ext_company_cache 30天有效缓存（0扣费）；
     * 3) 未配置 ext_qcc_appcode -> 诚实返回 UNCONFIGURED（绝不编造虚假法人/信用代码）。
     */
    public CompanyProfile alignCompany(Long tenantId, String companyOrShopName, String regionHint, boolean forceUnlockRegion) {
        if (!StringUtils.hasText(companyOrShopName)) {
            return new CompanyProfile("UNCONFIGURED", null, null, null, null, null, null, null, null, null, null, 0, List.of());
        }
        String key = MktCompetitionProtectionService.normalize(companyOrShopName);
        String combinedHint = (regionHint == null ? "" : regionHint) + " " + companyOrShopName;
        if (!forceUnlockRegion && isGuangdongLockEnabled() && "OUT_GD".equals(classifyZone(combinedHint, null))) {
            return new CompanyProfile("SKIPPED_OUT_PROVINCE", companyOrShopName.trim(), null, null, null, null, null,
                    "外省", null, null, regionHint, 0, List.of());
        }

        // 1. 查询 30 天本地缓存
        List<Map<String, Object>> cached = jdbc.queryForList("""
                SELECT company_name, credit_code, legal_person, reg_capital, insured_count, phone,
                       province, city, district, address, dishonest_count, shops_json
                  FROM crm_ext_company_cache
                 WHERE tenant_id = ? AND lookup_key = ? AND expires_at > NOW()
                 LIMIT 1
                """, tenantId, key);
        if (!cached.isEmpty()) {
            Map<String, Object> c = cached.get(0);
            return new CompanyProfile("CACHE_HIT",
                    str(c.get("company_name")), str(c.get("credit_code")), str(c.get("legal_person")),
                    str(c.get("reg_capital")), numInt(c.get("insured_count")), str(c.get("phone")),
                    str(c.get("province")), str(c.get("city")), str(c.get("district")),
                    str(c.get("address")), Optional.ofNullable(numInt(c.get("dishonest_count"))).orElse(0),
                    parseShopsJson(str(c.get("shops_json"))));
        }

        // 2. 多通道热插拔工商与联系方式穿透链：企查查官方 QCC_PRO -> 天眼查 TYC_PRO -> 阿里云/APISpace 百元包 LIGHT_CORP
        CompanyProfile fetched = tryQccOfficial(tenantId, companyOrShopName);
        if (fetched == null) fetched = tryTianyanchaOfficial(tenantId, companyOrShopName);
        if (fetched == null) fetched = tryLightCorpPack(tenantId, companyOrShopName);
        if (fetched == null) {
            return new CompanyProfile("UNCONFIGURED", null, null, null, null, null, null, null, null, null, null, 0, List.of());
        }

        // 若工商照面未含电话且已点亮探迹 TUNGEE_API，则补全老板/供应链实名手机
        String enrichedPhone = fetched.phone();
        if (!StringUtils.hasText(enrichedPhone)) {
            enrichedPhone = tryTungeeBossPhone(tenantId, fetched.companyName());
        }
        String shopsJson = "[]";
        try { shopsJson = json.writeValueAsString(fetched.extraShops()); } catch (Exception ignored) {}

        // 写入 30 天本地省钱缓存
        jdbc.update("""
                INSERT INTO crm_ext_company_cache
                  (tenant_id, lookup_key, company_name, credit_code, legal_person, reg_capital, insured_count,
                   phone, province, city, district, address, dishonest_count, shops_json, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL 30 DAY))
                ON DUPLICATE KEY UPDATE
                  company_name=VALUES(company_name), credit_code=VALUES(credit_code), legal_person=VALUES(legal_person),
                  reg_capital=VALUES(reg_capital), insured_count=VALUES(insured_count), phone=VALUES(phone),
                  address=VALUES(address), dishonest_count=VALUES(dishonest_count), shops_json=VALUES(shops_json),
                  expires_at=DATE_ADD(NOW(), INTERVAL 30 DAY)
                """, tenantId, key, fetched.companyName(), fetched.creditCode(), fetched.legalPerson(),
                fetched.regCapital(), fetched.insuredCount(), enrichedPhone, fetched.province(),
                fetched.city(), fetched.district(), fetched.address(), fetched.dishonestCount(), shopsJson);

        return new CompanyProfile("API_ALIGNED", fetched.companyName(), fetched.creditCode(), fetched.legalPerson(),
                fetched.regCapital(), fetched.insuredCount(), enrichedPhone, fetched.province(),
                fetched.city(), fetched.district(), fetched.address(), fetched.dishonestCount(), fetched.extraShops());
    }

    private CompanyProfile tryQccOfficial(Long tenantId, String keyword) {
        if (connectorHub == null) return null;
        Optional<Map<String, String>> credsOpt = connectorHub.acquireActiveCredentials(tenantId, "QCC_PRO", false);
        if (credsOpt.isEmpty()) return null;
        Map<String, String> creds = credsOpt.get();
        String appKey = trimNull(creds.get("appKey"));
        String secretKey = trimNull(creds.get("secretKey"));
        if (!StringUtils.hasText(appKey) || !StringUtils.hasText(secretKey)) return null;
        try {
            String timespan = String.valueOf(System.currentTimeMillis() / 1000);
            String token = DigestUtil.md5Hex(appKey + timespan + secretKey).toUpperCase(Locale.ROOT);
            String url = "https://api.qichacha.com/ECIV4/GetBasicDetailsByName?key=" + URLEncoder.encode(appKey, StandardCharsets.UTF_8)
                    + "&keyword=" + URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                    .header("Token", token).header("Timespan", timespan).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) return null;
            return parseStandardCompanyNode(json.readTree(resp.body()).path("Result"));
        } catch (Exception e) {
            log.warn("[qcc-pro] 企查查官方接口调用异常: {}", e.getMessage());
            return null;
        }
    }

    private CompanyProfile tryTianyanchaOfficial(Long tenantId, String keyword) {
        if (connectorHub == null) return null;
        Optional<Map<String, String>> credsOpt = connectorHub.acquireActiveCredentials(tenantId, "TYC_PRO", false);
        if (credsOpt.isEmpty()) return null;
        String token = trimNull(credsOpt.get().get("token"));
        if (!StringUtils.hasText(token)) return null;
        try {
            String url = "https://open.api.tianyancha.com/services/open/ic/baseinfoV2/2.0?keyword="
                    + URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                    .header("Authorization", token).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) return null;
            return parseStandardCompanyNode(json.readTree(resp.body()).path("result"));
        } catch (Exception e) {
            log.warn("[tyc-pro] 天眼查接口调用异常: {}", e.getMessage());
            return null;
        }
    }

    private CompanyProfile tryLightCorpPack(Long tenantId, String keyword) {
        String appCode = trimNull(bizSettings.raw(MODULE, "ext_qcc_appcode"));
        String endpoint = trimNull(bizSettings.raw(MODULE, "ext_qcc_endpoint"));
        if ((!StringUtils.hasText(appCode) || !StringUtils.hasText(endpoint)) && connectorHub != null) {
            Optional<Map<String, String>> opt = connectorHub.acquireActiveCredentials(tenantId, "LIGHT_CORP", false);
            if (opt.isPresent()) {
                appCode = trimNull(opt.get().get("appCode"));
                endpoint = trimNull(opt.get().get("endpoint"));
            }
        }
        if (!StringUtils.hasText(appCode) || !StringUtils.hasText(endpoint)) return null;
        try {
            String url = endpoint + (endpoint.contains("?") ? "&" : "?") + "keyword=" + URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                    .header("Authorization", "APPCODE " + appCode).header("Token", appCode).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) return null;
            JsonNode root = json.readTree(resp.body());
            JsonNode data = root.has("Result") ? root.path("Result") : root.has("result") ? root.path("result") : root.path("data");
            return parseStandardCompanyNode(data);
        } catch (Exception e) {
            log.warn("[light-corp] 轻量工商网关异常: {}", e.getMessage());
            return null;
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
        if (data == null || data.isMissingNode()) return null;
        if (data.isArray() && !data.isEmpty()) data = data.get(0);
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
                if (StringUtils.hasText(sn)) shops.add(Map.of("platform", firstText(s, "Platform", "platform") != null ? firstText(s, "Platform", "platform") : "天猫", "shopName", sn));
            }
        }
        return new CompanyProfile("API_ALIGNED", corpName, credit, legal, cap, insured, phone, prov, city, dist, addr, dishonest, shops);
    }

    /** 高德免费地理编码：将广东详细地址转为 GCJ-02 经纬度与大厦名。未配置 Key 时返回 empty 诚实降级。 */
    public Optional<GeoPoint> geocodeAddress(String address, String cityHint) {
        if (!StringUtils.hasText(address)) return Optional.empty();
        String amapKey = resolveAmapWebKey();
        if (!StringUtils.hasText(amapKey)) return Optional.empty();
        try {
            String url = "https://restapi.amap.com/v3/geocode/geo?key=" + URLEncoder.encode(amapKey, StandardCharsets.UTF_8)
                    + "&address=" + URLEncoder.encode(address.trim(), StandardCharsets.UTF_8)
                    + (StringUtils.hasText(cityHint) ? "&city=" + URLEncoder.encode(cityHint.trim(), StandardCharsets.UTF_8) : "&city=" + URLEncoder.encode("广东", StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = json.readTree(resp.body());
            JsonNode geocodes = root.path("geocodes");
            if (!"1".equals(root.path("status").asText()) || !geocodes.isArray() || geocodes.isEmpty()) {
                return Optional.empty();
            }
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

    /** 高德周边 10km 商圈 POI 扫楼雷达：搜直播基地/电商园/服饰化妆品公司。 */
    public List<PoiCandidate> scanAroundPois(BigDecimal centerLng, BigDecimal centerLat, int radiusMeters, String keywords) {
        String amapKey = resolveAmapWebKey();
        if (!StringUtils.hasText(amapKey)) {
            throw new BizException(400, "尚未配置高德开放平台免费 Web服务 Key，请先在「规则与字典 › 外部数据连接器中心」填入后再启动扫楼雷达");
        }
        BigDecimal lng = centerLng != null ? centerLng : new BigDecimal("113.324520"); // 默认广州白云/番禺电商轴心
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
                        p.path("cityname").isTextual() ? p.path("cityname").asText() : "广州市",
                        p.path("adname").isTextual() ? p.path("adname").asText() : null,
                        new BigDecimal(loc[0]).setScale(6, RoundingMode.HALF_UP),
                        new BigDecimal(loc[1]).setScale(6, RoundingMode.HALF_UP),
                        firstTel,
                        p.path("type").asText("电商相关主体")
                ));
            }
            return list;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(502, "请求高德开放平台超时，请检查网络或 Key 配额");
        }
    }

    /** 精确计算两个 GCJ-02 坐标点之间的球面直线米数（用于 500m GPS 到访打卡校验与最近云仓测距）。 */
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

    /** 连接器公开脱敏状态回显（供公海页与设置页读取）。 */
    public Map<String, Object> connectorStatus() {
        String amapWeb = resolveAmapWebKey();
        String amapJs = resolveAmapJsKey();
        String qccCode = trimNull(bizSettings.raw(MODULE, "ext_qcc_appcode"));
        boolean anyCorp = StringUtils.hasText(qccCode);
        int activeTotal = 4;
        int totalSlots = 18;
        if (connectorHub != null) {
            try {
                Map<String, Object> hub = connectorHub.hubOverview();
                Map<?, ?> sum = (Map<?, ?>) hub.get("summary");
                activeTotal = sum.get("activeConnectors") instanceof Number n ? n.intValue() : 4;
                totalSlots = sum.get("totalConnectors") instanceof Number n ? n.intValue() : 18;
                List<?> list = (List<?>) hub.get("connectors");
                if (list != null) {
                    anyCorp = anyCorp || list.stream().filter(Map.class::isInstance).map(Map.class::cast)
                            .anyMatch(m -> "CORP".equals(m.get("categoryCode")) && Boolean.TRUE.equals(m.get("enabled")) && Boolean.TRUE.equals(m.get("configured")));
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
        out.put("activeConnectors", activeTotal);
        out.put("totalConnectors", totalSlots);
        return out;
    }

    private String resolveAmapWebKey() {
        String k = trimNull(bizSettings.raw(MODULE, "ext_amap_web_key"));
        if (StringUtils.hasText(k)) return k;
        if (connectorHub != null) {
            Map<String, String> c = connectorHub.loadPlainCredentials(1L, "AMAP_LBS");
            return trimNull(c.get("webServiceKey"));
        }
        return null;
    }

    private String resolveAmapJsKey() {
        String k = trimNull(bizSettings.raw(MODULE, "ext_amap_js_key"));
        if (StringUtils.hasText(k)) return k;
        if (connectorHub != null) {
            Map<String, String> c = connectorHub.loadPlainCredentials(1L, "AMAP_LBS");
            return trimNull(c.get("webJsKey"));
        }
        return null;
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
                if (!clean.isEmpty()) return Integer.parseInt(clean);
            }
        }
        return null;
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static String trimNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static Integer numInt(Object o) { return o instanceof Number n ? n.intValue() : null; }
}
