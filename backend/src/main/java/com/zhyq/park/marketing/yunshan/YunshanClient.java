package com.zhyq.park.marketing.yunshan;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 云山 V1.1 外部接口:只读取实体目录与每日票数,不请求联系方式或订单明细。 */
@Slf4j
@Component
public class YunshanClient {

    public static final int MAX_PAGE_SIZE = 100;
    static final String PATH_OWNERS = "/v1/external/entity/owners";
    static final String PATH_WAREHOUSES = "/v1/external/entity/warehouses";
    static final String PATH_VOLUME = "/v1/external/stockout/volume/query";
    private static final String OK = "00000";
    private static final String NO_STAT_DATA = "NO_STAT_DATA";
    private static final String HAS_STAT_DATA = "HAS_STAT_DATA";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String appId;
    private final String publicKeyText;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile PublicKey publicKey;

    public YunshanClient(ObjectMapper objectMapper,
                         @Value("${zhyq.yunshan.base-url:}") String baseUrl,
                         @Value("${zhyq.yunshan.app-id:}") String appId,
                         @Value("${zhyq.yunshan.public-key:}") String publicKeyText) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.appId = appId;
        this.publicKeyText = publicKeyText;
    }

    /** 仅供同包的连接初始化使用，凭证不得经接口返回。 */
    String rawAppId() { return appId; }
    String rawPublicKey() { return publicKeyText; }

    public boolean configured() {
        return StringUtils.hasText(baseUrl) && StringUtils.hasText(appId) && StringUtils.hasText(publicKeyText);
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String maskedAppId() {
        if (!StringUtils.hasText(appId)) return "";
        if (appId.length() <= 8) return "****";
        return appId.substring(0, 3) + "****" + appId.substring(appId.length() - 3);
    }

    public record Entity(String no, String name) {
    }

    public record EntityPage(List<Entity> records, long total, long pages) {
    }

    public record DayVolume(LocalDate statDate, long consignNum) {
    }

    public record DailyPage(List<DayVolume> records, long total, long pages, long totalConsignNum,
                            String dataState, LocalDate latestStatDate, LocalDateTime dataAsOf) {
    }

    public record Summary(long consignNum, String dataState, LocalDate latestStatDate, LocalDateTime dataAsOf) {
    }

    public EntityPage owners(String keyword, String parentWarehouseNo, int current) {
        validateCurrent(current);
        JsonNode data = get(PATH_OWNERS, params("keyword", keyword, "parentWarehouseNo", parentWarehouseNo,
                "current", String.valueOf(current), "size", "20"));
        return entityPage(data, "parentOwnerNo", "parentOwnerName", current);
    }

    public EntityPage warehouses(String keyword, String parentOwnerNo, int current) {
        validateCurrent(current);
        JsonNode data = get(PATH_WAREHOUSES, params("keyword", keyword, "parentOwnerNo", parentOwnerNo,
                "current", String.valueOf(current), "size", "20"));
        return entityPage(data, "parentWarehouseNo", "parentWarehouseName", current);
    }

    public DailyPage daily(String ownerNo, String warehouseNo, LocalDate start, LocalDate end, int current) {
        validateCurrent(current);
        validateDates(start, end);
        JsonNode data = get(PATH_VOLUME, params("parentOwnerNo", ownerNo, "parentWarehouseNo", warehouseNo,
                "startDate", start.toString(), "endDate", end.toString(), "queryType", "daily",
                "current", String.valueOf(current), "size", String.valueOf(MAX_PAGE_SIZE)));
        validateVolumeScope(data, "daily", ownerNo, warehouseNo, start, end);
        List<DayVolume> days = new ArrayList<>();
        for (JsonNode r : records(data)) {
            days.add(new DayVolume(date(requiredText(r, "statDate"), "statDate"), longOf(r, "consignNum")));
        }
        long total = longOf(data, "total");
        long pages = longOf(data, "pages");
        long totalConsignNum = longOf(data, "totalConsignNum");
        validatePage(data, current, MAX_PAGE_SIZE, days.size(), total, pages);
        String state = dataState(data);
        LocalDate latest = dateOrNull(data, "latestStatDate");
        LocalDateTime asOf = dateTimeOrNull(data, "dataAsOf");
        if (NO_STAT_DATA.equals(state) && (!days.isEmpty() || total != 0 || totalConsignNum != 0 || asOf != null)) {
            throw badResponse("NO_STAT_DATA 与明细或合计不一致");
        }
        if (HAS_STAT_DATA.equals(state) && total == 0) throw badResponse("HAS_STAT_DATA 缺少统计日期");
        return new DailyPage(List.copyOf(days), total, pages, totalConsignNum, state, latest, asOf);
    }

    public Summary summary(String ownerNo, String warehouseNo, LocalDate start, LocalDate end) {
        validateDates(start, end);
        JsonNode data = get(PATH_VOLUME, params("parentOwnerNo", ownerNo, "parentWarehouseNo", warehouseNo,
                "startDate", start.toString(), "endDate", end.toString(), "queryType", "summary"));
        validateVolumeScope(data, "summary", ownerNo, warehouseNo, start, end);
        long count = longOf(data, "consignNum");
        String state = dataState(data);
        LocalDate latest = dateOrNull(data, "latestStatDate");
        LocalDateTime asOf = dateTimeOrNull(data, "dataAsOf");
        if (NO_STAT_DATA.equals(state) && (count != 0 || asOf != null)) {
            throw badResponse("NO_STAT_DATA 与合计或更新时间不一致");
        }
        return new Summary(count, state, latest, asOf);
    }

    /** 省略空参数;非空参数(包括空白字符)不得悄悄降级为未筛选。 */
    static Map<String, String> params(String... kv) {
        if (kv.length % 2 != 0) throw new IllegalArgumentException("参数必须成对提供");
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            if (kv[i + 1] != null && !kv[i + 1].isEmpty()) m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    JsonNode get(String path, Map<String, String> params) {
        if (!configured()) throw new YunshanApiException("NOT_CONFIGURED", "未配置云山地址/appId/公钥");
        // 使用同一份快照构造 Query 和签名,发送前不再修改参数。
        Map<String, String> requestParams = Map.copyOf(params);
        String query = requestParams.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue())).collect(Collectors.joining("&"));
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(URI.create(baseUrl + path + (query.isEmpty() ? "" : "?" + query)))
                    .timeout(Duration.ofSeconds(20))
                    .header("appId", appId)
                    .header("sign", YunshanSigner.sign(requestParams, key()))
                    .GET().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new YunshanApiException("INVALID_CONFIG", "地址、appId 或公钥配置无效");
        }
        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("[yunshan] {} 网络异常", path);
            throw new YunshanApiException("NETWORK", "网络异常,请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new YunshanApiException("NETWORK", "请求被中断");
        }
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new YunshanApiException("HTTP_" + resp.statusCode(), "HTTP 请求失败");
        }
        JsonNode body;
        try {
            body = objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(resp.body());
        } catch (IOException e) {
            throw badResponse("返回不是有效 JSON");
        }
        if (body == null || !body.isObject()) throw badResponse("返回不是 JSON 对象");
        String code = requiredText(body, "code");
        if (!body.path("success").isBoolean()) throw badResponse("success 不是布尔值");
        if (!OK.equals(code) || !body.path("success").booleanValue()) {
            throw new YunshanApiException(OK.equals(code) ? "BAD_RESPONSE" : code,
                    body.path("msg").isTextual() ? body.path("msg").textValue() : "调用失败");
        }
        if (!body.path("data").isObject()) throw badResponse("返回缺少 data");
        return body.path("data");
    }

    private PublicKey key() {
        PublicKey k = publicKey;
        if (k == null) {
            k = YunshanSigner.parsePublicKey(publicKeyText);
            publicKey = k;
        }
        return k;
    }

    private static EntityPage entityPage(JsonNode data, String noField, String nameField, int current) {
        List<Entity> list = new ArrayList<>();
        for (JsonNode r : records(data)) {
            list.add(new Entity(requiredText(r, noField), requiredText(r, nameField)));
        }
        long total = longOf(data, "total");
        long pages = longOf(data, "pages");
        validatePage(data, current, 20, list.size(), total, pages);
        return new EntityPage(List.copyOf(list), total, pages);
    }

    private static void validatePage(JsonNode data, int current, int size, int count, long total, long pages) {
        long expectedPages = total / size + (total % size == 0 ? 0 : 1);
        long expectedCount = current > pages ? 0 : Math.min(size, total - (long) (current - 1) * size);
        if (longOf(data, "current") != current || longOf(data, "size") != size || pages != expectedPages
                || count != expectedCount) throw badResponse("分页信息与请求或记录数不一致");
    }

    /** 合计相同也不能把另一实体组合或区间的响应写到当前映射。 */
    private static void validateVolumeScope(JsonNode data, String type, String ownerNo, String warehouseNo,
                                            LocalDate start, LocalDate end) {
        if (!type.equals(requiredText(data, "queryType")) || !start.toString().equals(requiredText(data, "startDate"))
                || !end.toString().equals(requiredText(data, "endDate"))) {
            throw badResponse("响应查询类型或日期范围与请求不一致");
        }
        validateFilterEcho(data, "parentOwnerNo", ownerNo);
        validateFilterEcho(data, "parentWarehouseNo", warehouseNo);
    }

    private static void validateFilterEcho(JsonNode data, String field, String expected) {
        JsonNode value = data.get(field);
        boolean omitted = expected == null || expected.isEmpty();
        if (value == null || (omitted ? !value.isNull() : !value.isTextual() || !expected.equals(value.textValue()))) {
            throw badResponse("响应实体筛选 " + field + " 与请求不一致");
        }
    }

    private static JsonNode records(JsonNode data) {
        JsonNode records = data.get("records");
        if (records == null || !records.isArray()) throw badResponse("缺少 records 数组");
        return records;
    }

    private static String dataState(JsonNode data) {
        String state = requiredText(data, "dataState");
        if (!NO_STAT_DATA.equals(state) && !HAS_STAT_DATA.equals(state)) throw badResponse("未知 dataState");
        return state;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** 实体 ID 等字符串按原值保留,尤其不能把超过 JS 安全整数范围的 ID 转成数值。 */
    private static String requiredText(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || !v.isTextual() || v.textValue().isBlank()) throw badResponse("缺少字符串字段 " + field);
        return v.textValue();
    }

    private static long longOf(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || !v.isIntegralNumber() || !v.canConvertToLong() || v.longValue() < 0) {
            throw badResponse(field + " 必须为非负 64 位整数");
        }
        return v.longValue();
    }

    private static LocalDate date(String value, String field) {
        try {
            if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) throw badResponse(field + " 日期格式无效");
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw badResponse(field + " 日期格式无效");
        }
    }

    private static LocalDate dateOrNull(JsonNode n, String field) {
        JsonNode value = n.get(field);
        if (value == null) throw badResponse("缺少字段 " + field);
        return value.isNull() ? null : date(requiredText(n, field), field);
    }

    private static LocalDateTime dateTimeOrNull(JsonNode n, String field) {
        JsonNode value = n.get(field);
        if (value == null) throw badResponse("缺少字段 " + field);
        if (value.isNull()) return null;
        try {
            return LocalDateTime.parse(requiredText(n, field), DATE_TIME);
        } catch (DateTimeParseException e) {
            throw badResponse(field + " 时间格式无效");
        }
    }

    private static void validateCurrent(int current) {
        if (current < 1) throw new YunshanApiException("BAD_REQUEST", "页码必须为正整数");
    }

    private static void validateDates(LocalDate start, LocalDate end) {
        if (start == null || end == null || start.isAfter(end)) {
            throw new YunshanApiException("BAD_REQUEST", "日期范围无效");
        }
    }

    private static YunshanApiException badResponse(String message) {
        return new YunshanApiException("BAD_RESPONSE", message);
    }
}
