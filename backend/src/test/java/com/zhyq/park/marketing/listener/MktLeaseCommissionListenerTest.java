package com.zhyq.park.marketing.listener;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.contract.entity.Contract;
import com.zhyq.park.contract.mapper.ContractMapper;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CommissionMapper;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.marketing.entity.MktCustomerGrade;
import com.zhyq.park.marketing.entity.MktReferralOrder;
import com.zhyq.park.marketing.mapper.MktCustomerGradeMapper;
import com.zhyq.park.marketing.mapper.MktReferralOrderMapper;
import com.zhyq.park.marketing.service.MktCommissionService;
import com.zhyq.park.marketing.service.MktLockService;
import com.zhyq.park.marketing.service.MktServiceContractService;
import com.zhyq.park.marketing.service.MktCommissionService.CommissionEvent;
import com.zhyq.park.tenant.entity.BizTenant;
import com.zhyq.park.tenant.mapper.BizTenantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MktLeaseCommissionListenerTest {

    @Mock ContractMapper contractMapper;
    @Mock BillMapper billMapper;
    @Mock BizTenantMapper tenantMapper;
    @Mock CustomerMapper customerMapper;
    @Mock CommissionMapper legacyCommissionMapper;
    @Mock MktCustomerGradeMapper gradeMapper;
    @Mock MktReferralOrderMapper orderMapper;
    @Mock MktCommissionService commissionService;
    @Mock MktLockService lockService;
    @Mock MktServiceContractService serviceContractService;
    @Mock BizSettings bizSettings;
    @Mock com.zhyq.park.marketing.mapper.MktServiceContractMapper serviceContracts;

    MktLeaseCommissionListener listener;

    @org.junit.jupiter.api.BeforeAll static void metadata() {
        var a = new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(a, com.zhyq.park.finance.entity.Bill.class);
    }

    @BeforeEach
    void setUp() {
        listener = new MktLeaseCommissionListener(contractMapper, billMapper, tenantMapper, customerMapper,
                legacyCommissionMapper, gradeMapper, orderMapper, commissionService, lockService, serviceContractService, bizSettings, serviceContracts);
    }

    @Test
    void approvedLeaseContractWithReferredTenantCreatesOneOffCommission() {
        // A 级:300 元/㎡/月 × 100 ㎡ × 1 个月 = 佣金池 30000,基数 = 月租金 30000
        when(contractMapper.selectById(10L)).thenReturn(contract(10L, 7L, "300", "100", "A"));
        when(tenantMapper.selectById(7L)).thenReturn(tenant("13800000001"));
        when(customerMapper.selectOne(any(Wrapper.class))).thenReturn(customer(5L, 99L, null));
        when(legacyCommissionMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(gradeMapper.selectOne(any(Wrapper.class))).thenReturn(grade("A", "1.00"));

        listener.onContractApproved(new DomainEvent.ContractApproved(10L, "HT-1", 7L, 1L, LocalDateTime.now()));

        ArgumentCaptor<CommissionEvent> cap = ArgumentCaptor.forClass(CommissionEvent.class);
        verify(commissionService).createAndSplitInTransaction(cap.capture());
        verify(lockService).markDeal(5L, 99L);
        CommissionEvent ev = cap.getValue();
        assertThat(ev.sourceType()).isEqualTo(MktCommissionService.SOURCE_LEASE);
        assertThat(ev.sourceNo()).isEqualTo("LEASE-10");
        assertThat(ev.sellerPromoterId()).isEqualTo(99L);
        assertThat(ev.grade()).isEqualTo("A");
        assertThat(ev.poolAmount()).isEqualByComparingTo("30000.00");
        assertThat(ev.baseAmount()).isEqualByComparingTo("30000");
        assertThat(ev.unfreezeAt()).isNull(); // 租赁靠到账事件解冻,不设自动解冻时间
    }

    @Test
    void tenantWithoutReferrerIsIgnored() {
        when(contractMapper.selectById(10L)).thenReturn(contract(10L, 7L, "300", "100", null));
        when(tenantMapper.selectById(7L)).thenReturn(tenant("13800000001"));
        when(customerMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        listener.onContractApproved(new DomainEvent.ContractApproved(10L, "HT-1", 7L, 1L, LocalDateTime.now()));

        verify(commissionService, never()).createAndSplitInTransaction(any());
    }

    @Test
    void legacyChannelCommissionBlocksWhenExclusive() {
        when(contractMapper.selectById(10L)).thenReturn(contract(10L, 7L, "300", "100", "A"));
        when(tenantMapper.selectById(7L)).thenReturn(tenant("13800000001"));
        when(customerMapper.selectOne(any(Wrapper.class))).thenReturn(customer(5L, 99L, "A"));
        when(legacyCommissionMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(bizSettings.getBoolean(eq("marketing"), eq("old_channel_exclusive"), anyBoolean())).thenReturn(true);

        listener.onContractApproved(new DomainEvent.ContractApproved(10L, "HT-1", 7L, 1L, LocalDateTime.now()));

        verify(commissionService, never()).createAndSplitInTransaction(any());
    }

    @Test
    void gradeFallsBackToCustomerThenD() {
        when(contractMapper.selectById(10L)).thenReturn(contract(10L, 7L, "100", "10", null));
        when(tenantMapper.selectById(7L)).thenReturn(tenant("13800000001"));
        when(customerMapper.selectOne(any(Wrapper.class))).thenReturn(customer(5L, 99L, null));
        when(legacyCommissionMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(gradeMapper.selectOne(any(Wrapper.class))).thenReturn(grade("D", "0.50"));

        listener.onContractApproved(new DomainEvent.ContractApproved(10L, "HT-1", 7L, 1L, LocalDateTime.now()));

        ArgumentCaptor<CommissionEvent> cap = ArgumentCaptor.forClass(CommissionEvent.class);
        verify(commissionService).createAndSplitInTransaction(cap.capture());
        assertThat(cap.getValue().grade()).isEqualTo("D");
        assertThat(cap.getValue().poolAmount()).isEqualByComparingTo("500.00"); // 1000 × 0.5
    }

    @Test
    void paymentReceivedUnfreezesLeaseAndBonusOrdersOfThatContract() {
        MktReferralOrder o1 = new MktReferralOrder(); o1.setId(31L);
        MktReferralOrder o2 = new MktReferralOrder(); o2.setId(32L);
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(o1, o2));
        var bill = rentBill(500L, "1000", 5);
        when(billMapper.selectById(500L)).thenReturn(bill);
        when(billMapper.selectList(any())).thenReturn(List.of(bill));
        lenient().when(commissionService.unfreezeByOrderInTransaction(31L)).thenReturn(2);
        lenient().when(commissionService.unfreezeByOrderInTransaction(32L)).thenReturn(0);

        listener.onPaymentReceived(new DomainEvent.PaymentReceived(500L, 10L, LocalDateTime.now()));

        verify(commissionService).unfreezeByOrderInTransaction(31L);
        verify(commissionService).unfreezeByOrderInTransaction(32L);
    }

    @Test
    void serviceBillOnlyUnfreezesContractBonusAndStartsPerforming() {
        MktReferralOrder o = new MktReferralOrder(); o.setId(31L);
        com.zhyq.park.finance.entity.Bill bill = new com.zhyq.park.finance.entity.Bill();
        bill.setSource("mkt_service"); bill.setBillingKey("mkt_service:10:first");
        bill.setStatus(5); bill.setFeeType("租金"); bill.setAmount(BigDecimal.TEN); bill.setPaidAmount(BigDecimal.TEN);
        when(billMapper.selectById(500L)).thenReturn(bill);
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(o));
        when(commissionService.unfreezeByOrderInTransaction(31L)).thenReturn(1);
        listener.onPaymentReceived(new DomainEvent.PaymentReceived(500L, 10L, LocalDateTime.now()));
        verify(commissionService).unfreezeByOrderInTransaction(31L);
        verify(serviceContractService).startPerforming(10L);
    }

    @Test
    void firstAnchoredRentBillUnfreezesBonus() {
        var bill = new com.zhyq.park.finance.entity.Bill();
        bill.setSource("mkt_service"); bill.setBillingKey("mkt_service:10:rent:2026-01-31");
        bill.setStatus(5); bill.setFeeType("租金"); bill.setAmount(BigDecimal.TEN); bill.setPaidAmount(BigDecimal.TEN);
        var contract = new com.zhyq.park.marketing.entity.MktServiceContract();
        contract.setStartDate(java.time.LocalDate.of(2026, 1, 31));
        when(serviceContracts.selectById(10L)).thenReturn(contract);
        when(billMapper.selectById(500L)).thenReturn(bill);
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        listener.onPaymentReceived(new DomainEvent.PaymentReceived(500L, 10L, LocalDateTime.now()));
        verify(serviceContractService).startPerforming(10L);
    }

    @Test
    void laterServiceBillDoesNotUnfreezeContractBonus() {
        com.zhyq.park.finance.entity.Bill bill = new com.zhyq.park.finance.entity.Bill();
        bill.setSource("mkt_service"); bill.setBillingKey("mkt_service:10:month-2");
        bill.setStatus(5); bill.setFeeType("租金"); bill.setAmount(BigDecimal.TEN); bill.setPaidAmount(BigDecimal.TEN);
        when(billMapper.selectById(500L)).thenReturn(bill);

        listener.onPaymentReceived(new DomainEvent.PaymentReceived(500L, 10L, LocalDateTime.now()));

        verify(commissionService, never()).unfreezeByOrderInTransaction(any());
        verify(serviceContractService, never()).startPerforming(any());
    }

    @Test
    void reversedFirstServiceBillRefreezesOnlySettleableBonus() {
        MktReferralOrder o = new MktReferralOrder(); o.setId(31L);
        com.zhyq.park.finance.entity.Bill bill = new com.zhyq.park.finance.entity.Bill();
        bill.setSource("mkt_service"); bill.setBillingKey("mkt_service:10:first");
        bill.setStatus(3); bill.setFeeType("保证金");
        bill.setPaidAmount(BigDecimal.ZERO);
        when(billMapper.selectById(500L)).thenReturn(bill);
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(o));

        listener.onPaymentReversed(new DomainEvent.PaymentReversed(700L, 500L, 10L, LocalDateTime.now()));

        verify(commissionService).refreezeByOrder(31L);
    }

    @Test
    void paymentWithoutContractIsIgnored() {
        listener.onPaymentReceived(new DomainEvent.PaymentReceived(500L, null, LocalDateTime.now()));
        verify(commissionService, never()).unfreezeByOrderInTransaction(any());
    }

    @Test void propertyPaymentAndPartialRentCannotUnfreezeLease() {
        var rent = rentBill(500L, "1", 4);
        var property = rentBill(501L, "1", 4); property.setFeeType("物业费");
        when(billMapper.selectList(any())).thenReturn(List.of(rent));
        when(billMapper.selectById(501L)).thenReturn(property);
        listener.onPaymentReceived(new DomainEvent.PaymentReceived(501L,10L,LocalDateTime.now()));
        when(billMapper.selectById(500L)).thenReturn(rent);
        listener.onPaymentReceived(new DomainEvent.PaymentReceived(500L,10L,LocalDateTime.now()));
        verify(commissionService, never()).unfreezeByOrderInTransaction(any());
    }

    @Test void writeOffSettledRentAndUnpaidSecondRoomDoNotMeetCashGate() {
        var first = rentBill(500L, "1000", 5);
        var room2 = rentBill(501L, "1", 5); // settled through write-off, only one yuan actual receipt
        when(billMapper.selectById(500L)).thenReturn(first);
        when(billMapper.selectList(any())).thenReturn(List.of(first, room2));
        listener.onPaymentReceived(new DomainEvent.PaymentReceived(500L,10L,LocalDateTime.now()));
        verify(commissionService, never()).unfreezeByOrderInTransaction(any());
    }

    @Test void laterRentPeriodDoesNotUnfreezeLease() {
        var first = rentBill(500L, "1", 4); var later = rentBill(501L, "1000", 5);
        later.setPeriodStart(first.getPeriodStart().plusMonths(1));
        when(billMapper.selectById(501L)).thenReturn(later); when(billMapper.selectList(any())).thenReturn(List.of(first,later));
        listener.onPaymentReceived(new DomainEvent.PaymentReceived(501L,10L,LocalDateTime.now()));
        verify(commissionService, never()).unfreezeByOrderInTransaction(any());
    }

    @Test void earlyTerminationClawsBackInSameTransactionAndPropagatesFailure() {
        var c = contract(10L,7L,"10","10","A"); c.setStartDate(java.time.LocalDate.of(2026,1,1)); c.setTerminateDate(c.getStartDate().plusDays(90));
        when(contractMapper.selectById(10L)).thenReturn(c); when(bizSettings.getInt("marketing","lease_clawback_days",90)).thenReturn(90);
        listener.onContractTerminated(new DomainEvent.ContractTerminated(10L,"HT-1",7L,1L,LocalDateTime.now()));
        verify(commissionService).clawbackBySource(eq(1),eq(10L),any());
        org.mockito.Mockito.doThrow(new com.zhyq.park.common.exception.BizException("rollback")).when(commissionService).clawbackBySource(eq(1),eq(10L),any());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> listener.onContractTerminated(new DomainEvent.ContractTerminated(10L,"HT-1",7L,1L,LocalDateTime.now())))
                .hasMessage("rollback");
    }

    @Test void terminationOutsideWindowDoesNotClawBackSettledCommissions() {
        var c = contract(10L,7L,"10","10","A"); c.setStartDate(java.time.LocalDate.of(2026,1,1)); c.setTerminateDate(c.getStartDate().plusDays(91));
        when(contractMapper.selectById(10L)).thenReturn(c); when(bizSettings.getInt("marketing","lease_clawback_days",90)).thenReturn(90);
        listener.onContractTerminated(new DomainEvent.ContractTerminated(10L,"HT-1",7L,1L,LocalDateTime.now()));
        verify(commissionService, never()).clawbackBySource(org.mockito.ArgumentMatchers.anyInt(),any(),any());
        verify(commissionService).voidUnsettledBySource(eq(1),eq(10L),any());
    }

    @Test void partialReversalBelowFirstRentAmountSuspendsEntitlement() {
        var bill = rentBill(500L, "999", 4); var order = new MktReferralOrder(); order.setId(31L);
        when(billMapper.selectById(500L)).thenReturn(bill); when(billMapper.selectList(any())).thenReturn(List.of(bill));
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        listener.onPaymentReversed(new DomainEvent.PaymentReversed(700L,500L,10L,LocalDateTime.now()));
        verify(commissionService).refreezeByOrder(31L);
    }
    @Test void reversalThatStillLeavesFullRentDoesNotSuspend() {
        var bill = rentBill(500L, "1000", 5);
        when(billMapper.selectById(500L)).thenReturn(bill); when(billMapper.selectList(any())).thenReturn(List.of(bill));
        listener.onPaymentReversed(new DomainEvent.PaymentReversed(700L,500L,10L,LocalDateTime.now()));
        verify(commissionService, never()).refreezeByOrder(any());
    }

    private static com.zhyq.park.finance.entity.Bill rentBill(Long id, String paid, int status) {
        var b = new com.zhyq.park.finance.entity.Bill(); b.setId(id); b.setContractId(10L); b.setDirection(1); b.setFeeType("租金");
        b.setSource("合同计划"); b.setAmount(new BigDecimal("1000")); b.setPaidAmount(new BigDecimal(paid)); b.setStatus(status);
        b.setPeriodStart(java.time.LocalDate.of(2026,9,1)); return b;
    }

    private static Contract contract(Long id, Long tenantRefId, String price, String area, String grade) {
        Contract c = new Contract();
        c.setId(id); c.setTenantRefId(tenantRefId);
        c.setRentPrice(new BigDecimal(price)); c.setRentArea(new BigDecimal(area)); c.setGrade(grade);
        return c;
    }

    private static BizTenant tenant(String phone) {
        BizTenant t = new BizTenant(); t.setPhone(phone); return t;
    }

    private static Customer customer(Long id, Long referrerId, String grade) {
        Customer c = new Customer(); c.setId(id); c.setReferrerId(referrerId); c.setGrade(grade); return c;
    }

    private static MktCustomerGrade grade(String code, String months) {
        MktCustomerGrade g = new MktCustomerGrade(); g.setCode(code); g.setLeaseCommissionMonths(new BigDecimal(months)); return g;
    }
}
