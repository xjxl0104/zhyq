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
    @org.springframework.transaction.annotation.Transactional
    public void runYesterday() {
        aggregate(LocalDate.now().minusDays(1));
    }

    @PostMapping("/bi/admin/backfill/daily")
    @PreAuthorize("hasAuthority('bi:admin:manage')")
    @org.springframework.transaction.annotation.Transactional
    public Result<String> backfill(@RequestParam String date) {
        LocalDate d = LocalDate.parse(date, DateTimeFormatter.ISO_DATE);
        aggregate(d);
        return Result.ok("日聚合完成: " + d);
    }

    @org.springframework.transaction.annotation.Transactional
    public void aggregate(LocalDate date) {
        log.info("Daily aggregation start: {}", date);
        long start = System.currentTimeMillis();
        try {
            aggregateAccessMetrics(date);
            aggregateBusinessMetrics(date);
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
            SELECT user_id, COALESCE(MAX(dept_id), 0), ?, 0, SUM(requests), JSON_ARRAYAGG(module), SUM(core_actions)
            FROM (
                SELECT user_id, module, MAX(dept_id) AS dept_id, COUNT(*) AS requests,
                       SUM(CASE WHEN is_core = 1 THEN 1 ELSE 0 END) AS core_actions
                FROM access_log
                WHERE created_at >= ? AND created_at < DATE_ADD(?, INTERVAL 1 DAY)
                GROUP BY user_id, module
            ) per_module
            GROUP BY user_id
            ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), request_count = VALUES(request_count),
                module_set = VALUES(module_set), core_action_count = VALUES(core_action_count), updated_at = NOW()
            """;
        jdbc.update(sql, date, date, date);

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
                                WHERE created_at >= ? AND created_at < DATE_ADD(?, INTERVAL 1 DAY)
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
        jdbc.update(sql, date, date, date);
    }

    private void aggregateBusinessMetrics(LocalDate date) {
        String sql = """
            INSERT INTO user_daily_metrics (user_id, dept_id, stat_date, data_created, flow_started, flow_completed, facts_measured)
            WITH daily_events AS (
                SELECT create_by actor, tenant_id, 1 created, 0 started, 0 completed FROM biz_contract
                    WHERE create_time >= ? AND create_time < ?
                UNION ALL SELECT create_by, tenant_id, 1, 0, 0 FROM fin_bill WHERE create_time >= ? AND create_time < ?
                UNION ALL SELECT create_by, tenant_id, 1, 0, 0 FROM pm_work_order WHERE create_time >= ? AND create_time < ?
                UNION ALL SELECT create_by, tenant_id, 0, 1, 0 FROM wf_instance WHERE create_time >= ? AND create_time < ?
                UNION ALL SELECT i.create_by, i.tenant_id, 0, 0, 1 FROM wf_instance i
                    JOIN wf_task t ON t.instance_id = i.id AND t.seq = i.current_seq AND t.deleted = 0
                    WHERE i.deleted = 0 AND i.status IN (2,3) AND t.status IN (2,3) AND t.act_time >= ? AND t.act_time < ?
            ), facts AS (
                SELECT u.id user_id, COALESCE(MAX(u.dept_id),0) dept_id, SUM(e.created) created,
                       SUM(e.started) started, SUM(e.completed) completed
                FROM daily_events e JOIN sys_user u ON u.username = e.actor AND u.tenant_id = e.tenant_id AND u.deleted = 0
                GROUP BY u.id
            )
            SELECT f.user_id, f.dept_id, ?, f.created, f.started, f.completed, 1 FROM facts f
            ON DUPLICATE KEY UPDATE data_created = VALUES(data_created), flow_started = VALUES(flow_started),
                flow_completed = VALUES(flow_completed), facts_measured = 1
            """;
        LocalDate end = date.plusDays(1);
        // Clear prior derived values for a repeatable backfill, including corrected or removed source records.
        jdbc.update("UPDATE user_daily_metrics SET data_created=0, flow_started=0, flow_completed=0, facts_measured=1 WHERE stat_date=?", date);
        jdbc.update(sql, date, end, date, end, date, end, date, end, date, end, date);
    }

    private void aggregateFeedbackMetrics(LocalDate date) {
        String sql = """
            UPDATE user_daily_metrics udm
            LEFT JOIN (
                SELECT user_id,
                    COUNT(*) AS submitted
                FROM suggestion
                WHERE create_time >= ? AND create_time < DATE_ADD(?, INTERVAL 1 DAY) AND deleted = 0
                GROUP BY user_id
            ) fs ON udm.user_id = fs.user_id
            LEFT JOIN (
                SELECT s.user_id,
                    COUNT(*) AS adopted
                FROM suggestion s
                WHERE s.status IN (4, 5) AND s.resolved_at >= ? AND s.resolved_at < DATE_ADD(?, INTERVAL 1 DAY) AND s.deleted = 0
                GROUP BY s.user_id
            ) fa ON udm.user_id = fa.user_id
            SET udm.feedback_submitted = COALESCE(fs.submitted, 0),
                udm.feedback_adopted = COALESCE(fa.adopted, 0),
                udm.updated_at = NOW()
            WHERE udm.stat_date = ?
            """;
        jdbc.update(sql, date, date, date, date, date);
    }
}
