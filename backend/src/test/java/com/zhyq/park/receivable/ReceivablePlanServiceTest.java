package com.zhyq.park.receivable;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.receivable.dto.ReceivableGenerateResult;
import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.entity.ReceivableRule;
import com.zhyq.park.receivable.mapper.ReceivableRegisterMapper;
import com.zhyq.park.receivable.mapper.ReceivableRuleMapper;
import com.zhyq.park.receivable.service.ReceivableCalculator;
import com.zhyq.park.receivable.service.ReceivablePlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceivablePlanServiceTest {
    @Mock private ReceivableRegisterMapper registerMapper;
    @Mock private ReceivableRuleMapper ruleMapper;
    @Mock private BillMapper billMapper;

    private ReceivablePlanService service;

    @BeforeEach
    void setUp() {
        service = new ReceivablePlanService(
                registerMapper, ruleMapper, billMapper, new ReceivableCalculator());
    }

    @Test
    void createsSeparateRentPropertyAndTwoDepositBills() {
        when(registerMapper.selectByIdForUpdate(7L)).thenReturn(register("CONFIRMED"));
        when(ruleMapper.selectList(any())).thenReturn(rules());
        when(billMapper.selectCount(any())).thenReturn(0L);
        when(billMapper.insert(any(Bill.class))).thenReturn(1);

        ReceivableGenerateResult result = service.generate(7L);

        assertEquals(4, result.totalCandidates());
        assertEquals(4, result.inserted());
        assertEquals(0, result.skipped());
        ArgumentCaptor<Bill> captor = ArgumentCaptor.forClass(Bill.class);
        verify(billMapper, times(4)).insert(captor.capture());
        List<Bill> bills = captor.getAllValues();
        assertEquals(Set.of("租金", "物业费", "租金保证金", "物业保证金"),
                bills.stream().map(Bill::getFeeType).collect(Collectors.toSet()));
        assertEquals(4, bills.stream().map(Bill::getBillingKey).distinct().count());
        assertTrue(bills.stream().allMatch(bill -> bill.getBillingKey().contains(":v3:")));
    }

    @Test
    void secondGenerationSkipsExistingBillingKeys() {
        when(registerMapper.selectByIdForUpdate(7L)).thenReturn(register("CONFIRMED"));
        when(ruleMapper.selectList(any())).thenReturn(rules());
        when(billMapper.selectCount(any())).thenReturn(1L);

        ReceivableGenerateResult result = service.generate(7L);

        assertEquals(4, result.skipped());
        assertEquals(0, result.inserted());
        verify(billMapper, never()).insert(any(Bill.class));
    }

    @Test
    void rejectsUnconfirmedOrUnresolvedRegister() {
        when(registerMapper.selectByIdForUpdate(7L)).thenReturn(register("DRAFT"));
        assertThrows(BizException.class, () -> service.generate(7L));

        ReceivableRegister unresolved = register("CONFIRMED");
        unresolved.setTenantRefId(null);
        when(registerMapper.selectByIdForUpdate(8L)).thenReturn(unresolved);
        assertThrows(BizException.class, () -> service.generate(8L));
    }

    private static ReceivableRegister register(String status) {
        ReceivableRegister register = new ReceivableRegister();
        register.setId(7L);
        register.setTenantId(1L);
        register.setTenantRefId(20L);
        register.setSpaceId(30L);
        register.setRoomId(40L);
        register.setContractId(50L);
        register.setStatus(status);
        register.setSourceVersion(3);
        register.setContractStartDate(LocalDate.of(2026, 5, 1));
        register.setContractEndDate(LocalDate.of(2026, 5, 31));
        register.setMonthlyRent(new BigDecimal("100000"));
        register.setMonthlyProperty(new BigDecimal("20000"));
        register.setRentDeposit(new BigDecimal("200000"));
        register.setPropertyDeposit(new BigDecimal("40000"));
        register.setCollectionTimingRaw("当月13号前缴纳当月租金");
        return register;
    }

    private static List<ReceivableRule> rules() {
        ReceivableRule rent = new ReceivableRule();
        rent.setId(101L);
        rent.setFeeType("RENT");
        rent.setRuleType("AUTHORITATIVE_MONTHLY");
        rent.setFixedAmount(new BigDecimal("100000"));
        rent.setStatus("ACTIVE");
        ReceivableRule property = new ReceivableRule();
        property.setId(102L);
        property.setFeeType("PROPERTY");
        property.setRuleType("AUTHORITATIVE_MONTHLY");
        property.setFixedAmount(new BigDecimal("20000"));
        property.setStatus("ACTIVE");
        return List.of(rent, property);
    }
}
