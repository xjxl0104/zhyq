package com.zhyq.park.stats.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import com.zhyq.park.common.result.Result;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RestController
public class DailyAggregationJob {

    private static final Logger log = LoggerFactory.getLogger(DailyAggregationJob.class);
    private final JdbcTemplate jdbc;

    public DailyAggregationJob(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void runYesterday() {
        aggregate(LocalDate.now().minusDays(1));
    }

    @PostMapping("/bi/admin/backfill/daily")
    @PreAuthorize("hasAuthority('bi:admin:manage')")
    public Result<String> backfill(@RequestParam String date) {
        LocalDate d = LocalDate.parse(date, DateTimeFormatter.ISO_DATE);
        aggregate(d);
        return Result.ok("日聚合完成: " + d);
    }

    public void aggregate(LocalDate date) {
        log.info("Daily aggregation start: {}", date);
        long start = System.currentTimeMillis();
        try {
            aggregateAccessMetrics(date);
            aggregateFeedbackMetrics(date);
            log.info("Daily aggregation done: {} in {}ms", date, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Daily aggregation failed: {}", date, e);
            throw e;
        }
    }

    private void aggregateAccessMetrics(LocalDate date) {
        String sql = """
            INSERT INTO user_daily_metrics
                (user_id, dept_id, stat_date, active_minutes, request_count, module_set, core_action_count)
            SELECT
                a.user_id,
                a.dept_id,
                DATE(a.created_at) AS stat_date,
                0 AS active_minutes,
                COUNT(*) AS request_count,
                JSON_ARRAYAGG(DISTINCT a.module) AS module_set,
                SUM(CASE WHEN a.is_core = 1 THEN 1 ELSE 0 END) AS core_action_count
            FROM access_log a
            WHERE DATE(a.created_at) = ?
            GROUP BY a.user_id, a.dept_id
            ON DUPLICATE KEY UPDATE
                request_count = VALUES(request_count),
                module_set = VALUES(module_set),
                core_action_count = VALUES(core_action_count),
                updated_at = NOW()
            """;
        jdbc.update(sql, date);

        updateActiveMinutes(date);
    }

    private void updateActiveMinutes(LocalDate date) {
        // active_minutes 需要按时间序列计算间隔，用 SQL 窗口函数
        String sql = """
            UPDATE user_daily_metrics udm
            INNER JOIN (
                SELECT user_id,
                    SUM(session_minutes) AS total_minutes
                FROM (
                    SELECT user_id,
                        GREATEST(0, TIMESTAMPDIFF(MINUTE, session_start, session_end)) AS session_minutes
                    FROM (
                        SELECT user_id,
                            MIN(created_at) AS session_start,
                            MAX(created_at) AS session_end
                        FROM (
                            SELECT user_id, created_at,
                                SUM(new_session) OVER (PARTITION BY user_id ORDER BY created_at) AS session_grp
                            FROM (
                                SELECT user_id, created_at,
                                    CASE WHEN TIMESTAMPDIFF(MINUTE,
                                        LAG(created_at) OVER (PARTITION BY user_id ORDER BY created_at),
                                        created_at) > 10
                                        OR LAG(created_at) OVER (PARTITION BY user_id ORDER BY created_at) IS NULL
                                    THEN 1 ELSE 0 END AS new_session
                                FROM access_log
                                WHERE DATE(created_at) = ?
                            ) t1
                        ) t2
                        GROUP BY user_id, session_grp
                    ) sessions
                ) session_dur
                GROUP BY user_id
            ) calc ON udm.user_id = calc.user_id
            SET udm.active_minutes = calc.total_minutes,
                udm.updated_at = NOW()
            WHERE udm.stat_date = ?
            """;
        jdbc.update(sql, date, date);
    }

    private void aggregateFeedbackMetrics(LocalDate date) {
        String sql = """
            UPDATE user_daily_metrics udm
            LEFT JOIN (
                SELECT user_id,
                    COUNT(*) AS submitted
                FROM suggestion
                WHERE DATE(create_time) = ? AND deleted = 0
                GROUP BY user_id
            ) fs ON udm.user_id = fs.user_id
            LEFT JOIN (
                SELECT s.user_id,
                    COUNT(*) AS adopted
                FROM suggestion s
                WHERE s.status IN (4, 5) AND DATE(s.resolved_at) = ? AND s.deleted = 0
                GROUP BY s.user_id
            ) fa ON udm.user_id = fa.user_id
            SET udm.feedback_submitted = COALESCE(fs.submitted, 0),
                udm.feedback_adopted = COALESCE(fa.adopted, 0),
                udm.updated_at = NOW()
            WHERE udm.stat_date = ?
            """;
        jdbc.update(sql, date, date, date);
    }
}
