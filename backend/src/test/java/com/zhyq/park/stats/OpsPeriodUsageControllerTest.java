package com.zhyq.park.stats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OpsPeriodUsageControllerTest {
    private static final String TOKEN = "fixture-dipark-period-read-only-key-123456";
    private static final String AUTH = "Bearer " + TOKEN;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T05:00:00Z"), ZoneOffset.UTC);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final OpsDailyUsageController controller = new OpsDailyUsageController(jdbc, TOKEN, CLOCK);

    private static MultiValueMap<String, String> query(String start, String end, String ids) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("start", start); q.add("end", end); q.add("source_user_ids", ids); return q;
    }
    private static MultiValueMap<String, String> query() { return query("2026-11-09", "2026-11-10", "admin:6"); }
    private static Map<String, Object> row(long id, String day, long ops, long marketing, long requests) {
        return Map.of("user_id", id, "day", java.sql.Date.valueOf(day), "ops", ops,
                "marketing_ops", marketing, "observed_rows", requests, "username", "PRIVATE_NAME", "email", "PRIVATE_EMAIL");
    }
    private void source(List<Long> ids, List<Map<String, Object>> rows) {
        doAnswer(inv ->
                inv.getArgument(0, String.class).startsWith("SELECT id FROM")
                        ? ids.stream().map(id -> Map.<String, Object>of("id", id)).toList() : rows)
                .when(jdbc).queryForList(anyString(), any(Object[].class));
    }
    @SuppressWarnings("unchecked") private static List<Map<String, Object>> people(Map<String, Object> data) { return (List<Map<String, Object>>) data.get("people"); }
    @SuppressWarnings("unchecked") private static List<Map<String, Object>> daily(Map<String, Object> person) { return (List<Map<String, Object>>) person.get("daily"); }
    private static Map<String, Object> body(ResponseEntity<Result<Map<String, Object>>> response) {
        assertEquals(200, response.getStatusCode().value()); assertNotNull(response.getBody()); return response.getBody().getData();
    }

    @Test void dedicatedCredentialPrecedesAllQueries() {
        assertEquals(503, new OpsDailyUsageController(jdbc, "", CLOCK).periodUsage(AUTH, query()).getStatusCode().value());
        for (String header : Arrays.asList(null, "", "Bearer model-key", TOKEN, AUTH + "wrong"))
            assertEquals(401, controller.periodUsage(header, query()).getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @Test void strictDatesExplicitIdsDuplicateAndExtraParametersAreRejectedBeforeSql() {
        List<MultiValueMap<String, String>> invalid = new ArrayList<>();
        invalid.add(query("2026-02-30", "2026-03-01", "admin:6"));
        invalid.add(query("2026-11-10", "2026-11-09", "admin:6"));
        invalid.add(query("2026-11-01", "2026-11-11", "admin:6"));
        invalid.add(query("2026-10-01", "2026-11-01", "admin:6"));
        invalid.add(query("2026-11-9", "2026-11-10", "admin:6"));
        for (String ids : List.of("", "6", "admin:0", "admin:06", "admin:6,admin:6", "mp:6", "admin:6,", "admin:6 OR 1=1", "admin:9999999999999999999"))
            invalid.add(query("2026-11-09", "2026-11-10", ids));
        invalid.add(query("2026-11-09", "2026-11-10", String.join(",", java.util.stream.LongStream.rangeClosed(1, 101).mapToObj(n -> "admin:" + n).toList())));
        MultiValueMap<String, String> duplicate = query(); duplicate.add("start", "2026-11-08"); invalid.add(duplicate);
        MultiValueMap<String, String> extra = query(); extra.add("username", "PRIVATE"); invalid.add(extra);
        MultiValueMap<String, String> missing = query(); missing.remove("source_user_ids"); invalid.add(missing);
        for (MultiValueMap<String, String> q : invalid) assertEquals(400, controller.periodUsage(AUTH, q).getStatusCode().value(), q.toString());
        verifyNoInteractions(jdbc);
    }

    @Test void observationsAreScopedToStableIdsAndMutuallyExclusiveBackendAreas() throws Exception {
        source(List.of(6L, 7L), List.of(row(6, "2026-11-09", 5, 5, 99), row(6, "2026-11-10", 12, 10, 111), row(7, "2026-11-09", 0, 0, 2)));
        Map<String, Object> data = body(controller.periodUsage(AUTH, query("2026-11-09", "2026-11-10", "admin:6,admin:7,admin:8")));
        assertEquals(Set.of("schema_version", "source", "metric", "timezone", "as_of", "period", "people"), data.keySet());
        assertEquals("authenticated_http_write_requests", data.get("metric")); assertEquals("Asia/Shanghai", data.get("timezone"));
        List<Map<String, Object>> people = people(data);
        assertEquals(List.of("admin:6", "admin:7", "admin:8"), people.stream().map(p -> p.get("source_user_id")).toList());
        assertEquals(17L, people.get(0).get("observed_count")); assertEquals("partial", people.get(0).get("state"));
        assertEquals(Map.of("marketing_admin", 10L, "other_admin", 2L), daily(people.get(0)).get(1).get("scopes"));
        assertEquals(0L, daily(people.get(1)).get(0).get("observed_count"));
        assertNull(daily(people.get(1)).get(1).get("observed_count"));
        assertEquals("unmapped", people.get(2).get("state")); assertNull(people.get(2).get("observed_count"));
        for (Map<String, Object> person : people) {
            Map<?, ?> coverage = (Map<?, ?>) person.get("coverage"); assertEquals(false, coverage.get("complete"));
            assertTrue(((List<?>) coverage.get("reason_codes")).contains("retention_not_verified"));
            for (Map<String, Object> d : daily(person)) assertEquals(false, d.get("complete"));
        }
        String json = new ObjectMapper().writeValueAsString(data);
        assertFalse(json.contains("PRIVATE")); assertFalse(json.contains("username")); assertFalse(json.contains("route"));
        var captures = org.mockito.ArgumentCaptor.forClass(Object[].class);
        var sqls = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).queryForList(sqls.capture(), captures.capture());
        assertTrue(sqls.getAllValues().get(1).contains("a.user_id IN (?,?)"));
        assertTrue(sqls.getAllValues().get(1).contains("a.status_code < 400"));
        assertFalse(sqls.getAllValues().get(1).contains("a.status_code >= 200"));
        assertFalse(sqls.getAllValues().get(1).contains("nickname"));
        assertArrayEquals(new Object[]{6L, 7L, Timestamp.valueOf("2026-11-09 00:00:00"), Timestamp.valueOf("2026-11-11 00:00:00")}, captures.getAllValues().get(1));
    }

    @Test void closedDayWithoutRetainedLogsRemainsUnknownAndDoesNotInventZero() {
        source(List.of(6L), List.of());
        Map<String, Object> person = people(body(controller.periodUsage(AUTH, query("2026-11-09", "2026-11-09", "admin:6")))).get(0);
        assertEquals("missing", person.get("state")); assertNull(person.get("observed_count"));
        assertNull(daily(person).get(0).get("observed_count")); assertEquals(false, daily(person).get(0).get("complete"));
    }

    @Test void thirtyOneDaysAreAllowedAndUnknownAccountDoesNotQueryItsLogs() {
        source(List.of(), List.of());
        Map<String, Object> person = people(body(controller.periodUsage(AUTH, query("2026-10-01", "2026-10-31", "admin:6")))).get(0);
        assertEquals(31, daily(person).size()); assertEquals("unmapped", person.get("state"));
        verify(jdbc, times(1)).queryForList(anyString(), any(Object[].class));
    }

    @Test void inconsistentForeignDuplicateOrOutOfWindowSourceRowsFailClosed() {
        List<List<Map<String, Object>>> invalid = List.of(
                List.of(row(7, "2026-11-09", 1, 0, 1)), List.of(row(6, "2026-11-08", 1, 0, 1)),
                List.of(row(6, "2026-11-09", 1, 2, 3)), List.of(row(6, "2026-11-09", 2, 0, 1)),
                List.of(row(6, "2026-11-09", 0, 0, 0)), List.of(row(6, "2026-11-09", -1, 0, 1)),
                List.of(row(6, "2026-11-09", 1, 0, 1), row(6, "2026-11-09", 1, 0, 1)));
        for (List<Map<String, Object>> rows : invalid) {
            source(List.of(6L), rows);
            ResponseEntity<Result<Map<String, Object>>> response = controller.periodUsage(AUTH, query());
            assertEquals(502, response.getStatusCode().value()); assertNull(response.getBody().getData());
        }
    }

    @Test void sqlFailuresReturnOnlySafeConstants() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenThrow(new IllegalStateException("PRIVATE_SQL_PASSWORD"));
        ResponseEntity<Result<Map<String, Object>>> response = controller.periodUsage(AUTH, query());
        assertEquals(502, response.getStatusCode().value()); assertFalse(response.getBody().getMessage().contains("PRIVATE"));
    }

    @Test void concurrentWindowsDoNotSharePersonOrDailyState() throws Exception {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(inv -> {
            Object[] args = (Object[]) inv.getRawArguments()[1];
            if (inv.getArgument(0, String.class).startsWith("SELECT id FROM")) return List.of(Map.of("id", args[0]));
            long id = ((Number) args[0]).longValue(); String day = ((Timestamp) args[1]).toLocalDateTime().toLocalDate().toString();
            return List.of(row(id, day, id, 0, id));
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Map<String, Object>> first = pool.submit(() -> body(controller.periodUsage(AUTH, query("2026-11-09", "2026-11-09", "admin:6"))));
            Future<Map<String, Object>> second = pool.submit(() -> body(controller.periodUsage(AUTH, query("2026-11-10", "2026-11-10", "admin:7"))));
            assertEquals("admin:6", people(first.get(5, TimeUnit.SECONDS)).get(0).get("source_user_id"));
            Map<String, Object> person = people(second.get(5, TimeUnit.SECONDS)).get(0);
            assertEquals("admin:7", person.get("source_user_id")); assertEquals(7L, person.get("observed_count"));
            assertEquals("2026-11-10", daily(person).get(0).get("day"));
        } finally { pool.shutdownNow(); }
    }

    @Test void endpointIsGetOnlyAndRequiresBearerEvenWithoutJwt() throws Exception {
        source(List.of(6L), List.of(row(6, "2026-11-09", 5, 5, 99)));
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/ops/perf-period").params(query())).andExpect(status().isUnauthorized());
        mvc.perform(post("/ops/perf-period").header("Authorization", AUTH).params(query())).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/ops/perf-period").header("Authorization", AUTH).params(query())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.people[0].source_user_id").value("admin:6"))
                .andExpect(jsonPath("$.data.people[0].observed_count").value(5));
    }
}
