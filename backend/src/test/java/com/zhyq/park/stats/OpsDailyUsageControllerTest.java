package com.zhyq.park.stats;

import com.zhyq.park.common.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 应用日报日汇总:固定令牌鉴权 + 只回每人次数与首末时刻。 */
class OpsDailyUsageControllerTest {

    private static final String TOKEN = "t".repeat(OpsDailyUsageController.MIN_TOKEN_LENGTH);

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @Test
    void 未配置或令牌过短_接口不可用且不查库() {
        assertEquals(503, new OpsDailyUsageController(jdbc, "").dailyUsage("Bearer x", "2026-11-05").getStatusCode().value());
        assertEquals(503, new OpsDailyUsageController(jdbc, "short").dailyUsage("Bearer short", "2026-11-05").getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @Test
    void 令牌缺失或不对_401且不查库() {
        OpsDailyUsageController c = new OpsDailyUsageController(jdbc, TOKEN);
        assertEquals(401, c.dailyUsage(null, "2026-11-05").getStatusCode().value());
        assertEquals(401, c.dailyUsage(TOKEN, "2026-11-05").getStatusCode().value()); // 缺 Bearer 前缀
        assertEquals(401, c.dailyUsage("Bearer " + TOKEN + "x", "2026-11-05").getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @Test
    void 日期格式不对_400() {
        OpsDailyUsageController c = new OpsDailyUsageController(jdbc, TOKEN);
        assertEquals(400, c.dailyUsage("Bearer " + TOKEN, "2026/11/05").getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @Test
    void 按北京时间整天查询_只回归人字段与次数时刻() {
        Map<String, Object> mapped = new HashMap<>();
        mapped.put("portal_uid", "2092221766364692481");
        mapped.put("name", "丁超");
        mapped.put("ops", 24L);
        mapped.put("first_at", Timestamp.valueOf("2026-11-05 09:03:41"));
        mapped.put("last_at", Timestamp.valueOf("2026-11-05 18:20:05"));
        Map<String, Object> unmapped = new HashMap<>();
        unmapped.put("portal_uid", " ");
        unmapped.put("name", "王钰淞");
        unmapped.put("ops", null); // 只看不改: SUM 结果为 0/NULL
        unmapped.put("first_at", Timestamp.valueOf("2026-11-05 10:00:00"));
        unmapped.put("last_at", Timestamp.valueOf("2026-11-05 10:05:00"));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(mapped, unmapped));

        ResponseEntity<Result<Map<String, Object>>> resp =
                new OpsDailyUsageController(jdbc, TOKEN).dailyUsage("Bearer " + TOKEN, "2026-11-05");

        assertEquals(200, resp.getStatusCode().value());
        verify(jdbc).queryForList(eq(OpsDailyUsageController.SQL),
                eq(Timestamp.valueOf("2026-11-05 00:00:00")), eq(Timestamp.valueOf("2026-11-06 00:00:00")));
        Map<String, Object> data = resp.getBody().getData();
        assertEquals("2026-11-05", data.get("date"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> people = (List<Map<String, Object>>) data.get("people");
        assertEquals(Map.of("portalUid", "2092221766364692481", "name", "丁超", "ops", 24L,
                "first", "09:03", "last", "18:20"), people.get(0));
        assertNull(people.get(1).get("portalUid"));
        assertEquals(0L, people.get(1).get("ops"));
        assertEquals(List.of("portalUid", "name", "ops", "first", "last"), List.copyOf(people.get(0).keySet()));
    }

    @Test
    void 只把成功的写请求算操作() {
        String sql = OpsDailyUsageController.SQL;
        assertTrue(sql.contains("a.method IN ('POST', 'PUT', 'PATCH', 'DELETE') AND a.status_code < 400"));
        assertFalse(sql.contains("route"), "不回路由等操作明细");
    }
}
