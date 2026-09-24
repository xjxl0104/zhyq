package com.zhyq.park.marketing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.SysAudit;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.SysAuditMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MktPromoterDeletionServiceTest {
    private final MktPromoterMapper promoters = mock(MktPromoterMapper.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SysAuditMapper audits = mock(SysAuditMapper.class);
    private final MktPromoterDeletionService service = new MktPromoterDeletionService(promoters, jdbc, audits, new ObjectMapper());

    @BeforeEach void setup() {
        MktPromoter p = new MktPromoter(); p.setId(7L); p.setName("空伙伴"); p.setStatus(1);
        p.setPath("/7/"); p.setInviteCode("ABC23456");
        when(promoters.selectForUpdate(7L)).thenReturn(p);
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of());
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(audits.insert(any(SysAudit.class))).thenReturn(1);
    }

    @Test void emptyPartnerDeletionReleasesLoginKeysKeepsArchiveAndRecordsReason() {
        service.delete(7L, "  误注册测试档案  ");
        var order = inOrder(promoters, jdbc, audits);
        order.verify(promoters).selectForUpdate(7L);
        verify(jdbc).update(startsWith("UPDATE crm_marketing_credential"), eq("system"), eq(7L));
        verify(jdbc).update(contains("deleted=1, status=4, phone=?, openid=NULL, unionid=NULL"), eq("D7"), eq("system"), eq(7L));
        ArgumentCaptor<SysAudit> audit = ArgumentCaptor.forClass(SysAudit.class);
        verify(audits).insert(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("promoter.delete");
        assertThat(audit.getValue().getReason()).isEqualTo("误注册测试档案");
        assertThat(audit.getValue().getBeforeJson()).contains("空伙伴", "ABC23456").doesNotContain("password", "openid", "phone");
        assertThat(audit.getValue().getAfterJson()).contains("\"deleted\":1", "\"loginRevoked\":true");
        verify(jdbc, never()).update(startsWith("DELETE"), any(Object[].class));
        verify(jdbc, never()).update(contains("crm_promoter_account"), any(Object[].class));
        verify(jdbc, never()).update(contains("crm_position_history"), any(Object[].class));
    }

    @ParameterizedTest
    @CsvSource({"crm_promoter,下级", "crm_lead,推荐线索", "crm_customer,推荐客户", "crm_customer_lock,锁定记录",
            "crm_service_contract,关联合同", "crm_referral_order,业务订单", "crm_promoter_commission,佣金流水", "crm_withdrawal,提现记录"})
    void everyBusinessDependencyStopsDeletionBeforeAnyMutation(String table, String hint) {
        when(jdbc.queryForList(startsWith("SELECT id FROM " + table + " WHERE"), eq(Long.class), any(Object[].class)))
                .thenReturn(List.of(99L));
        assertThatThrownBy(() -> service.delete(7L, "清理重复档案")).isInstanceOf(BizException.class).hasMessageContaining(hint);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(audits);
    }

    @Test void softDeletedBusinessHistoryAlsoBlocksButDeletedChildrenDoNot() {
        service.delete(7L, "清理测试");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(8)).queryForList(sql.capture(), eq(Long.class), any(Object[].class));
        assertThat(sql.getAllValues().get(0)).contains("deleted=0", "parent_id=?", "path LIKE ?");
        assertThat(sql.getAllValues().subList(1, 8)).allSatisfy(query -> assertThat(query).doesNotContain("deleted="));
    }

    @Test void missingAndRepeatedDeletionDoNotTouchCredentialsOrAudit() {
        when(promoters.selectForUpdate(7L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(7L, "重复操作")).hasMessageContaining("不存在或已删除");
        verifyNoInteractions(jdbc, audits);
    }

    @Test void reasonRequiredAndBoundedBeforeAnyDatabaseAccess() {
        for (String reason : new String[]{null, "  ", "原".repeat(501)})
            assertThatThrownBy(() -> service.delete(7L, reason)).hasMessageContaining("500字");
        verifyNoInteractions(promoters, jdbc, audits);
    }

    @Test void failedConditionalDeleteCannotProduceSuccessAudit() {
        when(jdbc.update(startsWith("UPDATE crm_promoter SET"), any(Object[].class))).thenReturn(0);
        assertThatThrownBy(() -> service.delete(7L, "清理测试")).hasMessageContaining("已变化");
        verifyNoInteractions(audits);
    }

    @Test void failedAuditThrowsSoTransactionRollsBackDeletionAndCredentialChanges() {
        when(audits.insert(any(SysAudit.class))).thenReturn(0);
        assertThatThrownBy(() -> service.delete(7L, "清理测试")).hasMessageContaining("审计保存失败");
    }

    @Test void largestIdStillHasUniqueBoundedNonPhoneTombstone() {
        MktPromoter p = new MktPromoter(); p.setId(Long.MAX_VALUE); p.setPath("/" + Long.MAX_VALUE + "/");
        when(promoters.selectForUpdate(Long.MAX_VALUE)).thenReturn(p);
        service.delete(Long.MAX_VALUE, "清理测试");
        String tombstone = "D" + Long.toString(Long.MAX_VALUE, 36);
        assertThat(tombstone.length()).isLessThanOrEqualTo(20);
        assertThat(tombstone).doesNotMatch("^1\\d{10}$");
        verify(jdbc).update(startsWith("UPDATE crm_promoter SET"), eq(tombstone), eq("system"), eq(Long.MAX_VALUE));
    }
}
