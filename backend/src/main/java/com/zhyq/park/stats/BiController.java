package com.zhyq.park.stats;

import com.zhyq.park.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "BI 使用度统计")
@RestController
@RequestMapping("/bi")
@RequiredArgsConstructor
public class BiController {

    private final JdbcTemplate jdbc;

    @Operation(summary = "北极星指标")
    @GetMapping("/admin/north-star")
    @PreAuthorize("hasAuthority('bi:admin:view')")
    public Result<Map<String, Object>> northStar() {
        String sql = """
            SELECT
                (SELECT COUNT(DISTINCT user_id) FROM user_period_metrics
                 WHERE period_type = 1 AND period_start = (
                     SELECT MAX(period_start) FROM user_period_metrics WHERE period_type = 1)
                 AND active_days > 0 AND total_core_actions > 0) AS current_week,
                (SELECT COUNT(DISTINCT user_id) FROM user_period_metrics
                 WHERE period_type = 1 AND period_start = (
                     SELECT MAX(period_start) FROM user_period_metrics WHERE period_type = 1) - INTERVAL 7 DAY
                 AND active_days > 0 AND total_core_actions > 0) AS prev_week
            """;
        Map<String, Object> row = jdbc.queryForMap(sql);
        return Result.ok(row);
    }

    @Operation(summary = "部门雷达评分列表")
    @GetMapping("/admin/dept-radar")
    @PreAuthorize("hasAuthority('bi:admin:view')")
    public Result<List<Map<String, Object>>> deptRadar(@RequestParam(required = false) String periodStart) {
        String sql = """
            SELECT d.id AS dept_id, d.name AS dept_name,
                   ROUND(AVG(s.dim_coverage)) AS avg_coverage,
                   ROUND(AVG(s.dim_flow)) AS avg_flow,
                   ROUND(AVG(s.dim_frequency)) AS avg_frequency,
                   ROUND(AVG(s.dim_data)) AS avg_data,
                   ROUND(AVG(s.dim_feedback)) AS avg_feedback,
                   ROUND(AVG(s.total_score)) AS avg_score
            FROM user_score s
            JOIN sys_dept d ON s.dept_id = d.id
            WHERE s.period_type = 2
              AND s.period_start = COALESCE(?, (SELECT MAX(period_start) FROM user_score WHERE period_type = 2))
            GROUP BY d.id, d.name
            ORDER BY avg_score DESC
            """;
        return Result.ok(jdbc.queryForList(sql, periodStart));
    }

    @Operation(summary = "部门下钻到个人")
    @GetMapping("/admin/dept-radar/{deptId}")
    @PreAuthorize("hasAuthority('bi:admin:view')")
    public Result<List<Map<String, Object>>> deptDetail(@PathVariable Long deptId,
                                                        @RequestParam(required = false) String periodStart) {
        String sql = """
            SELECT s.user_id, u.nickname,
                   s.dim_coverage, s.dim_flow, s.dim_frequency, s.dim_data, s.dim_feedback, s.total_score
            FROM user_score s
            JOIN sys_user u ON s.user_id = u.id
            WHERE s.dept_id = ? AND s.period_type = 2
              AND s.period_start = COALESCE(?, (SELECT MAX(period_start) FROM user_score WHERE period_type = 2))
            ORDER BY s.total_score DESC
            """;
        return Result.ok(jdbc.queryForList(sql, deptId, periodStart));
    }

    @Operation(summary = "趋势数据")
    @GetMapping("/admin/trend")
    @PreAuthorize("hasAuthority('bi:admin:view')")
    public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "1") int periodType) {
        String sql = """
            SELECT period_start,
                   COUNT(DISTINCT user_id) AS active_users,
                   COUNT(DISTINCT CASE WHEN total_core_actions > 0 THEN user_id END) AS core_users
            FROM user_period_metrics
            WHERE period_type = ?
            GROUP BY period_start
            ORDER BY period_start DESC
            LIMIT 12
            """;
        return Result.ok(jdbc.queryForList(sql, periodType));
    }

    @Operation(summary = "模块使用率")
    @GetMapping("/product/module-usage")
    @PreAuthorize("hasAuthority('bi:product:view')")
    public Result<List<Map<String, Object>>> moduleUsage() {
        String sql = """
            SELECT module, COUNT(*) AS request_count, COUNT(DISTINCT user_id) AS user_count
            FROM access_log
            WHERE module IS NOT NULL AND created_at > DATE_SUB(NOW(), INTERVAL 30 DAY)
            GROUP BY module
            ORDER BY request_count DESC
            """;
        return Result.ok(jdbc.queryForList(sql));
    }

    @Operation(summary = "流程卡点分析")
    @GetMapping("/product/flow-analysis")
    @PreAuthorize("hasAuthority('bi:product:view')")
    public Result<Map<String, Object>> flowAnalysis() {
        // 表无 flow_started/flow_completed 列,用核心操作数与闭环率折算:
        // 发起数 = SUM(total_core_actions);完成数 = SUM(ROUND(total_core_actions * flow_close_rate))
        String started = """
            SELECT
              COALESCE(SUM(total_core_actions), 0) AS total_started,
              COALESCE(SUM(ROUND(total_core_actions * COALESCE(flow_close_rate, 0))), 0) AS total_completed
            FROM user_period_metrics
            WHERE period_type = 2
              AND period_start = (SELECT MAX(period_start) FROM user_period_metrics WHERE period_type = 2)
            """;
        return Result.ok(jdbc.queryForMap(started));
    }

    @Operation(summary = "反馈看板")
    @GetMapping("/product/feedback-board")
    @PreAuthorize("hasAuthority('bi:product:view')")
    public Result<List<Map<String, Object>>> feedbackBoard() {
        String sql = """
            SELECT module, status, COUNT(*) AS cnt
            FROM suggestion
            WHERE deleted = 0
            GROUP BY module, status
            ORDER BY module, status
            """;
        return Result.ok(jdbc.queryForList(sql));
    }
}
