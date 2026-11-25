package com.zhyq.park.marketing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class MktExtDataGateway {

    private static final String MODULE = "marketing";
    private static final Pattern BUILDING_PAT = Pattern.compile(
            "([\\u4e00-\\u9fa5A-Za-z0-9]{2,18}?(?:电商大厦|直播基地|电商园|产业园|科创园|创业园|电商城|服装城|美妆园|大厦|广场|中心))");
    private static final Pattern OUT_PROVINCE_PAT = Pattern.compile(
            "(北京|上海|天津|重庆|浙江|杭州|宁波|温州|义乌|嘉兴|湖州|绍兴|金华|江苏|南京|苏州|无锡|山东|济南|青岛|临沂|福建|福州|厦门|泉州|河北|石家庄|保定|河南|郑州|湖北|武汉|湖南|长沙|四川|成都|安徽|合肥|江西|南昌|辽宁|陕西|西安)");

    private final JdbcTemplate jdbc;
    private final BizSettings bizSettings;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

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

        // 2. 检查是否配置了外部轻量工商 API 凭证
        String appCode = trimNull(bizSettings.raw(MODULE, "ext_qcc_appcode"));
        String endpoint = trimNull(bizSettings.raw(MODULE, "ext_qcc_endpoint"));
        if (!StringUtils.hasText(appCode) || !StringUtils.hasText(endpoint)) {
            return new CompanyProfile("UNCONFIGURED", null, null, null, null, null, null, null, null, null, null, 0, List.of());
        }

        try {
            String url = endpoint + (endpoint.contains("?") ? "&" : "?") + "keyword=" + URLEncoder.encode(companyOrShopName.trim(), StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "APPCODE " + appCode)
                    .header("Token", appCode)
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.warn("[ext-qcc] 工商接口返回 HTTP {}", resp.statusCode());
                return new CompanyProfile("UNCONFIGURED", null, null, null, null, null, null, null, null, null, null, 0, List.of());
            }
            JsonNode root = json.readTree(resp.body());
            JsonNode data = root.has("Result") ? root.path("Result") : root.has("result") ? root.path("result") : root.path("data");
            if (data.isArray() && !data.isEmpty()) data = data.get(0);
            String corpName = firstText(data, "Name", "companyName", "entName", "name");
            if (!StringUtils.hasText(corpName)) {
                return new CompanyProfile("UNCONFIGURED", null, null, null, null, null, null, null, null, null, null, 0, List.of());
            }
            String credit = firstText(data, "CreditCode", "creditCode", "regNo");
            String legal = firstText(data, "OperName", "legalPerson", "frName");
            String cap = firstText(data, "RegistCapi", "regCapital", "regCap");
            Integer insured = firstInt(data, "InsuredCount", "insuredCount", "socialStaffNum");
            String phone = firstText(data, "ContactNumber", "phoneNumber", "tel", "phone");
            String prov = firstText(data, "Province", "province");
            String city = firstText(data, "City", "city");
            String dist = firstText(data, "District", "district", "county");
            String addr = firstText(data, "Address", "address", "regLocation");
            int dishonest = Optional.ofNullable(firstInt(data, "DishonestCount", "dishonestCount")).orElse(0);

            // 写入 30 天本地省钱缓存
            jdbc.update("""
                    INSERT INTO crm_ext_company_cache
                      (tenant_id, lookup_key, company_name, credit_code, legal_person, reg_capital, insured_count,
                       phone, province, city, district, address, dishonest_count, expires_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL 30 DAY))
                    ON DUPLICATE KEY UPDATE
                      company_name=VALUES(company_name), credit_code=VALUES(credit_code), legal_person=VALUES(legal_person),
                      reg_capital=VALUES(reg_capital), insured_count=VALUES(insured_count), phone=VALUES(phone),
                      address=VALUES(address), dishonest_count=VALUES(dishonest_count), expires_at=DATE_ADD(NOW(), INTERVAL 30 DAY)
                    """, tenantId, key, corpName, credit, legal, cap, insured, phone, prov, city, dist, addr, dishonest);

            return new CompanyProfile("API_ALIGNED", corpName, credit, legal, cap, insured, phone, prov, city, dist, addr, dishonest, List.of());
        } catch (Exception e) {
            log.warn("[ext-qcc] 调用外部工商网关异常: {}", e.getMessage());
            return new CompanyProfile("UNCONFIGURED", null, null, null, null, null, null, null, null, null, null, 0, List.of());
        }
    }

    /** 高德免费地理编码：将广东详细地址转为 GCJ-02 经纬度与大厦名。未配置 Key 时返回 empty 诚实降级。 */
    public Optional<GeoPoint> geocodeAddress(String address, String cityHint) {
        if (!StringUtils.hasText(address)) return Optional.empty();
        String amapKey = trimNull(bizSettings.raw(MODULE, "ext_amap_web_key"));
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
        String amapKey = trimNull(bizSettings.raw(MODULE, "ext_amap_web_key"));
        if (!StringUtils.hasText(amapKey)) {
            throw new BizException(400, "尚未配置高德开放平台免费 Web服务 Key，请先在「规则与字典 › 外部数据连接器」填入后再启动扫楼雷达");
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
        String amapWeb = trimNull(bizSettings.raw(MODULE, "ext_amap_web_key"));
        String amapJs = trimNull(bizSettings.raw(MODULE, "ext_amap_js_key"));
        String qccCode = trimNull(bizSettings.raw(MODULE, "ext_qcc_appcode"));
        String feiyuToken = trimNull(bizSettings.raw(MODULE, "ext_feiyu_token"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("regionLockProvince", Optional.ofNullable(trimNull(bizSettings.raw(MODULE, "sea_region_lock_province"))).orElse("广东省"));
        out.put("checkinMaxMeters", bizSettings.getInt(MODULE, "sea_checkin_max_meters", 500));
        out.put("visitProtectDays", bizSettings.getInt(MODULE, "sea_visit_protect_days", 15));
        out.put("amapWebConfigured", StringUtils.hasText(amapWeb));
        out.put("amapJsConfigured", StringUtils.hasText(amapJs));
        out.put("amapJsKey", StringUtils.hasText(amapJs) ? amapJs : ""); // Web端JS地图瓦片加载公钥
        out.put("qccConfigured", StringUtils.hasText(qccCode));
        out.put("feiyuSignatureEnabled", StringUtils.hasText(feiyuToken));
        return out;
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
