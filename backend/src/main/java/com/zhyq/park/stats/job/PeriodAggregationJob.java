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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

@Component
@RestController
public class PeriodAggregationJob {

    private static final Logger log = LoggerFactory.getLogger(PeriodAggregationJob.class);
    private final JdbcTemplate jdbc;
    private final ScoreCalculationJob scoreJob;

    public PeriodAggregationJob(JdbcTemplate jdbc, ScoreCalculationJob scoreJob) {
        this.jdbc = jdbc;
        this.scoreJob = scoreJob;
    }

    @Scheduled(cron = "0 0 3 ? * MON")
    public void runWeekly() {
        LocalDate weekStart = LocalDate.now().minusWeeks(1).with(DayOfWeek.MONDAY);
        aggregatePeriod(weekStart, weekStart.plusDays(6), 1);
        scoreJob.calculate(1, weekStart);
    }

    @Scheduled(cron = "0 30 3 1 * ?")
    public void runMonthly() {
        LocalDate monthStart = LocalDate.now().minusMonths(1).with(TemporalAdjusters.firstDayOfMonth());
        LocalDate monthEnd = monthStart.with(TemporalAdjusters.lastDayOfMonth());
        aggregatePeriod(monthStart, monthEnd, 2);
        scoreJob.calculate(2, monthStart);
    }

    @PostMapping("/bi/admin/backfill/period")
    @PreAuthorize("hasAuthority('bi:admin:manage')")
    public Result<String> backfill(@RequestParam int periodType, @RequestParam String startDate) {
        LocalDate start = LocalDate.parse(startDate, DateTimeFormatter.ISO_DATE);
        LocalDate end = periodType == 1 ? start.plusDays(6)
                : start.with(TemporalAdjusters.lastDayOfMonth());
        aggregatePeriod(start, end, periodType);
        scoreJob.calculate(periodType, start);
        return Result.ok("周期聚合+评分完成: type=" + periodType + " start=" + start);
    }

    private void aggregatePeriod(LocalDate start, LocalDate end, int periodType) {
        log.info("Period aggregation: type={} {} ~ {}", periodType, start, end);

        int totalModules = getTotalModuleCount();

        String sql = """
            INSERT INTO user_period_metrics
                (user_id, dept_id, period_type, period_start, active_days, coverage_rate,
                 flow_close_rate, total_core_actions, total_data_created, total_feedback, total_adopted)
            SELECT
                user_id,
                dept_id,
                ? AS period_type,
                ? AS period_start,
                COUNT(CASE WHEN request_count > 0 THEN 1 END) AS active_days,
                CASE WHEN ? > 0 THEN
                    (SELECT COUNT(DISTINCT jt.module)
                     FROM user_daily_metrics udm2
                     CROSS JOIN JSON_TABLE(udm2.module_set, '$[*]' COLUMNS(module VARCHAR(64) PATH '$')) jt
                     WHERE udm2.user_id = d.user_id AND udm2.stat_date BETWEEN ? AND ?
                       AND jt.module IS NOT NULL
                    ) / ?
                ELSE NULL END AS coverage_rate,
                CASE WHEN SUM(flow_started) > 0
                    THEN SUM(flow_completed) / SUM(flow_started)
                    ELSE NULL END AS flow_close_rate,
                SUM(core_action_count) AS total_core_actions,
                SUM(data_created) AS total_data_created,
                SUM(feedback_submitted) AS total_feedback,
                SUM(feedback_adopted) AS total_adopted
            FROM user_daily_metrics d
            WHERE stat_date BETWEEN ? AND ?
            GROUP BY user_id, dept_id
            ON DUPLICATE KEY UPDATE
                active_days = VALUES(active_days),
                coverage_rate = VALUES(coverage_rate),
                flow_close_rate = VALUES(flow_close_rate),
                total_core_actions = VALUES(total_core_actions),
                total_data_created = VALUES(total_data_created),
                total_feedback = VALUES(total_feedback),
                total_adopted = VALUES(total_adopted),
                updated_at = NOW()
            """;
        jdbc.update(sql, periodType, start, totalModules, start, end, (double) totalModules, start, end);
    }

    private int getTotalModuleCount() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT module) FROM route_module_mapping WHERE enabled = 1", Integer.class);
        return count != null ? count : 1;
    }
}
