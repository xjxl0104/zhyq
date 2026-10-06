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
    private static final Set<String> ALLOWED = Set.of(
            "ladder_depth", "override_enabled", "freeze_days",
            "daily_referral_cap", "attribution_rule", "protect_days", "dup_customer_fields", "clawback_days",
            "old_channel_exclusive", "internal_commission", "monthly_cap", "tax_mode", "tax_rate",
            "default_sign_mode", "prelock_days", "lock_days", "lock_extend_days", "lock_cooldown_days",
            "lease_clawback_days", "lease_renew_ratio", "invite_grace_days", ARRIVAL_TIME);

    private final JdbcTemplate jdbc;
    private final MktAuditService auditService;

    @Operation(summary = "全部参数")
    @PreAuthorize("hasAuthority('crm:marketing:setting:query')")
    @GetMapping
    public Result<List<Map<String, Object>>> all() {
        List<Map<String, Object>> rows = new ArrayList<>(jdbc.queryForList(
                "SELECT skey, svalue, remark FROM biz_setting WHERE module = ? AND deleted = 0 "
                        + "AND skey NOT IN ('min_withdraw', 'withdraw_free_times') ORDER BY id", MODULE));
        // The default is visible without writing during GET; saving it creates the missing setting.
        if (rows.stream().noneMatch(row -> ARRIVAL_TIME.equals(row.get("skey")))) {
            rows.add(Map.of("skey", ARRIVAL_TIME, "svalue", MktWithdrawalService.DEFAULT_ARRIVAL_TIME, "remark", ARRIVAL_REMARK));
        }
        return Result.ok(rows);
    }

    @Operation(summary = "批量保存(只改白名单 key)")
    @PreAuthorize("hasAuthority('crm:marketing:setting:config')")
    @PutMapping
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Result<Void> update(@RequestBody List<Map<String, String>> items) {
        for (Map<String, String> it : items) {
            String key = it.get("skey");
            String value = it.get("svalue");
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

    private void saveArrivalTime(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty() || value.codePointCount(0, value.length()) > 200) {
            throw new BizException("请填写实际提现到账时效说明，不能为空且最多200字");
        }
        // biz_setting has a non-unique index. Lock the key range and guard insertion;
        // ON DUPLICATE KEY would not prevent a second row for a previously absent key.
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT svalue FROM biz_setting WHERE module = ? AND skey = ? AND deleted = 0 FOR UPDATE", MODULE, ARRIVAL_TIME);
        String before = existing.isEmpty() ? null : (String) existing.get(0).get("svalue");
        if (value.equals(before)) return;
        int changed;
        if (existing.isEmpty()) {
            String operator = MktAuditService.currentOperator();
            changed = jdbc.update("INSERT INTO biz_setting (module, skey, svalue, remark, create_by, create_time, update_by, update_time) "
                            + "SELECT ?, ?, ?, ?, ?, NOW(), ?, NOW() WHERE NOT EXISTS "
                            + "(SELECT 1 FROM biz_setting WHERE module = ? AND skey = ? AND deleted = 0)",
                    MODULE, ARRIVAL_TIME, value, ARRIVAL_REMARK, operator, operator, MODULE, ARRIVAL_TIME);
        } else {
            changed = jdbc.update("UPDATE biz_setting SET svalue = ?, update_time = NOW() WHERE module = ? AND skey = ? AND deleted = 0",
                    value, MODULE, ARRIVAL_TIME);
        }
        if (changed < 1) throw new BizException("提现到账时效设置已变化，请刷新页面后重试");
        auditService.log("setting.change", "setting", null, ARRIVAL_TIME, before, value);
    }
}
