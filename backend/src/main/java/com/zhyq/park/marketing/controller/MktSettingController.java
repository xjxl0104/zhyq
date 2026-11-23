package com.zhyq.park.marketing.controller;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktWithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

/** 规则参数(biz_setting module=marketing),只允许仍在使用的白名单 key。 */
@Tag(name = "全民营销-规则参数")
@RestController
@RequestMapping("/crm/marketing/setting")
@RequiredArgsConstructor
public class MktSettingController {

    private static final String MODULE = "marketing";
    private static final String ARRIVAL_TIME = "withdraw_arrival_time";
    private static final String ARRIVAL_REMARK = "提现实际处理与到账时效说明，请按可履行的运营安排填写（最多200字）";
    private static final String WINDOW_START_KEY = "withdraw_window_start_day";
    private static final String WINDOW_START_REMARK = "提现申请开放起始日（0 表示全月随时可提交；1–28 表示每月固定开放起始日）";
    private static final String WINDOW_END_KEY = "withdraw_window_end_day";
    private static final String WINDOW_END_REMARK = "提现申请开放截止日（1–31，起始日为 0 时忽略）";
    private static final String AUTO_WITHDRAW_KEY = "auto_withdraw_day";
    private static final String AUTO_WITHDRAW_REMARK = "每月自动提现日：当天 10:00 自动将可提现余额发起提现申请（0 关闭，1–28）";
    private static final String PLATFORM_RETAIN_KEY = "platform_retain_rate";
    private static final String PLATFORM_RETAIN_REMARK = "平台保底留存比例（如 0.05 表示固定留存总佣金的 5%，个人可分配上限 95%）";

    /** 后端 0 次读取的幽灵历史参数:查询时不展示以防误导管理员。 */
    public static final Set<String> RETIRED_GHOST_KEYS = Set.of(
            "attribution_rule", "protect_days", "dup_customer_fields", "internal_commission",
            "monthly_cap", "default_sign_mode", "lease_renew_ratio", "invite_grace_days");

    private static final Set<String> ALLOWED = Set.of(
            "ladder_depth", "override_enabled", "freeze_days",
            "daily_referral_cap", "clawback_days",
            "old_channel_exclusive", "tax_mode", "tax_rate",
            "lock_cooldown_days", "lease_clawback_days",
            WINDOW_START_KEY, WINDOW_END_KEY, AUTO_WITHDRAW_KEY, PLATFORM_RETAIN_KEY, ARRIVAL_TIME);

    private final JdbcTemplate jdbc;
    private final MktAuditService auditService;

    @Operation(summary = "全部参数")
    @PreAuthorize("hasAuthority('crm:marketing:setting:query')")
    @GetMapping
    public Result<List<Map<String, Object>>> all() {
        List<Map<String, Object>> rows = new ArrayList<>(jdbc.queryForList(
                "SELECT skey, svalue, remark FROM biz_setting WHERE module = ? AND deleted = 0 "
                        + "AND skey NOT IN ('min_withdraw', 'withdraw_free_times', 'prelock_days', 'lock_days', 'lock_extend_days', "
                        + "'withdraw_approver_username', 'withdraw_approver_promoter_id', "
                        + "'attribution_rule', 'protect_days', 'dup_customer_fields', 'internal_commission', "
                        + "'monthly_cap', 'default_sign_mode', 'lease_renew_ratio', 'invite_grace_days') ORDER BY id", MODULE));
        rows.removeIf(row -> row != null && RETIRED_GHOST_KEYS.contains(String.valueOf(row.get("skey"))));
        // The defaults are visible without writing during GET; saving them creates any missing setting.
        ensureVirtualDefault(rows, ARRIVAL_TIME, MktWithdrawalService.DEFAULT_ARRIVAL_TIME, ARRIVAL_REMARK);
        ensureVirtualDefault(rows, WINDOW_START_KEY, "0", WINDOW_START_REMARK);
        ensureVirtualDefault(rows, WINDOW_END_KEY, "31", WINDOW_END_REMARK);
        ensureVirtualDefault(rows, AUTO_WITHDRAW_KEY, "10", AUTO_WITHDRAW_REMARK);
        ensureVirtualDefault(rows, PLATFORM_RETAIN_KEY, "0.05", PLATFORM_RETAIN_REMARK);
        return Result.ok(rows);
    }

    private static void ensureVirtualDefault(List<Map<String, Object>> rows, String key, String defaultValue, String remark) {
        if (rows.stream().noneMatch(row -> key.equals(row.get("skey")))) {
            rows.add(Map.of("skey", key, "svalue", defaultValue, "remark", remark));
        }
    }

    @Operation(summary = "批量保存(只改白名单 key)")
    @PreAuthorize("hasAuthority('crm:marketing:setting:config')")
    @PutMapping
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Result<Void> update(@RequestBody List<Map<String, String>> items) {
        for (Map<String, String> it : items) {
            String key = it.get("skey");
            String value = it.get("svalue");
            if (Set.of("prelock_days", "lock_days", "lock_extend_days").contains(key == null ? "" : key)) {
                throw new BizException("客户统一锁定三个月，签约成交后长期有效；旧锁定参数已停用，请刷新页面");
            }
            if ("min_withdraw".equals(key) || "withdraw_free_times".equals(key)) {
                throw new BizException("提现按分申请，不限每日次数且不收手续费；旧参数已停用，请刷新页面");
            }
            if (key == null || !ALLOWED.contains(key)) {
                throw new BizException("不允许修改的参数: " + key);
            }
            if ("ladder_depth".equals(key)) {
                throw new BizException("岗位数请在「岗位与份额」页切换");
            }
            if (ARRIVAL_TIME.equals(key)) {
                saveArrivalTime(value);
                continue;
            }
            if (WINDOW_START_KEY.equals(key)) {
                int day = parseBoundedInt(value, 0, 28, "提现申请开放起始日请填写 0–28（0 表示全月随时可提交）");
                upsertSetting(WINDOW_START_KEY, String.valueOf(day), WINDOW_START_REMARK, "提现窗口起始日设置已变化，请刷新页面后重试");
                continue;
            }
            if (WINDOW_END_KEY.equals(key)) {
                int day = parseBoundedInt(value, 1, 31, "提现申请开放截止日请填写 1–31");
                upsertSetting(WINDOW_END_KEY, String.valueOf(day), WINDOW_END_REMARK, "提现窗口截止日设置已变化，请刷新页面后重试");
                continue;
            }
            if (AUTO_WITHDRAW_KEY.equals(key)) {
                int day = parseBoundedInt(value, 0, 28, "自动提现日请填写 0–28（0 表示关闭自动提现）");
                upsertSetting(AUTO_WITHDRAW_KEY, String.valueOf(day), AUTO_WITHDRAW_REMARK, "自动提现日设置已变化，请刷新页面后重试");
                continue;
            }
            if (PLATFORM_RETAIN_KEY.equals(key)) {
                String normalized = parseRetainRate(value);
                upsertSetting(PLATFORM_RETAIN_KEY, normalized, PLATFORM_RETAIN_REMARK, "平台保底留存比例设置已变化，请刷新页面后重试");
                continue;
            }
            String before = jdbc.query("SELECT svalue FROM biz_setting WHERE module = ? AND skey = ? AND deleted = 0 LIMIT 1",
                    rs -> rs.next() ? rs.getString(1) : null, MODULE, key);
            if (value != null && !value.equals(before)) {
                jdbc.update("UPDATE biz_setting SET svalue = ?, update_time = NOW() WHERE module = ? AND skey = ? AND deleted = 0",
                        value, MODULE, key);
                auditService.log("setting.change", "setting", null, key, before, value);
            }
        }
        return Result.ok();
    }

    private static int parseBoundedInt(String raw, int min, int max, String errorMsg) {
        if (raw == null || !raw.trim().matches("\\d{1,2}")) {
            throw new BizException(errorMsg);
        }
        int parsed = Integer.parseInt(raw.trim());
        if (parsed < min || parsed > max) {
            throw new BizException(errorMsg);
        }
        return parsed;
    }

    private static String parseRetainRate(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new BizException("平台保底留存比例须填写 0 到 0.50 之间的数值");
        }
        BigDecimal rate;
        try {
            rate = new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            throw new BizException("平台保底留存比例须填写 0 到 0.50 之间的数值");
        }
        if (rate.signum() < 0 || rate.compareTo(new BigDecimal("0.50")) > 0 || rate.stripTrailingZeros().scale() > 4) {
            throw new BizException("平台保底留存比例须在 0 到 0.50 之间（最多 4 位小数）");
        }
        return raw.trim();
    }

    private void saveArrivalTime(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty() || value.codePointCount(0, value.length()) > 200) {
            throw new BizException("请填写实际提现到账时效说明，不能为空且最多200字");
        }
        upsertSetting(ARRIVAL_TIME, value, ARRIVAL_REMARK, "提现到账时效设置已变化，请刷新页面后重试");
    }

    private void upsertSetting(String key, String value, String remark, String conflictMsg) {
        // biz_setting has a non-unique index. Lock the key range and guard insertion;
        // ON DUPLICATE KEY would not prevent a second row for a previously absent key.
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT svalue FROM biz_setting WHERE module = ? AND skey = ? AND deleted = 0 FOR UPDATE", MODULE, key);
        String before = existing.isEmpty() ? null : (String) existing.get(0).get("svalue");
        if (value.equals(before)) return;
        int changed;
        if (existing.isEmpty()) {
            String operator = MktAuditService.currentOperator();
            changed = jdbc.update("INSERT INTO biz_setting (module, skey, svalue, remark, create_by, create_time, update_by, update_time) "
                            + "SELECT ?, ?, ?, ?, ?, NOW(), ?, NOW() WHERE NOT EXISTS "
                            + "(SELECT 1 FROM biz_setting WHERE module = ? AND skey = ? AND deleted = 0)",
                    MODULE, key, value, remark, operator, operator, MODULE, key);
        } else {
            changed = jdbc.update("UPDATE biz_setting SET svalue = ?, update_time = NOW() WHERE module = ? AND skey = ? AND deleted = 0",
                    value, MODULE, key);
        }
        if (changed < 1) throw new BizException(conflictMsg);
        auditService.log("setting.change", "setting", null, key, before, value);
    }
}
