package com.zhyq.park.stats;

import com.zhyq.park.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
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

    public OpsDailyUsageController(JdbcTemplate jdbc,
                                   @Value("${zhyq.ops.daily-usage-token:}") String token) {
        this.jdbc = jdbc;
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
