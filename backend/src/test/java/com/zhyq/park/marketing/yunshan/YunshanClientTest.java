package com.zhyq.park.marketing.yunshan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.Cipher;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YunshanClientTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 2);
    private static final String APP_ID = "mock-yunshan-app";
    private static final String DAILY = """
            {"queryType":"daily","parentOwnerNo":"OWNER","parentWarehouseNo":"WH","startDate":"2026-10-01","endDate":"2026-10-02",
             "records":[{"statDate":"2026-10-01","consignNum":120},{"statDate":"2026-10-02","consignNum":180}],
             "total":2,"pages":1,"current":1,"size":100,"totalConsignNum":300,"dataState":"HAS_STAT_DATA",
             "latestStatDate":"2026-10-02","dataAsOf":"2026-10-03 00:10:00"}
            """;
    private static final String SUMMARY = """
            {"queryType":"summary","parentOwnerNo":"OWNER","parentWarehouseNo":"WH","startDate":"2026-10-01","endDate":"2026-10-02",
             "consignNum":300,"dataState":"HAS_STAT_DATA","latestStatDate":"2026-10-02","dataAsOf":"2026-10-03 00:10:00"}
            """;
    private static KeyPair keyPair;
    private static String publicKey;
    private HttpServer server;
    private YunshanClient client;
    private String baseUrl;
    private volatile String response;
    private volatile int status;
    private final AtomicReference<Request> lastRequest = new AtomicReference<>();

    private record Request(String method, String path, String rawQuery, String appId, String sign) {}

    @BeforeAll static void keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    @BeforeEach void start() throws Exception {
        status = 200;
        response = success(SUMMARY);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", exchange -> {
            lastRequest.set(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawQuery(), exchange.getRequestHeaders().getFirst("appId"),
                    exchange.getRequestHeaders().getFirst("sign")));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
        client = new YunshanClient(JSON, baseUrl + "/", APP_ID, publicKey);
    }

    @AfterEach void stop() {
        server.stop(0);
    }

    @Test void signsTheExactDailyQueryAndReadsDatesAndCounts() throws Exception {
        response = success(DAILY.replace("\"OWNER\"", "\"0009007199254740993\"").replace("\"WH\"", "\"WH-1\""));
        YunshanClient.DailyPage page = client.daily("0009007199254740993", "WH-1", START, END, 1);
        assertThat(page.records()).containsExactly(new YunshanClient.DayVolume(START, 120), new YunshanClient.DayVolume(END, 180));
        assertThat(page.totalConsignNum()).isEqualTo(300);
        assertThat(page.latestStatDate()).isEqualTo(END);
        assertThat(page.dataAsOf()).isEqualTo(LocalDateTime.of(2026, 10, 3, 0, 10));
        Map<String, String> sent = assertSignedRequest(YunshanClient.PATH_VOLUME);
        assertThat(sent).containsExactlyInAnyOrderEntriesOf(Map.of(
                "parentOwnerNo", "0009007199254740993", "parentWarehouseNo", "WH-1",
                "startDate", "2026-10-01", "endDate", "2026-10-02", "queryType", "daily", "current", "1", "size", "100"));
    }

    @Test void directoriesKeepLongAndLeadingZeroIdsAndSignFiltersAndPagination() throws Exception {
        response = success("""
                {"records":[{"parentOwnerNo":"0009007199254740993","parentOwnerName":"棒棒猪"}],
                 "current":2,"size":20,"total":21,"pages":2}
                """);
        YunshanClient.EntityPage owners = client.owners("棒棒猪 & A+B/仓", "WH-001", 2);
        assertThat(owners.records()).containsExactly(new YunshanClient.Entity("0009007199254740993", "棒棒猪"));
        assertThat(owners.total()).isEqualTo(21);
        assertThat(owners.pages()).isEqualTo(2);
        assertThat(assertSignedRequest(YunshanClient.PATH_OWNERS)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "keyword", "棒棒猪 & A+B/仓", "parentWarehouseNo", "WH-001", "current", "2", "size", "20"));
        assertThat(lastRequest.get().rawQuery()).contains("%26", "%2B", "%20", "%2F");

        response = success("""
                {"records":[{"parentWarehouseNo":"000800001","parentWarehouseName":"云仓"}],
                 "current":1,"size":20,"total":1,"pages":1}
                """);
        assertThat(client.warehouses(null, "0009007199254740993", 1).records())
                .containsExactly(new YunshanClient.Entity("000800001", "云仓"));
        assertThat(assertSignedRequest(YunshanClient.PATH_WAREHOUSES)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "parentOwnerNo", "0009007199254740993", "current", "1", "size", "20"));
    }

    @Test void summaryOmitsPaginationAndAbsentFiltersAndKeeps64BitCounts() throws Exception {
        response = success(SUMMARY.replace("300", "9223372036854775807").replace("\"OWNER\"", "null").replace("\"WH\"", "null"));
        assertThat(client.summary(null, "", START, END).consignNum()).isEqualTo(Long.MAX_VALUE);
        assertThat(assertSignedRequest(YunshanClient.PATH_VOLUME)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "startDate", "2026-10-01", "endDate", "2026-10-02", "queryType", "summary"));
    }

    @Test void explicitEmptyQueryValueIsIncludedInSignatureAndWhitespaceIsNotDropped() throws Exception {
        client.get(YunshanClient.PATH_VOLUME, Map.of("parentOwnerNo", "", "keyword", " "));
        assertThat(assertSignedRequest(YunshanClient.PATH_VOLUME))
                .containsExactlyInAnyOrderEntriesOf(Map.of("parentOwnerNo", "", "keyword", " "));
        assertThat(YunshanClient.params("parentOwnerNo", " ")).containsEntry("parentOwnerNo", " ");
    }

    @Test void noStatisticsAndRealZeroVolumeRemainDistinct() {
        response = success("""
                {"queryType":"daily","parentOwnerNo":"OWNER","parentWarehouseNo":"WH","startDate":"2026-10-01","endDate":"2026-10-02",
                 "records":[],"total":0,"pages":0,"current":1,"size":100,"totalConsignNum":0,
                 "dataState":"NO_STAT_DATA","latestStatDate":"2026-09-30","dataAsOf":null}
                """);
        YunshanClient.DailyPage absent = client.daily("OWNER", "WH", START, END, 1);
        assertThat(absent.records()).isEmpty();
        assertThat(absent.dataState()).isEqualTo("NO_STAT_DATA");
        assertThat(absent.latestStatDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(absent.dataAsOf()).isNull();

        response = success(DAILY.replace("120", "0").replace("180", "0").replace("300", "0"));
        YunshanClient.DailyPage zero = client.daily("OWNER", "WH", START, END, 1);
        assertThat(zero.records()).hasSize(2).allMatch(day -> day.consignNum() == 0);
        assertThat(zero.dataState()).isEqualTo("HAS_STAT_DATA");

        response = success("""
                {"queryType":"summary","parentOwnerNo":"OWNER","parentWarehouseNo":"WH","startDate":"2026-10-01","endDate":"2026-10-02",
                 "consignNum":0,"dataState":"NO_STAT_DATA","latestStatDate":null,"dataAsOf":null}
                """);
        assertThat(client.summary("OWNER", "WH", START, END).dataState()).isEqualTo("NO_STAT_DATA");
    }

    @ParameterizedTest
    @ValueSource(strings = {"BIZ_SCOPE_NOT_CONFIGURED", "BIZ_SCOPE_FORBIDDEN", "BIZ_ENTITY_MAPPING_INVALID"})
    void preservesBusinessFailureCodes(String code) {
        response = "{\"code\":\"" + code + "\",\"success\":false,\"msg\":\"请核对授权\"}";
        assertApiCode(() -> client.summary("OWNER", "WH", START, END), code);
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 400, 401, 500})
    void rejectsHttpFailuresEvenWithSuccessEnvelope(int code) {
        status = code;
        assertApiCode(() -> client.summary("OWNER", "WH", START, END), "HTTP_" + code);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "<html>error</html>", "{}",
            "{\"code\":0,\"success\":true,\"data\":{}}",
            "{\"code\":\"00000\",\"success\":\"true\",\"data\":{}}",
            "{\"code\":\"00000\",\"success\":1,\"data\":{}}",
            "{\"code\":\"00000\",\"success\":false,\"data\":{}}",
            "{\"code\":\"00000\",\"success\":true}",
            "{\"code\":\"00000\",\"success\":true,\"data\":[]} {}"})
    void rejectsMalformedOrNonBooleanSuccessEnvelopes(String body) {
        response = body;
        assertApiCode(() -> client.summary("OWNER", "WH", START, END), "BAD_RESPONSE");
    }

    static Stream<Arguments> invalidDailyFields() {
        return Stream.of(
                Arguments.of("records", null), Arguments.of("records", "null"), Arguments.of("records", "{}"),
                Arguments.of("total", null), Arguments.of("total", "null"), Arguments.of("total", "-1"),
                Arguments.of("total", "1.5"), Arguments.of("total", "\"2\""), Arguments.of("total", "9223372036854775808"),
                Arguments.of("totalConsignNum", null), Arguments.of("totalConsignNum", "1.5"),
                Arguments.of("totalConsignNum", "9223372036854775808"), Arguments.of("pages", null),
                Arguments.of("pages", "2"), Arguments.of("current", "2"), Arguments.of("size", "20"),
                Arguments.of("dataState", null), Arguments.of("dataState", "\"UNKNOWN\""),
                Arguments.of("dataState", "\"NO_STAT_DATA\""), Arguments.of("latestStatDate", null),
                Arguments.of("latestStatDate", "\"2026-02-30\""), Arguments.of("dataAsOf", "\"2026-02-30 00:10:00\""));
    }

    @ParameterizedTest @MethodSource("invalidDailyFields")
    void rejectsMissingMalformedOverflowAndInconsistentDailyData(String field, String value) throws Exception {
        ObjectNode data = (ObjectNode) JSON.readTree(DAILY);
        if (value == null) data.remove(field);
        else data.set(field, JSON.readTree(value));
        response = success(data.toString());
        assertApiCode(() -> client.daily("OWNER", "WH", START, END, 1), "BAD_RESPONSE");
    }

    @ParameterizedTest @ValueSource(strings = {"null", "1.1", "-1", "9223372036854775808", "\"12\""})
    void rejectsInvalidDailyCounts(String value) throws Exception {
        ObjectNode data = (ObjectNode) JSON.readTree(DAILY);
        ((ObjectNode) data.path("records").get(0)).set("consignNum", JSON.readTree(value));
        response = success(data.toString());
        assertApiCode(() -> client.daily("OWNER", "WH", START, END, 1), "BAD_RESPONSE");
    }

    @Test void rejectsMissingSummaryCountAndContradictoryNoStatData() {
        response = success(SUMMARY.replace("\"consignNum\":300,", ""));
        assertApiCode(() -> client.summary("OWNER", "WH", START, END), "BAD_RESPONSE");
        response = success(SUMMARY.replace("HAS_STAT_DATA", "NO_STAT_DATA"));
        assertApiCode(() -> client.summary("OWNER", "WH", START, END), "BAD_RESPONSE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"queryType", "parentOwnerNo", "parentWarehouseNo", "startDate", "endDate"})
    void rejectsMismatchedOrMissingScopeEvenWhenVolumeTotalsMatch(String field) throws Exception {
        for (String payload : new String[]{DAILY, SUMMARY}) {
            for (String wrong : new String[]{null, "different-scope"}) {
                ObjectNode data = (ObjectNode) JSON.readTree(payload);
                if (wrong == null) data.remove(field);
                else data.put(field, wrong);
                response = success(data.toString());
                if (payload.equals(DAILY)) assertApiCode(() -> client.daily("OWNER", "WH", START, END, 1), "BAD_RESPONSE");
                else assertApiCode(() -> client.summary("OWNER", "WH", START, END), "BAD_RESPONSE");
            }
        }
    }

    @Test void unfilteredResponseMustEchoNullInsteadOfClaimingSingleEntity() {
        response = success(SUMMARY);
        assertApiCode(() -> client.summary(null, null, START, END), "BAD_RESPONSE");
        response = success(SUMMARY.replace("\"OWNER\"", "null").replace("\"WH\"", "null"));
        assertThat(client.summary(null, "", START, END).consignNum()).isEqualTo(300);
        assertApiCode(() -> client.summary("OWNER", "WH", START, END), "BAD_RESPONSE");
    }

    @Test void rejectsNumericEntityIdsInsteadOfCoercingThem() {
        response = success("""
                {"records":[{"parentOwnerNo":9007199254740993,"parentOwnerName":"示例货主"}],
                 "current":1,"size":20,"total":1,"pages":1}
                """);
        assertApiCode(() -> client.owners(null, null, 1), "BAD_RESPONSE");
    }

    @Test void unconfiguredOrInvalidClientAndInvalidParametersFailBeforeNetwork() {
        YunshanClient missing = new YunshanClient(JSON, baseUrl, "", publicKey);
        assertThat(missing.configured()).isFalse();
        assertThat(missing.maskedAppId()).isEmpty();
        assertApiCode(() -> missing.summary("OWNER", "WH", START, END), "NOT_CONFIGURED");
        YunshanClient invalid = new YunshanClient(JSON, baseUrl, APP_ID, "invalid-key");
        assertApiCode(() -> invalid.summary("OWNER", "WH", START, END), "INVALID_CONFIG");
        assertApiCode(() -> client.owners(null, null, 0), "BAD_REQUEST");
        assertApiCode(() -> client.daily("OWNER", "WH", END, START, 1), "BAD_REQUEST");
        assertThat(lastRequest.get()).isNull();
        assertThat(client.maskedAppId()).isEqualTo("moc****app");
        assertThat(new YunshanClient(JSON, baseUrl, "short", publicKey).maskedAppId()).isEqualTo("****");
    }

    private Map<String, String> assertSignedRequest(String path) throws Exception {
        Request request = lastRequest.get();
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.path()).isEqualTo("/api" + path);
        assertThat(request.appId()).isEqualTo(APP_ID);
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : request.rawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        byte[] json = JSON.writeValueAsBytes(new TreeMap<>(params));
        String expectedMd5 = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(json));
        Cipher decrypt = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        decrypt.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
        assertThat(new String(decrypt.doFinal(Base64.getDecoder().decode(request.sign())), StandardCharsets.UTF_8))
                .isEqualTo(expectedMd5);
        return params;
    }

    private static String success(String data) {
        return "{\"code\":\"00000\",\"success\":true,\"msg\":\"操作成功\",\"data\":" + data + "}";
    }

    private static void assertApiCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(YunshanApiException.class)
                .satisfies(error -> assertThat(((YunshanApiException) error).getApiCode()).isEqualTo(code));
    }
}
