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
    void secondGenerationSynchronizesExistingUnpaidBills() {
        when(registerMapper.selectByIdForUpdate(7L)).thenReturn(register("CONFIRMED"));
        when(ruleMapper.selectList(any())).thenReturn(rules());
        Bill existing = new Bill();
        existing.setId(99L);
        existing.setPaidAmount(BigDecimal.ZERO);
        existing.setStatus(3);
        when(billMapper.selectOne(any())).thenReturn(existing);
        // 覆盖必须走条件更新(entity + wrapper 带 paid_amount=0 前置条件),不再 updateById 盲写
        when(billMapper.update(any(Bill.class), any())).thenReturn(1);

        ReceivableGenerateResult result = service.generate(7L);

        assertEquals(4, result.updated());
        assertEquals(0, result.skipped());
        assertEquals(0, result.inserted());
        verify(billMapper, never()).insert(any(Bill.class));
        verify(billMapper, never()).updateById(any(Bill.class));
        ArgumentCaptor<Bill> captor = ArgumentCaptor.forClass(Bill.class);
        verify(billMapper, times(4)).update(captor.capture(), any());
        // 覆盖只同步生成侧字段;滞纳金/状态/实收/逾期天数/开票是运行时状态,
        // 重跑生成把滞纳金清零等于免债(滞纳金已计入应收)
        for (Bill patch : captor.getAllValues()) {
            assertEquals(null, patch.getLateFee());
            assertEquals(null, patch.getStatus());
            assertEquals(null, patch.getPaidAmount());
            assertEquals(null, patch.getOverdueDays());
            assertEquals(null, patch.getInvoiceStatus());
            assertTrue(patch.getAmount() != null);
        }
    }

    @Test
    void syncSkipsWhenPaymentRacesInBetweenReadAndWrite() {
        // 读快照时未收款(canSynchronize 通过),但条件更新时钱刚到账 → updated==0,
        // 必须计入 skipped 而不是把 paid_amount 清零覆盖掉
        when(registerMapper.selectByIdForUpdate(7L)).thenReturn(register("CONFIRMED"));
        when(ruleMapper.selectList(any())).thenReturn(rules());
        Bill existing = new Bill();
        existing.setId(99L);
        existing.setPaidAmount(BigDecimal.ZERO);
        existing.setStatus(3);
        when(billMapper.selectOne(any())).thenReturn(existing);
        when(billMapper.update(any(Bill.class), any())).thenReturn(0);

        ReceivableGenerateResult result = service.generate(7L);

        assertEquals(0, result.updated());
        assertEquals(4, result.skipped());
        assertEquals(0, result.inserted());
    }

    @Test
    void syncSkipsPaidBillsWithoutTouchingDb() {
        when(registerMapper.selectByIdForUpdate(7L)).thenReturn(register("CONFIRMED"));
        when(ruleMapper.selectList(any())).thenReturn(rules());
        Bill paid = new Bill();
        paid.setId(99L);
        paid.setPaidAmount(new BigDecimal("50"));
        paid.setStatus(4);
        when(billMapper.selectOne(any())).thenReturn(paid);

        ReceivableGenerateResult result = service.generate(7L);

        assertEquals(0, result.updated());
        assertEquals(4, result.skipped());
        verify(billMapper, never()).update(any(Bill.class), any());
        verify(billMapper, never()).updateById(any(Bill.class));
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

    @Test
    void generatesBillsFromConfirmedRegisterWhenFormalContractIsNotLinked() {
        ReceivableRegister noContract = register("CONFIRMED");
        noContract.setContractId(null);
        when(registerMapper.selectByIdForUpdate(7L)).thenReturn(noContract);
        when(ruleMapper.selectList(any())).thenReturn(rules());
        when(billMapper.insert(any(Bill.class))).thenReturn(1);

        ReceivableGenerateResult result = service.generate(7L);

        assertEquals(4, result.inserted());
        verify(billMapper, times(4)).insert(any(Bill.class));
    }

    // ---------- 按月按需出账(不再一次性铺满整个合同期) ----------

    @Test
    void generatesOnlyArrivedPeriodsPlusDeposits() {
        // 固定"今天"=2026-09-02:合同 2026-07~2027-12,只出 07/08/09 三个月 + 两张保证金
        ReceivableRegister spanning = register("CONFIRMED");
        spanning.setContractStartDate(LocalDate.of(2026, 7, 1));
        spanning.setContractEndDate(LocalDate.of(2027, 12, 31));
        when(registerMapper.selectByIdForUpdate(7L)).thenReturn(spanning);
        when(ruleMapper.selectList(any())).thenReturn(rules());
        when(billMapper.insert(any(Bill.class))).thenReturn(1);

        ReceivableGenerateResult result = serviceAt(LocalDate.of(2026, 9, 2)).generate(7L);

        assertEquals(8, result.totalCandidates());
        assertEquals(8, result.inserted());
        ArgumentCaptor<Bill> captor = ArgumentCaptor.forClass(Bill.class);
        verify(billMapper, times(8)).insert(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .map(Bill::getPeriodStart)
                .noneMatch(start -> start.isAfter(LocalDate.of(2026, 9, 30))));
    }

    @Test
    void futureContractGeneratesDepositsOnly() {
        ReceivableRegister future = register("CONFIRMED");
        future.setContractStartDate(LocalDate.of(2027, 1, 1));
        future.setContractEndDate(LocalDate.of(2027, 12, 31));
        when(registerMapper.selectByIdForUpdate(7L)).thenReturn(future);
        when(ruleMapper.selectList(any())).thenReturn(rules());
        when(billMapper.insert(any(Bill.class))).thenReturn(1);

        ReceivableGenerateResult result = serviceAt(LocalDate.of(2026, 9, 2)).generate(7L);

        assertEquals(2, result.totalCandidates());
        ArgumentCaptor<Bill> captor = ArgumentCaptor.forClass(Bill.class);
        verify(billMapper, times(2)).insert(captor.capture());
        assertEquals(Set.of("租金保证金", "物业保证金"),
                captor.getAllValues().stream().map(Bill::getFeeType).collect(Collectors.toSet()));
    }

    @Test
    void advanceCollectionEmitsNextMonthOnItsDueDate() {
        // 「当月30日前收取下个月」:下月账单的应收日落在本月 30 日,当天就要能出
        ReceivableRegister advance = register("CONFIRMED");
        advance.setContractStartDate(LocalDate.of(2026, 7, 1));
        advance.setContractEndDate(LocalDate.of(2027, 12, 31));
        advance.setCollectionTimingRaw("当月30日前收取下个月租金");
        when(registerMapper.selectByIdForUpdate(7L)).thenReturn(advance);
        when(ruleMapper.selectList(any())).thenReturn(rules());
        when(billMapper.insert(any(Bill.class))).thenReturn(1);

        // 9/2:10 月账单应收日(9/30)未到,不出 → 3 个月×2 + 2 张保证金
        assertEquals(8, serviceAt(LocalDate.of(2026, 9, 2)).generate(7L).totalCandidates());
        // 9/30:10 月账单应收日已到,提前放行 → 4 个月×2 + 2 张保证金
        assertEquals(10, serviceAt(LocalDate.of(2026, 9, 30)).generate(7L).totalCandidates());
    }

    /** 固定"今天"的服务实例:按月按需出账以自然日为界 */
    private ReceivablePlanService serviceAt(LocalDate fixedToday) {
        return new ReceivablePlanService(
                registerMapper, ruleMapper, billMapper, new ReceivableCalculator()) {
            @Override
            protected LocalDate today() {
                return fixedToday;
            }
        };
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
