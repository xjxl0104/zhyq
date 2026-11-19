package com.zhyq.park.stats.job;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "DIPARK_BI_TEST_URL", matches = ".+")
class MetricHistoryBackfillMysqlTest {
    @Test
    void rebuildsHistoricalDailyWeeklyAndMonthlyScoresAndResumesAfterRollback() {
        var source = new DriverManagerDataSource(System.getenv("DIPARK_BI_TEST_URL"),
                System.getenv().getOrDefault("DIPARK_BI_TEST_USER", "root"),
                System.getenv().getOrDefault("DIPARK_BI_TEST_PASSWORD", ""));
        var jdbc = new JdbcTemplate(source);
        assertTrue(jdbc.queryForObject("SELECT DATABASE()", String.class).startsWith("dipark_bi_test_"),
                "Use a dedicated migrated scratch schema named dipark_bi_test_*");
        var last = LocalDate.of(2025, 8, 31); // Sunday and end of month
        String actor = "bi_" + UUID.randomUUID().toString().substring(0,8);
        String reader = actor + "_zero";
        long dept = Math.abs((long) actor.hashCode());
        jdbc.update("INSERT INTO sys_user(username,nickname,dept_id) VALUES(?,?,?),(?,?,?)", actor, actor, dept, reader, reader, dept);
        long authorId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, actor);
        long readerId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, reader);
        jdbc.update("INSERT INTO biz_contract(code,tenant_ref_id,project_id,create_by,create_time) VALUES(?,1,1,?,?)", actor, actor, last);
        jdbc.update("INSERT INTO wf_instance(definition_id,biz_type,biz_id,create_by,create_time,status) VALUES(1,?,1,?,?,2)", actor, actor, last);
        long instance = jdbc.queryForObject("SELECT id FROM wf_instance WHERE biz_type=?", Long.class, actor);
        jdbc.update("INSERT INTO wf_task(instance_id,node_id,seq,status,act_time) VALUES(?,1,1,2,?)", instance, last);
        jdbc.update("INSERT INTO access_log(user_id,dept_id,route,method,module,status_code,duration_ms,created_at) VALUES(?,?,'/read','GET','crm',200,3,?)", readerId, dept, last);
        jdbc.update("INSERT INTO user_score(user_id,dept_id,period_type,period_start,dim_data) VALUES(?,?,1,?,100)", readerId, dept, last.minusDays(6));
        jdbc.update("UPDATE bi_metric_history_backfill SET next_day=NULL, through_day=?, finished_at=NULL WHERE id=1", last);

        var daily = new DailyAggregationJob(jdbc);
        var periods = new PeriodAggregationJob(jdbc, new ScoreCalculationJob(jdbc));
        var transactions = new DataSourceTransactionManager(source);
        var onceUnavailable = spy(periods);
        doThrow(new IllegalStateException("fixture")).when(onceUnavailable).aggregateAndScore(last.minusDays(6), last, 1);
        var interrupted = new MetricHistoryBackfill(jdbc, daily, onceUnavailable, transactions);
        // The earliest pre-existing weekly score predates this Sunday's source facts.
        assertThrows(IllegalStateException.class, () -> {
            for (int i = 0; i < 64; i++) interrupted.runBatch();
        });
        assertEquals(last, jdbc.queryForObject("SELECT next_day FROM bi_metric_history_backfill WHERE id=1", LocalDate.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_daily_metrics WHERE user_id=?", Integer.class, authorId));

        var resumed = new MetricHistoryBackfill(jdbc, daily, periods, transactions);
        resumed.runBatch();
        resumed.runBatch();
        assertEquals(1, jdbc.queryForObject("SELECT finished_at IS NOT NULL FROM bi_metric_history_backfill WHERE id=1", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_daily_metrics WHERE user_id=? AND stat_date=? AND facts_measured=1 AND data_created=1 AND flow_started=1 AND flow_completed=1", Integer.class, authorId,last));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM user_score WHERE user_id=? AND dim_data=100 AND dim_flow=100", Integer.class, authorId));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM user_score WHERE user_id=? AND dim_data=0 AND dim_flow IS NULL", Integer.class, readerId));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM user_period_metrics WHERE user_id=? AND facts_measured=1 AND total_data_created=1 AND flow_close_rate=1", Integer.class, authorId));
    }
}
