package com.zhyq.park.marketing.controller;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.service.MktAuditService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.api.Test;
import com.zhyq.park.marketing.service.MktWithdrawalService;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MktWithdrawalSettingTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MktAuditService audit = mock(MktAuditService.class);
    private final MktSettingController controller = new MktSettingController(jdbc, audit);
    @ParameterizedTest
    @ValueSource(strings = { "prelock_days", "lock_days", "lock_extend_days" })
    void aStaleAdminPageCannotRestoreRetiredLockRules(String key) {
        assertThatThrownBy(() -> controller.update(List.of(Map.of("skey", key, "svalue", "180"))))
                .isInstanceOf(BizException.class).hasMessageContaining("旧锁定参数已停用");
        verifyNoInteractions(jdbc, audit);
    }

    @ParameterizedTest
    @ValueSource(strings = { "min_withdraw", "withdraw_free_times" })
    void aStaleAdminPageCannotRestoreRetiredWithdrawalRestrictions(String key) {
        assertThatThrownBy(() -> controller.update(List.of(Map.of("skey", key, "svalue", "100"))))
                .isInstanceOf(BizException.class).hasMessageContaining("旧参数已停用");
        verifyNoInteractions(jdbc, audit);
    }

    @Test void readingMissingArrivalPolicyShowsEditableDefaultWithoutWritingIt() {
        when(jdbc.queryForList(anyString(), eq("marketing"))).thenReturn(List.of());
        var rows = controller.all().getData();
        assertThat(rows).hasSize(4);
        assertThat(rows).extracting(r -> r.get("skey")).containsExactly(
                "withdraw_arrival_time", "withdraw_window_start_day", "withdraw_window_end_day",
                "auto_withdraw_day");
        assertThat(rows.get(0)).containsEntry("skey", "withdraw_arrival_time")
                .containsEntry("svalue", MktWithdrawalService.DEFAULT_ARRIVAL_TIME);
        assertThat(rows.get(1)).containsEntry("skey", "withdraw_window_start_day").containsEntry("svalue", "0");
        assertThat(rows.get(2)).containsEntry("skey", "withdraw_window_end_day").containsEntry("svalue", "31");
        assertThat(rows.get(3)).containsEntry("skey", "auto_withdraw_day").containsEntry("svalue", "10");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(audit);
    }

    @Test void anExistingArrivalPolicyIsReturnedWithoutAddingASecondDefault() {
        var existing = Map.<String, Object>of("skey", "withdraw_arrival_time", "svalue", "实际运营说明", "remark", "说明");
        when(jdbc.queryForList(anyString(), eq("marketing"))).thenReturn(List.of(existing));
        var rows = controller.all().getData();
        assertThat(rows.stream().filter(r -> "withdraw_arrival_time".equals(r.get("skey"))).toList())
                .containsExactly(existing);
        assertThat(rows).hasSize(4);
    }

    @Test void retiredGhostKeysAreFilteredOutFromSettingsList() {
        var ghost1 = Map.<String, Object>of("skey", "attribution_rule", "svalue", "1", "remark", "旧参数");
        var ghost2 = Map.<String, Object>of("skey", "monthly_cap", "svalue", "50000", "remark", "旧参数");
        var active = Map.<String, Object>of("skey", "freeze_days", "svalue", "7", "remark", "冻结天数");
        when(jdbc.queryForList(anyString(), eq("marketing"))).thenReturn(List.of(ghost1, ghost2, active));
        var keys = controller.all().getData().stream().map(r -> String.valueOf(r.get("skey"))).toList();
        assertThat(keys).contains("freeze_days", "withdraw_window_start_day")
                .doesNotContainAnyElementsOf(MktSettingController.RETIRED_GHOST_KEYS);
    }

    @Test void windowAndAutoWithdrawSupportSafeUpsertAndValidation() {
        when(jdbc.queryForList(contains("FOR UPDATE"), eq("marketing"), anyString())).thenReturn(List.of());
        when(jdbc.update(startsWith("INSERT"), any(Object[].class))).thenReturn(1);

        controller.update(List.of(
                Map.of("skey", "withdraw_window_start_day", "svalue", "0"),
                Map.of("skey", "withdraw_window_end_day", "svalue", "31"),
                Map.of("skey", "auto_withdraw_day", "svalue", "10")));
        verify(audit).log("setting.change", "setting", null, "withdraw_window_start_day", null, "0");
        verify(audit).log("setting.change", "setting", null, "withdraw_window_end_day", null, "31");
        verify(audit).log("setting.change", "setting", null, "auto_withdraw_day", null, "10");

        assertThatThrownBy(() -> controller.update(List.of(Map.of("skey", "withdraw_window_start_day", "svalue", "29"))))
                .isInstanceOf(BizException.class).hasMessageContaining("0–28");
        assertThatThrownBy(() -> controller.update(List.of(Map.of("skey", "withdraw_window_end_day", "svalue", "0"))))
                .isInstanceOf(BizException.class).hasMessageContaining("1–31");
        assertThatThrownBy(() -> controller.update(List.of(Map.of("skey", "auto_withdraw_day", "svalue", "29"))))
                .isInstanceOf(BizException.class).hasMessageContaining("0–28");
    }

    @Test void savingTheVirtualDefaultActuallyInsertsAndAuditsTheMissingRow() {
        when(jdbc.queryForList(contains("FOR UPDATE"), eq("marketing"), eq("withdraw_arrival_time"))).thenReturn(List.of());
        when(jdbc.update(startsWith("INSERT"), any(Object[].class))).thenReturn(1);
        controller.update(List.of(Map.of("skey", "withdraw_arrival_time", "svalue", "  实际运营说明  ")));
        verify(jdbc).update(startsWith("INSERT"), eq("marketing"), eq("withdraw_arrival_time"), eq("实际运营说明"),
                anyString(), anyString(), anyString(), eq("marketing"), eq("withdraw_arrival_time"));
        verify(audit).log("setting.change", "setting", null, "withdraw_arrival_time", null, "实际运营说明");
    }

    @Test void savingExistingArrivalPolicyUpdatesTheRowAndPreservesAnAuditTrail() {
        when(jdbc.queryForList(contains("FOR UPDATE"), eq("marketing"), eq("withdraw_arrival_time")))
                .thenReturn(List.of(Map.of("svalue", "原时效说明")));
        when(jdbc.update(startsWith("UPDATE"), eq("新时效说明"), eq("marketing"), eq("withdraw_arrival_time"))).thenReturn(1);
        controller.update(List.of(Map.of("skey", "withdraw_arrival_time", "svalue", "新时效说明")));
        verify(audit).log("setting.change", "setting", null, "withdraw_arrival_time", "原时效说明", "新时效说明");
        verify(jdbc, never()).update(startsWith("INSERT"), any(Object[].class));
    }

    @Test void aFailedInsertCannotBeReportedAsASuccessfulPolicySave() {
        when(jdbc.queryForList(contains("FOR UPDATE"), eq("marketing"), eq("withdraw_arrival_time"))).thenReturn(List.of());
        assertThatThrownBy(() -> controller.update(List.of(Map.of("skey", "withdraw_arrival_time", "svalue", "实际运营说明"))))
                .isInstanceOf(BizException.class).hasMessageContaining("刷新页面");
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   " })
    void emptyArrivalPromisesAreRejectedBeforeAnyDatabaseWrite(String value) {
        var body = new java.util.HashMap<String, String>(); body.put("skey", "withdraw_arrival_time"); body.put("svalue", value);
        assertThatThrownBy(() -> controller.update(List.of(body))).isInstanceOf(BizException.class).hasMessageContaining("不能为空");
        verifyNoInteractions(jdbc, audit);
    }

    @Test void arrivalPolicyCannotExceedTwoHundredCharacters() {
        assertThatThrownBy(() -> controller.update(List.of(Map.of("skey", "withdraw_arrival_time", "svalue", "长".repeat(201)))))
                .isInstanceOf(BizException.class).hasMessageContaining("最多200字");
        verifyNoInteractions(jdbc, audit);
    }
}
