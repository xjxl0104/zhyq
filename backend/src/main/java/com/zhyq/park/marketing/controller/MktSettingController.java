package com.zhyq.park.marketing.controller;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.service.MktAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 规则参数(biz_setting module=marketing),只允许白名单 key(V57 种子里那 23 个)。 */
@Tag(name = "全民营销-规则参数")
@RestController
@RequestMapping("/crm/marketing/setting")
@RequiredArgsConstructor
public class MktSettingController {

    private static final String MODULE = "marketing";
    private static final Set<String> ALLOWED = Set.of(
            "ladder_depth", "override_enabled", "freeze_days", "min_withdraw", "withdraw_free_times",
            "daily_referral_cap", "attribution_rule", "protect_days", "dup_customer_fields", "clawback_days",
            "old_channel_exclusive", "internal_commission", "monthly_cap", "tax_mode", "tax_rate",
            "default_sign_mode", "prelock_days", "lock_days", "lock_extend_days", "lock_cooldown_days",
            "lease_clawback_days", "lease_renew_ratio", "invite_grace_days");

    private final JdbcTemplate jdbc;
    private final MktAuditService auditService;

    @Operation(summary = "全部参数")
    @PreAuthorize("hasAuthority('crm:marketing:setting:query')")
    @GetMapping
    public Result<List<Map<String, Object>>> all() {
        return Result.ok(jdbc.queryForList(
                "SELECT skey, svalue, remark FROM biz_setting WHERE module = ? AND deleted = 0 ORDER BY id", MODULE));
    }

    @Operation(summary = "批量保存(只改白名单 key)")
    @PreAuthorize("hasAuthority('crm:marketing:setting:config')")
    @PutMapping
    @Transactional
    public Result<Void> update(@RequestBody List<Map<String, String>> items) {
        for (Map<String, String> it : items) {
            String key = it.get("skey");
            String value = it.get("svalue");
            if (key == null || !ALLOWED.contains(key)) {
                throw new BizException("不允许修改的参数: " + key);
            }
            if ("ladder_depth".equals(key)) {
                throw new BizException("岗位数请在「岗位与份额」页切换");
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
}
