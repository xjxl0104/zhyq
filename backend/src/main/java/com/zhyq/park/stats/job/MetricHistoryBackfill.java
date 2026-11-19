package com.zhyq.park.stats.job;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@Component
public class MetricHistoryBackfill {
    private final JdbcTemplate jdbc;
    private final DailyAggregationJob daily;
    private final PeriodAggregationJob periods;
    private final TransactionTemplate transaction;

    public MetricHistoryBackfill(JdbcTemplate jdbc, DailyAggregationJob daily, PeriodAggregationJob periods,
                                 PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.daily = daily;
        this.periods = periods;
        this.transaction = new TransactionTemplate(transactions);
    }

    @Scheduled(initialDelay = 20000, fixedDelay = 10000)
    public void runBatch() {
        // Yield the scheduler regularly, and commit each day before taking another.
        long deadline = System.nanoTime() + 2_000_000_000L;
        for (int processed = 0; processed < 3 && System.nanoTime() < deadline; processed++) {
            if (!Boolean.TRUE.equals(transaction.execute(status -> advance()))) return;
        }
    }

    private boolean advance() {
        var cursors = jdbc.query("""
                SELECT next_day, through_day FROM bi_metric_history_backfill
                WHERE id=1 AND finished_at IS NULL FOR UPDATE SKIP LOCKED
                """, (row, i) -> new Cursor(row.getObject(1, LocalDate.class), row.getObject(2, LocalDate.class)));
        if (cursors.isEmpty()) return false;
        Cursor cursor = cursors.get(0);
        LocalDate end = cursor.end() == null ? LocalDate.now().minusDays(1) : cursor.end();
        LocalDate day = cursor.next() == null ? firstDate() : cursor.next();
        if (day == null || day.isAfter(end)) {
            jdbc.update("UPDATE bi_metric_history_backfill SET through_day=?, finished_at=NOW() WHERE id=1", end);
            return false;
        }

        daily.aggregate(day);
        LocalDate weekStart = day.with(DayOfWeek.MONDAY);
        LocalDate monthStart = day.withDayOfMonth(1);
        if (day.getDayOfWeek() == DayOfWeek.SUNDAY || (day.equals(end) && hasPublished(1, weekStart))) {
            periods.aggregateAndScore(weekStart, weekStart.plusDays(6), 1);
        }
        if (day.equals(day.with(TemporalAdjusters.lastDayOfMonth())) || (day.equals(end) && hasPublished(2, monthStart))) {
            periods.aggregateAndScore(monthStart, day.with(TemporalAdjusters.lastDayOfMonth()), 2);
        }
        jdbc.update("UPDATE bi_metric_history_backfill SET next_day=?, through_day=?, finished_at=? WHERE id=1",
                day.plusDays(1), end, day.equals(end) ? java.sql.Timestamp.from(java.time.Instant.now()) : null);
        return !day.equals(end);
    }

    private boolean hasPublished(int type, LocalDate start) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM user_period_metrics WHERE period_type=? AND period_start=?)
                OR EXISTS(SELECT 1 FROM user_score WHERE period_type=? AND period_start=?)
            """, Boolean.class, type, start, type, start));
    }

    private LocalDate firstDate() {
        return jdbc.queryForObject("""
            SELECT DATE(MIN(first_at)) FROM (
                SELECT MIN(stat_date) first_at FROM user_daily_metrics
                UNION ALL SELECT MIN(period_start) FROM user_period_metrics
                UNION ALL SELECT MIN(period_start) FROM user_score
                UNION ALL SELECT MIN(created_at) FROM access_log
                UNION ALL SELECT MIN(create_time) FROM biz_contract
                UNION ALL SELECT MIN(create_time) FROM fin_bill
                UNION ALL SELECT MIN(create_time) FROM pm_work_order
                UNION ALL SELECT MIN(create_time) FROM wf_instance
                UNION ALL SELECT MIN(act_time) FROM wf_task
            ) history
            """, LocalDate.class);
    }

    private record Cursor(LocalDate next, LocalDate end) {}
}
