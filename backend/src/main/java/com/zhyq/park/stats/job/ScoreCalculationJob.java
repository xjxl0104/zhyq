package com.zhyq.park.stats.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class ScoreCalculationJob {

    private static final Logger log = LoggerFactory.getLogger(ScoreCalculationJob.class);
    private final JdbcTemplate jdbc;

    public ScoreCalculationJob(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void calculate(int periodType, LocalDate periodStart) {
        log.info("Score calculation: type={} start={}", periodType, periodStart);

        int workDays = periodType == 1 ? 5 : getWorkDaysInMonth(periodStart);

        String sql = """
            INSERT INTO user_score
                (user_id, dept_id, period_type, period_start,
                 dim_coverage, dim_flow, dim_frequency, dim_data, dim_feedback, total_score)
            SELECT
                pm.user_id,
                pm.dept_id,
                pm.period_type,
                pm.period_start,
                -- 覆盖广度
                LEAST(ROUND(COALESCE(pm.coverage_rate, 0) * 100), 100) AS dim_coverage,
                -- 流程闭环
                CASE WHEN pm.flow_close_rate IS NULL THEN NULL
                     ELSE LEAST(ROUND(pm.flow_close_rate * 100), 100) END AS dim_flow,
                -- 使用频率
                LEAST(ROUND(pm.active_days / ? * 100), 100) AS dim_frequency,
                -- 数据贡献（部门分位数）
                ROUND(
                    (SELECT COUNT(*) FROM user_period_metrics pm2
                     WHERE pm2.dept_id = pm.dept_id
                       AND pm2.period_type = pm.period_type
                       AND pm2.period_start = pm.period_start
                       AND pm2.total_data_created <= pm.total_data_created
                    ) * 100.0 /
                    GREATEST((SELECT COUNT(*) FROM user_period_metrics pm3
                     WHERE pm3.dept_id = pm.dept_id
                       AND pm3.period_type = pm.period_type
                       AND pm3.period_start = pm.period_start), 1)
                ) AS dim_data,
                -- 反馈贡献
                LEAST(ROUND(
                    (pm.total_feedback + pm.total_adopted * 2) * 50.0 /
                    GREATEST((SELECT AVG(total_feedback + total_adopted * 2)
                              FROM user_period_metrics pm4
                              WHERE pm4.dept_id = pm.dept_id
                                AND pm4.period_type = pm.period_type
                                AND pm4.period_start = pm.period_start), 1)
                ), 100) AS dim_feedback,
                -- 综合得分（有效维度均值）
                0 AS total_score
            FROM user_period_metrics pm
            WHERE pm.period_type = ? AND pm.period_start = ?
            ON DUPLICATE KEY UPDATE
                dim_coverage = VALUES(dim_coverage),
                dim_flow = VALUES(dim_flow),
                dim_frequency = VALUES(dim_frequency),
                dim_data = VALUES(dim_data),
                dim_feedback = VALUES(dim_feedback),
                updated_at = NOW()
            """;
        jdbc.update(sql, (double) workDays, periodType, periodStart);

        // 计算 total_score（有效维度均值）
        String updateTotal = """
            UPDATE user_score
            SET total_score = (
                COALESCE(dim_coverage, 0) + COALESCE(dim_flow, 0) + dim_frequency + dim_data + dim_feedback
            ) / (
                CASE WHEN dim_flow IS NULL THEN 4 ELSE 5 END
            ),
            updated_at = NOW()
            WHERE period_type = ? AND period_start = ?
            """;
        jdbc.update(updateTotal, periodType, periodStart);
    }

    private int getWorkDaysInMonth(LocalDate monthStart) {
        int days = monthStart.lengthOfMonth();
        int workDays = 0;
        for (int i = 0; i < days; i++) {
            var dow = monthStart.plusDays(i).getDayOfWeek();
            if (dow.getValue() <= 5) workDays++;
        }
        return workDays;
    }
}
