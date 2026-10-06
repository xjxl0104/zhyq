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
    @ValueSource(strings = { "min_withdraw", "withdraw_free_times" })
    void aStaleAdminPageCannotRestoreRetiredWithdrawalRestrictions(String key) {
        assertThatThrownBy(() -> controller.update(List.of(Map.of("skey", key, "svalue", "100"))))
                .isInstanceOf(BizException.class).hasMessageContaining("旧参数已停用");
        verifyNoInteractions(jdbc, audit);
    }

    @Test void readingMissingArrivalPolicyShowsEditableDefaultWithoutWritingIt() {
        when(jdbc.queryForList(anyString(), eq("marketing"))).thenReturn(List.of());
        var rows = controller.all().getData();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("skey", "withdraw_arrival_time")
                .containsEntry("svalue", MktWithdrawalService.DEFAULT_ARRIVAL_TIME);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(audit);
    }

    @Test void anExistingArrivalPolicyIsReturnedWithoutAddingASecondDefault() {
        var existing = Map.<String, Object>of("skey", "withdraw_arrival_time", "svalue", "实际运营说明", "remark", "说明");
        when(jdbc.queryForList(anyString(), eq("marketing"))).thenReturn(List.of(existing));
        assertThat(controller.all().getData()).containsExactly(existing);
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
