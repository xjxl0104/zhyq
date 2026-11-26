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
    private static final String SEA_SLA_HOURS_KEY = "sea_sla_hours";
    private static final String SEA_SLA_HOURS_REMARK = "公海认领后首触达保活时限（小时，超时未填核实纪要自动弹回公海）";
    private static final String SEA_WH_DAILY_CAP_KEY = "sea_wh_daily_cap";
    private static final String SEA_WH_DAILY_CAP_REMARK = "加盟云仓每日最多认领公海热单户数";
    private static final String SEA_WH_ACTIVE_CAP_KEY = "sea_wh_active_cap";
    private static final String SEA_WH_ACTIVE_CAP_REMARK = "加盟云仓在途未签约公海客户上限户数";
    private static final String SEA_ADMIN_ACTIVE_CAP_KEY = "sea_admin_active_cap";
    private static final String SEA_ADMIN_ACTIVE_CAP_REMARK = "内部招商专员同时在跟的公海客户上限（户），防止囤积客户";
    private static final String SEA_REGION_LOCK_KEY = "sea_region_lock_province";
    private static final String SEA_REGION_LOCK_REMARK = "公海外部付费API定向省钱锁省份（非本省主体自动跳过付费工商查询，填 ALL 不限省）";
    private static final String SEA_CHECKIN_METERS_KEY = "sea_checkin_max_meters";
    private static final String SEA_CHECKIN_METERS_REMARK = "公海地推现场GPS打卡最大允许偏差米数（默认500米内有效）";
    private static final String SEA_VISIT_DAYS_KEY = "sea_visit_protect_days";
    private static final String SEA_VISIT_DAYS_REMARK = "通过500米现场拍照打卡后授予的深度地推保护天数（默认15天）";
    private static final String SEA_FOLLOWUP_MAX_DAYS_KEY = "sea_followup_max_days";
    private static final String SEA_FOLLOWUP_MAX_DAYS_REMARK = "公海客户完成首触达或15天保护期满后最长跟进天数（超期未签约且无新跟进自动回流公海，默认30天）";
    /** 外部接口密钥已统一迁到「外部数据连接器中心」，历史 ext_* 行不在规则参数页展示也不允许保存。 */
    public static final String LEGACY_CONNECTOR_KEY_PREFIX = "ext_";

    /** 后端 0 次读取的幽灵历史参数:查询时不展示以防误导管理员。 */
    public static final Set<String> RETIRED_GHOST_KEYS = Set.of(
            "attribution_rule", "protect_days", "dup_customer_fields", "internal_commission",
            "monthly_cap", "default_sign_mode", "lease_renew_ratio", "invite_grace_days");

    private static final Set<String> ALLOWED = Set.of(
            "ladder_depth", "override_enabled", "freeze_days",
            "daily_referral_cap", "clawback_days",
            "old_channel_exclusive", "tax_mode", "tax_rate",
            "lock_cooldown_days", "lease_clawback_days",
            WINDOW_START_KEY, WINDOW_END_KEY, AUTO_WITHDRAW_KEY, ARRIVAL_TIME,
            SEA_SLA_HOURS_KEY, SEA_WH_DAILY_CAP_KEY, SEA_WH_ACTIVE_CAP_KEY, SEA_ADMIN_ACTIVE_CAP_KEY,
            SEA_REGION_LOCK_KEY, SEA_CHECKIN_METERS_KEY, SEA_VISIT_DAYS_KEY, SEA_FOLLOWUP_MAX_DAYS_KEY);

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
        rows.removeIf(row -> row != null && String.valueOf(row.get("skey")).startsWith(LEGACY_CONNECTOR_KEY_PREFIX));
        // The defaults are visible without writing during GET; saving them creates any missing setting.
        ensureVirtualDefault(rows, ARRIVAL_TIME, MktWithdrawalService.DEFAULT_ARRIVAL_TIME, ARRIVAL_REMARK);
        ensureVirtualDefault(rows, WINDOW_START_KEY, "0", WINDOW_START_REMARK);
        ensureVirtualDefault(rows, WINDOW_END_KEY, "31", WINDOW_END_REMARK);
        ensureVirtualDefault(rows, AUTO_WITHDRAW_KEY, "10", AUTO_WITHDRAW_REMARK);
        ensureVirtualDefault(rows, SEA_SLA_HOURS_KEY, "72", SEA_SLA_HOURS_REMARK);
        ensureVirtualDefault(rows, SEA_WH_DAILY_CAP_KEY, "5", SEA_WH_DAILY_CAP_REMARK);
        ensureVirtualDefault(rows, SEA_WH_ACTIVE_CAP_KEY, "10", SEA_WH_ACTIVE_CAP_REMARK);
        ensureVirtualDefault(rows, SEA_ADMIN_ACTIVE_CAP_KEY, "30", SEA_ADMIN_ACTIVE_CAP_REMARK);
        ensureVirtualDefault(rows, SEA_REGION_LOCK_KEY, "广东省", SEA_REGION_LOCK_REMARK);
        ensureVirtualDefault(rows, SEA_CHECKIN_METERS_KEY, "500", SEA_CHECKIN_METERS_REMARK);
        ensureVirtualDefault(rows, SEA_VISIT_DAYS_KEY, "15", SEA_VISIT_DAYS_REMARK);
        ensureVirtualDefault(rows, SEA_FOLLOWUP_MAX_DAYS_KEY, "30", SEA_FOLLOWUP_MAX_DAYS_REMARK);
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
            if (key != null && key.startsWith(LEGACY_CONNECTOR_KEY_PREFIX)) {
                throw new BizException("外部接口密钥已迁至「规则与字典 › 外部数据连接器中心」统一加密保存，请刷新页面");
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
            if (SEA_SLA_HOURS_KEY.equals(key)) {
                int h = parseBoundedInt(value, 1, 720, "公海首触达保活时限请填写 1–720 小时");
                upsertSetting(SEA_SLA_HOURS_KEY, String.valueOf(h), SEA_SLA_HOURS_REMARK, "公海保活时限设置已变化，请刷新页面后重试");
                continue;
            }
            if (SEA_WH_DAILY_CAP_KEY.equals(key)) {
                int cap = parseBoundedInt(value, 1, 50, "云仓每日认领上限请填写 1–50 户");
                upsertSetting(SEA_WH_DAILY_CAP_KEY, String.valueOf(cap), SEA_WH_DAILY_CAP_REMARK, "云仓每日认领上限已变化，请刷新页面后重试");
                continue;
            }
            if (SEA_WH_ACTIVE_CAP_KEY.equals(key)) {
                int cap = parseBoundedInt(value, 1, 200, "云仓在途公海客户上限请填写 1–200 户");
                upsertSetting(SEA_WH_ACTIVE_CAP_KEY, String.valueOf(cap), SEA_WH_ACTIVE_CAP_REMARK, "云仓在途上限已变化，请刷新页面后重试");
                continue;
            }
            if (SEA_ADMIN_ACTIVE_CAP_KEY.equals(key)) {
                int cap = parseBoundedInt(value, 1, 500, "专员在跟公海客户上限请填写 1–500 户");
                upsertSetting(SEA_ADMIN_ACTIVE_CAP_KEY, String.valueOf(cap), SEA_ADMIN_ACTIVE_CAP_REMARK, "专员在跟上限已变化，请刷新页面后重试");
                continue;
            }
            if (SEA_CHECKIN_METERS_KEY.equals(key)) {
                int meters = parseBoundedInt(value, 50, 5000, "现场 GPS 打卡最大偏差米数请填写 50–5000 米");
                upsertSetting(SEA_CHECKIN_METERS_KEY, String.valueOf(meters), SEA_CHECKIN_METERS_REMARK, "公海打卡偏差米数已变化，请刷新页面后重试");
                continue;
            }
            if (SEA_VISIT_DAYS_KEY.equals(key)) {
                int days = parseBoundedInt(value, 1, 90, "地推打卡保护天数请填写 1–90 天");
                upsertSetting(SEA_VISIT_DAYS_KEY, String.valueOf(days), SEA_VISIT_DAYS_REMARK, "地推打卡保护天数已变化，请刷新页面后重试");
                continue;
            }
            if (SEA_FOLLOWUP_MAX_DAYS_KEY.equals(key)) {
                int days = parseBoundedInt(value, 3, 365, "最长跟进回收天数请填写 3–365 天");
                upsertSetting(SEA_FOLLOWUP_MAX_DAYS_KEY, String.valueOf(days), SEA_FOLLOWUP_MAX_DAYS_REMARK, "最长跟进回收天数已变化，请刷新页面后重试");
                continue;
            }
            if (SEA_REGION_LOCK_KEY.equals(key)) {
                String prov = value == null || value.trim().isEmpty() ? "广东省" : value.trim();
                if (prov.length() > 32) throw new BizException("定向省钱锁省份请控制在 32 字以内");
                upsertSetting(SEA_REGION_LOCK_KEY, prov, SEA_REGION_LOCK_REMARK, "公海定向省钱锁省份已变化，请刷新页面后重试");
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
        if (raw == null || !raw.trim().matches("\\d{1,5}")) {
            throw new BizException(errorMsg);
        }
        int parsed = Integer.parseInt(raw.trim());
        if (parsed < min || parsed > max) {
            throw new BizException(errorMsg);
        }
        return parsed;
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
