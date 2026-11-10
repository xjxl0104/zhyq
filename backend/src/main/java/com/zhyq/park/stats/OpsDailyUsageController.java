package com.zhyq.park.stats;

import com.zhyq.park.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;

/**
 * 全平台应用日报的只读日汇总(A 机每小时拉一次)。
 *
 * <p>数据取 access_log(每个登录后的请求一行):写请求(POST/PUT/PATCH/DELETE 且成功)= 一次操作,
 * 首末时间按全部请求算。只回每人的次数和时刻,不回路由、参数等操作明细。</p>
 * <p>不走 JWT:用配置里的固定令牌 {@code zhyq.ops.daily-usage-token}(Bearer),没配置时接口不可用。</p>
 */
@Tag(name = "运维-应用日报")
@RestController
@RequestMapping("/ops")
public class OpsDailyUsageController {

    /** 令牌太短等于没配,避免随手填个弱口令就对外开放。 */
    static final int MIN_TOKEN_LENGTH = 32;
    private static final ZoneId BJ = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    static final String SQL = "SELECT u.portal_uid, COALESCE(NULLIF(u.nickname, ''), u.username) AS name, "
            + "SUM(a.method IN ('POST', 'PUT', 'PATCH', 'DELETE') AND a.status_code < 400) AS ops, "
            + "MIN(a.created_at) AS first_at, MAX(a.created_at) AS last_at "
            + "FROM access_log a JOIN sys_user u ON u.id = a.user_id "
            + "WHERE a.created_at >= ? AND a.created_at < ? "
            + "GROUP BY a.user_id, u.portal_uid, u.nickname, u.username "
            + "ORDER BY ops DESC, first_at";

    private final JdbcTemplate jdbc;
    private final byte[] token;
    private final Clock clock;

    @Autowired
    public OpsDailyUsageController(JdbcTemplate jdbc,
                                   @Value("${zhyq.ops.daily-usage-token:}") String token) {
        this(jdbc, token, Clock.systemUTC());
    }

    OpsDailyUsageController(JdbcTemplate jdbc, String token, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        String t = token == null ? "" : token.trim();
        this.token = t.length() >= MIN_TOKEN_LENGTH ? t.getBytes(StandardCharsets.UTF_8) : null;
    }

    @Operation(summary = "某天每人的操作次数与首末时间(北京时间)")
    @GetMapping("/daily-usage")
    public ResponseEntity<Result<Map<String, Object>>> dailyUsage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "date", required = false) String date) {
        if (token == null) {
            return fail(HttpStatus.SERVICE_UNAVAILABLE, "未配置日报令牌");
        }
        if (!authorized(authorization)) {
            return fail(HttpStatus.UNAUTHORIZED, "令牌无效");
        }
        LocalDate day;
        try {
            day = date == null || date.isBlank() ? LocalDate.now(BJ) : LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            return fail(HttpStatus.BAD_REQUEST, "date 须为 YYYY-MM-DD");
        }

        List<Map<String, Object>> rows = jdbc.queryForList(SQL,
                Timestamp.valueOf(day.atStartOfDay()), Timestamp.valueOf(day.plusDays(1).atStartOfDay()));
        List<Map<String, Object>> people = rows.stream().map(OpsDailyUsageController::person).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", day.toString());
        data.put("people", people);
        return ResponseEntity.ok(Result.ok(data));
    }

    private static final Set<String> PERIOD_PARAMS = Set.of("start", "end", "source_user_ids");
    private static final String WRITE = "a.method IN ('POST', 'PUT', 'PATCH', 'DELETE') AND a.status_code < 400";
    private static final String MARKETING = "(a.route = '/crm/marketing' OR a.route LIKE '/crm/marketing/%' OR a.route = '/marketing' OR a.route LIKE '/marketing/%')";

    /** Current backend account identity only; names and Portal UID guesses are never attribution inputs. */
    @Operation(summary = "按稳定后台账号查询本人写操作观测(只读,不声明日志留存完整)")
    @GetMapping("/perf-period")
    public ResponseEntity<Result<Map<String, Object>>> periodUsage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam MultiValueMap<String, String> query) {
        if (token == null) return fail(HttpStatus.SERVICE_UNAVAILABLE, "未配置日报令牌");
        if (!authorized(authorization)) return fail(HttpStatus.UNAUTHORIZED, "令牌无效");
        LocalDate start, end;
        List<Long> ids = new ArrayList<>();
        try {
            if (query == null || !query.keySet().equals(PERIOD_PARAMS)
                    || query.values().stream().anyMatch(values -> values.size() != 1)) throw new IllegalArgumentException();
            start = strictDay(query.getFirst("start")); end = strictDay(query.getFirst("end"));
            if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) >= 31
                    || end.isAfter(LocalDate.now(clock.withZone(BJ)))) throw new IllegalArgumentException();
            String value = query.getFirst("source_user_ids");
            if (value == null) throw new IllegalArgumentException();
            String[] requested = value.split(",", -1);
            if (requested.length < 1 || requested.length > 100) throw new IllegalArgumentException();
            Set<Long> unique = new LinkedHashSet<>();
            for (String id : requested) {
                if (!id.matches("admin:[1-9][0-9]{0,18}") || !unique.add(Long.parseLong(id.substring(6)))) throw new IllegalArgumentException();
            }
            ids.addAll(unique);
        } catch (RuntimeException ex) {
            return fail(HttpStatus.BAD_REQUEST, "期间或后台账号参数无效(最多31天、100个不同账号)");
        }
        try {
            String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
            List<Map<String, Object>> accounts = jdbc.queryForList(
                    "SELECT id FROM sys_user WHERE status = 1 AND deleted = 0 AND id IN (" + placeholders + ")", ids.toArray());
            Set<Long> known = new LinkedHashSet<>();
            for (Map<String, Object> row : accounts) {
                long id = identifier(row.get("id"));
                if (!ids.contains(id) || !known.add(id)) throw new IllegalStateException();
            }
            Map<Long, Map<LocalDate, long[]>> observations = new LinkedHashMap<>();
            if (!known.isEmpty()) {
                String wanted = String.join(",", java.util.Collections.nCopies(known.size(), "?"));
                String sql = "SELECT a.user_id, DATE(a.created_at) AS day, COUNT(*) AS observed_rows, "
                        + "SUM(CASE WHEN " + WRITE + " THEN 1 ELSE 0 END) AS ops, "
                        + "SUM(CASE WHEN " + WRITE + " AND " + MARKETING + " THEN 1 ELSE 0 END) AS marketing_ops "
                        + "FROM access_log a WHERE a.user_id IN (" + wanted + ") AND a.created_at >= ? AND a.created_at < ? "
                        + "GROUP BY a.user_id, DATE(a.created_at) ORDER BY a.user_id, DATE(a.created_at)";
                List<Object> parameters = new ArrayList<>(known);
                parameters.add(Timestamp.valueOf(start.atStartOfDay())); parameters.add(Timestamp.valueOf(end.plusDays(1).atStartOfDay()));
                for (Map<String, Object> row : jdbc.queryForList(sql, parameters.toArray())) {
                    long id = identifier(row.get("user_id")); LocalDate observedDay = strictDay(String.valueOf(row.get("day")));
                    long n = number(row.get("ops")), marketing = number(row.get("marketing_ops")), records = number(row.get("observed_rows"));
                    if (!known.contains(id) || observedDay.isBefore(start) || observedDay.isAfter(end) || records < 1
                            || n > records || marketing > n) throw new IllegalStateException();
                    Map<LocalDate, long[]> perDay = observations.computeIfAbsent(id, ignored -> new LinkedHashMap<>());
                    if (perDay.putIfAbsent(observedDay, new long[]{n, marketing, n - marketing}) != null) throw new IllegalStateException();
                }
            }
            List<Map<String, Object>> people = new ArrayList<>();
            for (long id : ids) {
                boolean mapped = known.contains(id); int observedDays = 0; long total = 0;
                List<Map<String, Object>> daily = new ArrayList<>();
                for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                    long[] counts = observations.getOrDefault(id, Map.of()).get(d);
                    if (counts != null) {
                        observedDays++; total = Math.addExact(total, counts[0]);
                        if (total > 9007199254740991L) throw new IllegalStateException();
                    }
                    Map<String, Object> scopes = new LinkedHashMap<>();
                    scopes.put("marketing_admin", counts == null ? null : counts[1]);
                    scopes.put("other_admin", counts == null ? null : counts[2]);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("day", d.toString()); row.put("state", !mapped ? "unmapped" : counts == null ? "missing" : "partial");
                    row.put("observed_count", counts == null ? null : counts[0]); row.put("complete", false); row.put("scopes", scopes);
                    daily.add(row);
                }
                List<String> reasons = new ArrayList<>(); reasons.add("retention_not_verified");
                if (!mapped) reasons.add("source_user_unmapped");
                if (end.equals(LocalDate.now(clock.withZone(BJ)))) reasons.add("current_day_incomplete");
                Map<String, Object> coverage = new LinkedHashMap<>();
                coverage.put("complete", false); coverage.put("expected_days", daily.size()); coverage.put("observed_days", observedDays);
                coverage.put("missing_days", daily.size() - observedDays); coverage.put("reason_codes", reasons);
                Map<String, Object> person = new LinkedHashMap<>();
                person.put("source_user_id", "admin:" + id); person.put("state", !mapped ? "unmapped" : observedDays == 0 ? "missing" : "partial");
                person.put("observed_count", observedDays == 0 ? null : total); person.put("coverage", coverage); person.put("daily", daily);
                people.add(person);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("schema_version", 1); data.put("source", "dipark_access_log"); data.put("metric", "authenticated_http_write_requests");
            data.put("timezone", "Asia/Shanghai"); data.put("as_of", clock.instant().toString());
            data.put("period", Map.of("start", start.toString(), "end", end.toString())); data.put("people", people);
            return ResponseEntity.ok(Result.ok(data));
        } catch (RuntimeException ex) {
            return fail(HttpStatus.BAD_GATEWAY, "后台操作观测暂不可用");
        }
    }

    private static LocalDate strictDay(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
        LocalDate d = LocalDate.parse(value);
        if (!d.toString().equals(value)) throw new IllegalArgumentException();
        return d;
    }

    private static long number(Object value) {
        if (!(value instanceof Number)) throw new IllegalStateException();
        long n = new BigDecimal(value.toString()).longValueExact();
        if (n < 0 || n > 9007199254740991L) throw new IllegalStateException();
        return n;
    }

    private static long identifier(Object value) {
        if (!(value instanceof Number)) throw new IllegalStateException();
        long id = new BigDecimal(value.toString()).longValueExact();
        if (id < 1) throw new IllegalStateException();
        return id;
    }

    private boolean authorized(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return false;
        byte[] given = authorization.substring(7).trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(token, given);
    }

    private static Map<String, Object> person(Map<String, Object> row) {
        Map<String, Object> p = new LinkedHashMap<>();
        Object uid = row.get("portal_uid");
        p.put("portalUid", uid == null || uid.toString().isBlank() ? null : uid.toString().trim());
        p.put("name", row.get("name"));
        Object ops = row.get("ops");
        p.put("ops", ops instanceof Number n ? n.longValue() : 0L);
        p.put("first", hhmm(row.get("first_at")));
        p.put("last", hhmm(row.get("last_at")));
        return p;
    }

    /** access_log.created_at 由 JVM(TZ=Asia/Shanghai)写入,本身就是北京时间。 */
    private static String hhmm(Object value) {
        if (value instanceof Timestamp ts) return ts.toLocalDateTime().format(HHMM);
        if (value instanceof LocalDateTime t) return t.format(HHMM);
        return null;
    }

    private static ResponseEntity<Result<Map<String, Object>>> fail(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Result.fail(status.value(), message));
    }
}
