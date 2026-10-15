package com.zhyq.park.marketing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.entity.SysAudit;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.mapper.SysAuditMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MktServiceContractDeletionServiceTest {
    final MktServiceContractMapper contracts = mock(MktServiceContractMapper.class);
    final CustomerMapper customers = mock(CustomerMapper.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final SysAuditMapper audits = mock(SysAuditMapper.class);
    final MktServiceContractDeletionService service = new MktServiceContractDeletionService(contracts, customers, jdbc, audits, new ObjectMapper().findAndRegisterModules());
    MktServiceContract contract;
    @BeforeEach void setup() {
        contract = new MktServiceContract(); contract.setId(9L); contract.setCustomerId(2L);
        contract.setStatus(8); contract.setContractNo("SC9"); contract.setProjectId(1L);
        when(contracts.selectById(9L)).thenReturn(contract);
        when(contracts.selectForUpdate(9L)).thenReturn(contract);
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of());
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(audits.insert(any(SysAudit.class))).thenReturn(1);
    }
    @Test void emptyTerminatedContractIsArchivedWithMandatoryAuditAndCustomerFirstLock() {
        service.delete(9L, " 误录 ");
        var order = inOrder(customers, contracts, jdbc, audits);
        order.verify(customers).selectForUpdate(2L);
        order.verify(contracts).selectForUpdate(9L);
        verify(jdbc).update(contains("WHERE id=? AND deleted=0 AND status=8"), eq("system"), eq(9L));
        var audit = ArgumentCaptor.forClass(SysAudit.class);
        verify(audits).insert(audit.capture());
        assertThat(audit.getValue().getReason()).isEqualTo("误录");
        assertThat(audit.getValue().getBeforeJson()).contains("SC9");
        assertThat(audit.getValue().getAction()).isEqualTo("contract.delete");
        verify(jdbc, never()).update(startsWith("DELETE"), any(Object[].class));
    }
    @ParameterizedTest @ValueSource(ints={1,2,3,4,5,6,7,9})
    void nonTerminatedContractCannotBeDeleted(int status) {
        contract.setStatus(status);
        assertThatThrownBy(() -> service.delete(9L,"误录")).hasMessageContaining("仅已终止");
        verifyNoInteractions(jdbc, audits);
    }
    @ParameterizedTest @ValueSource(strings={"fin_bill", "crm_service_fee_bill", "crm_direct_sign_payment", "crm_referral_order"})
    void financialHistoryIncludingSoftDeletedHistoryBlocksDeletion(String table) {
        when(jdbc.queryForList(startsWith("SELECT id FROM " + table + " WHERE"), eq(Long.class), eq(9L))).thenReturn(List.of(1L));
        assertThatThrownBy(() -> service.delete(9L,"误录")).hasMessageContaining("财务档案");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(audits);
    }
    @Test void voidBonusRuleStillBlocksSettledOrWithdrawnCommission() {
        service.delete(9L,"误录");
        verify(jdbc).queryForList(argThat(sql -> sql.contains("FROM crm_referral_order") && sql.contains("source_type<>4")
                && sql.contains("pc.status<>5") && sql.contains("withdrawn_amount") && !sql.contains("deleted=")), eq(Long.class), eq(9L));
    }
    @Test void absentContractAndConcurrentRemovalCannotProduceSuccess() {
        when(contracts.selectForUpdate(9L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(9L,"误录")).hasMessageContaining("已删除");
        verifyNoInteractions(jdbc, audits);
    }
    @Test void reasonIsRequiredBeforeAnyDatabaseAccess() {
        for (String reason : new String[]{null," ","字".repeat(501)})
            assertThatThrownBy(() -> service.delete(9L,reason)).hasMessageContaining("500字");
        verifyNoInteractions(contracts, customers, jdbc, audits);
    }
    @Test void failedCompareAndSetOrAuditMustFailTransaction() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        assertThatThrownBy(() -> service.delete(9L,"误录")).hasMessageContaining("状态已变化");
        verifyNoInteractions(audits);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(audits.insert(any(SysAudit.class))).thenReturn(0);
        assertThatThrownBy(() -> service.delete(9L,"误录")).hasMessageContaining("审计保存失败");
    }
}
