package com.zhyq.park.receivable;

import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.importing.mapper.ImportBatchMapper;
import com.zhyq.park.importing.mapper.ImportRowMapper;
import com.zhyq.park.receivable.controller.ReceivableController;
import com.zhyq.park.receivable.dto.ReceivableUpsertRequest;
import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.mapper.CollectionAccountMapper;
import com.zhyq.park.receivable.mapper.DepositLedgerMapper;
import com.zhyq.park.receivable.mapper.ReceivableRegisterMapper;
import com.zhyq.park.receivable.mapper.ReceivableRuleMapper;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import com.zhyq.park.receivable.service.ReceivableCalculator;
import com.zhyq.park.receivable.service.ReceivableExportService;
import com.zhyq.park.receivable.service.ReceivableImportService;
import com.zhyq.park.receivable.service.ReceivablePlanService;
import com.zhyq.park.receivable.service.ReceivableProvisionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceivableControllerMutationTest {
    private final ReceivableRegisterMapper registers = mock(ReceivableRegisterMapper.class);
    private final ReceivableRuleMapper rules = mock(ReceivableRuleMapper.class);
    private final DepositLedgerMapper deposits = mock(DepositLedgerMapper.class);
    private final BillMapper bills = mock(BillMapper.class);
    private ReceivableController controller;

    @BeforeEach
    void setUp() {
        controller = new ReceivableController(registers, rules, deposits, bills,
                mock(CollectionAccountMapper.class), mock(ReceivableImportService.class),
                mock(ReceivablePlanService.class),
                mock(com.zhyq.park.receivable.service.ReceivableAutoBillService.class),
                mock(ReceivableProvisionService.class),
                mock(ReceivableExportService.class),
                mock(FieldEncryptionService.class), mock(ImportBatchMapper.class),
                mock(ImportRowMapper.class), mock(ReceivableCalculator.class));
        when(registers.insert(any(ReceivableRegister.class))).thenAnswer(invocation -> {
            ((ReceivableRegister) invocation.getArgument(0)).setId(99L);
            return 1;
        });
    }

    @Test
    void manualCreateUsesServerOwnedStatusAndSourceFields() {
        controller.add(request(null, "100.00", "20.00", "120.00"));

        ArgumentCaptor<ReceivableRegister> captor = ArgumentCaptor.forClass(ReceivableRegister.class);
        verify(registers).insert(captor.capture());
        ReceivableRegister saved = captor.getValue();
        assertEquals("DRAFT", saved.getStatus());
        assertEquals(1, saved.getSourceVersion());
        assertNull(saved.getSourceBatchId());
        assertNull(saved.getSourceRowId());
        assertEquals(new BigDecimal("120.00"), saved.getMonthlyTotal());
    }

    @Test
    void confirmedOrImportedRegistersCannotBeRewrittenByGenericEdit() {
        ReceivableRegister existing = new ReceivableRegister();
        existing.setId(7L);
        existing.setStatus("CONFIRMED");
        existing.setSourceBatchId(3L);
        existing.setSourceVersion(4);
        when(registers.selectById(7L)).thenReturn(existing);

        assertThrows(RuntimeException.class,
                () -> controller.update(request(7L, "1.00", "2.00", "3.00")));
    }

    @Test
    void deletingDraftRegisterAlsoDeletesItsRulesAndDeposits() {
        when(bills.selectCount(any())).thenReturn(0L);
        controller.delete(8L);
        verify(rules).delete(any());
        verify(deposits).delete(any());
        verify(registers).deleteById(8L);
    }

    private static ReceivableUpsertRequest request(Long id, String rent, String property, String total) {
        return new ReceivableUpsertRequest(id, "HT-1", "租户甲", "A-101",
                new BigDecimal(rent), new BigDecimal(property), new BigDecimal(total),
                11L, 12L, null, 13L);
    }
}
