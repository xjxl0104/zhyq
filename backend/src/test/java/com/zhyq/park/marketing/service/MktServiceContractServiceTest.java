package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.marketing.entity.MktCustomerGrade;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.mapper.MktCustomerGradeMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractVersionMapper;
import com.zhyq.park.marketing.service.MktCommissionService.CommissionEvent;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 服务合同状态机:每条迁移一个合法前态 case + 一个非法前态 case(条件更新 0 行 → BizException)。 */
@ExtendWith(MockitoExtension.class)
class MktServiceContractServiceTest {

    @Mock MktServiceContractMapper contractMapper;
    @Mock MktServiceContractVersionMapper versionMapper;
    @Mock MktCustomerGradeMapper gradeMapper;
    @Mock CustomerMapper customerMapper;
    @Mock BillMapper billMapper;
    @Mock MktCommissionService commissionService;
    @Mock MktLockService lockService;
    @Mock MktAuditService auditService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock MktCustomerAssignmentService assignmentService;

    MktServiceContractService service;

    @BeforeAll
    static void initMp() {
        MapperBuilderAssistant a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(a, MktServiceContract.class);
        TableInfoHelper.initTableInfo(a, Bill.class);
        TableInfoHelper.initTableInfo(a, com.zhyq.park.marketing.entity.MktServiceContractVersion.class);
    }

    @BeforeEach
    void setUp() {
        service = new MktServiceContractService(contractMapper, versionMapper, gradeMapper, customerMapper,
                billMapper, commissionService, lockService, auditService, eventPublisher, assignmentService);
        lenient().when(customerMapper.selectForUpdate(any())).thenReturn(new Customer());
        var warehouse = new com.zhyq.park.marketing.entity.MktWarehouse(); warehouse.setFeeModel("{\"perOrder\":5}");
        lenient().when(assignmentService.requireAvailableWarehouse(any(), any())).thenReturn(warehouse);
        var grade = new MktCustomerGrade(); grade.setErpTotalRate(new BigDecimal("8"));
        lenient().when(gradeMapper.selectOne(any(Wrapper.class))).thenReturn(grade);
        lenient().when(contractMapper.selectById(1L)).thenReturn(contract(1L, MktServiceContractService.ST_DRAFT, 1));
    }

    // ---- 草稿 ----
    @Test
    void createDraftSnapshotsCustomerSignModeGradeAndReferrer() {
        Customer c = new Customer(); c.setId(5L); c.setSignMode(2); c.setGrade("B"); c.setReferrerId(99L);
        when(assignmentService.validateContractWarehouse(any())).thenReturn(c);
        MktServiceContract d = contract(null, 1, 1); d.setSignMode(null);

        service.createDraft(d);

        assertThat(d.getStatus()).isEqualTo(MktServiceContractService.ST_DRAFT);
        assertThat(d.getContractNo()).startsWith("CS-");
        assertThat(d.getSignMode()).isEqualTo(2);
        assertThat(d.getGrade()).isEqualTo("B");
        assertThat(d.getPartnerId()).isEqualTo(99L);
        verify(contractMapper).insert(d);
    }

    @Test
    void createDraftIgnoresClientSuppliedPartnerAndGrade() {
        // B9:即使调用方(或未来某个新入口)传入 partnerId/grade,也必须以客户档案为准,防越权改佣金归属
        Customer c = new Customer(); c.setId(5L); c.setGrade("C"); c.setReferrerId(11L);
        when(assignmentService.validateContractWarehouse(any())).thenReturn(c);
        MktServiceContract d = contract(null, 1, 1);
        d.setPartnerId(999L);   // 伪造:想把佣金记到别人头上
        d.setGrade("A");        // 伪造:想抬高比例

        service.createDraft(d);

        assertThat(d.getPartnerId()).isEqualTo(11L);
        assertThat(d.getGrade()).isEqualTo("C");
    }

    @Test
    void createDraftDefaultsToParkSignMode() {
        Customer c = new Customer(); c.setId(5L);
        when(assignmentService.validateContractWarehouse(any())).thenReturn(c);
        MktServiceContract d = contract(null, 1, 1); d.setSignMode(null);
        service.createDraft(d);
        assertThat(d.getSignMode()).isEqualTo(MktServiceContractService.SIGN_MODE_PARK);
    }

    // ---- 提交 / 审核 ----
    @Test
    void submitMovesDraftToPendingAudit() {
        updated(1);
        service.submit(1L);
        verify(auditService).log(eq("contract.submit"), eq("service_contract"), eq(1L), isNull());
    }

    @Test
    void submitFromWrongStateThrows() {
        updated(0);
        assertThatThrownBy(() -> service.submit(1L)).isInstanceOf(BizException.class).hasMessageContaining("状态已变化");
    }

    @Test
    void auditPassMovesToPendingSign() {
        updated(1);
        service.audit(1L, true, null);
        verify(auditService).log(eq("contract.audit.pass"), any(), eq(1L), isNull());
    }

    @Test
    void auditRejectRequiresReasonAndReturnsToDraft() {
        assertThatThrownBy(() -> service.audit(1L, false, "")).isInstanceOf(BizException.class).hasMessageContaining("原因");
        updated(1);
        service.audit(1L, false, "单价表缺失");
        verify(auditService).log(eq("contract.audit.reject"), any(), eq(1L), eq("单价表缺失"));
    }

    // ---- 生效 ----
    @Test
    void signOfflineOnParkContractPublishesEventCreatesBonusAndFirstBill() {
        MktServiceContract c = contract(1L, MktServiceContractService.ST_PENDING_SIGN, 1);
        c.setPartnerId(99L); c.setGrade("A"); c.setDeposit(new BigDecimal("5000")); c.setStartDate(LocalDate.of(2026, 10, 1));
        when(contractMapper.selectById(1L)).thenReturn(c);
        updated(1);
        MktCustomerGrade g = new MktCustomerGrade(); g.setCode("A"); g.setContractBonus(new BigDecimal("2000")); g.setErpTotalRate(new BigDecimal("8"));
        when(gradeMapper.selectOne(any(Wrapper.class))).thenReturn(g);

        service.signOffline(1L, "[12]");

        verify(lockService).markDeal(5L, 99L);
        verify(eventPublisher).publishEvent(any(DomainEvent.ServiceContractEffective.class));
        ArgumentCaptor<CommissionEvent> ev = ArgumentCaptor.forClass(CommissionEvent.class);
        verify(commissionService).createAndSplitInTransaction(ev.capture());
        assertThat(ev.getValue().sourceType()).isEqualTo(MktCommissionService.SOURCE_CONTRACT_BONUS);
        assertThat(ev.getValue().poolAmount()).isEqualByComparingTo("2000");
        assertThat(ev.getValue().unfreezeAt()).isNull();
        ArgumentCaptor<Bill> bill = ArgumentCaptor.forClass(Bill.class);
        verify(billMapper).insert(bill.capture());
        assertThat(bill.getValue().getBillingKey()).isEqualTo("mkt_service:1:first");
        assertThat(bill.getValue().getAmount()).isEqualByComparingTo("5000");
        assertThat(bill.getValue().getFeeType()).isEqualTo("保证金");
        assertThat(bill.getValue().getSource()).isEqualTo("mkt_service");
    }

    @Test
    void signOfflineOnDirectContractDoesNotCreateCustomerBill() {
        MktServiceContract c = contract(1L, MktServiceContractService.ST_PENDING_SIGN, 2);
        when(contractMapper.selectById(1L)).thenReturn(c);
        updated(1);

        service.signOffline(1L, "[12]");

        verify(billMapper, never()).insert(any(Bill.class));
    }

    @Test
    void zeroBonusGradeCreatesNoCommission() {
        MktServiceContract c = contract(1L, MktServiceContractService.ST_PENDING_SIGN, 1);
        c.setPartnerId(99L); c.setGrade("D");
        when(contractMapper.selectById(1L)).thenReturn(c);
        updated(1);
        MktCustomerGrade g = new MktCustomerGrade(); g.setCode("D"); g.setContractBonus(BigDecimal.ZERO); g.setErpTotalRate(new BigDecimal("8"));
        when(gradeMapper.selectOne(any(Wrapper.class))).thenReturn(g);

        service.signOffline(1L, "[12]");

        verify(commissionService, never()).createAndSplitInTransaction(any());
    }

    @Test
    void effectDirectRejectsParkContract() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, MktServiceContractService.ST_DRAFT, 1));
        assertThatThrownBy(() -> service.effectDirect(1L)).isInstanceOf(BizException.class).hasMessageContaining("直签");
    }

    @Test
    void effectDirectFromDraftIsRejected() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, MktServiceContractService.ST_DRAFT, 2));
        assertThatThrownBy(() -> service.effectDirect(1L)).isInstanceOf(BizException.class).hasMessageContaining("先提交审核");
        verify(contractMapper, never()).update(isNull(), any(Wrapper.class));
        verify(commissionService, never()).createAndSplitInTransaction(any());
    }

    @Test
    void effectDirectRequiresSignedAttachmentThenActivatesFromAudit() {
        MktServiceContract c = contract(1L, MktServiceContractService.ST_PENDING_AUDIT, 2);
        c.setFiles("[]");
        when(contractMapper.selectById(1L)).thenReturn(c);
        assertThatThrownBy(() -> service.effectDirect(1L)).isInstanceOf(BizException.class).hasMessageContaining("附件");
        c.setFiles("[{\"id\":12,\"name\":\"签署件.pdf\",\"size\":321,\"ext\":\"pdf\"}]");
        updated(1);
        service.effectDirect(1L);
        verify(assignmentService).contractEffective(c);
        verify(lockService).markDeal(5L, null);
    }

    @Test
    void effectiveRetryDoesNotCreateAnotherBillOrCommission() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, MktServiceContractService.ST_EFFECTIVE, 2));
        service.effectDirect(1L);
        verify(contractMapper, never()).update(isNull(), any(Wrapper.class));
        verify(commissionService, never()).createAndSplitInTransaction(any());
        verify(billMapper, never()).insert(any(Bill.class));
    }

    @Test
    void unitPricedContractDoesNotCreateZeroAmountPlaceholderBill() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, MktServiceContractService.ST_PENDING_SIGN, 1));
        updated(1);
        service.signOffline(1L, "[12]");
        verify(billMapper, never()).insert(any(Bill.class));
    }

    @Test
    void monthlyContractCreatesRealFirstMonthRent() {
        MktServiceContract c = contract(1L, MktServiceContractService.ST_PENDING_SIGN, 1);
        c.setFeeModel(4); c.setPriceTable("{\"monthly\":1800}");
        when(contractMapper.selectById(1L)).thenReturn(c);
        updated(1);
        service.signOffline(1L, "[12]");
        ArgumentCaptor<Bill> bill = ArgumentCaptor.forClass(Bill.class);
        verify(billMapper).insert(bill.capture());
        assertThat(bill.getValue().getAmount()).isEqualByComparingTo("1800");
        assertThat(bill.getValue().getFeeType()).isEqualTo("租金");
    }

    @Test
    void invalidTermsAndAttachmentCannotActivate() {
        MktServiceContract c = contract(1L, MktServiceContractService.ST_PENDING_SIGN, 1);
        when(contractMapper.selectById(1L)).thenReturn(c);
        assertThatThrownBy(() -> service.signOffline(1L, "[]")).isInstanceOf(BizException.class).hasMessageContaining("附件");
        assertThatThrownBy(() -> service.signOffline(1L, "[{\"id\":-1}]")).isInstanceOf(BizException.class).hasMessageContaining("编号");
        c.setPriceTable("{\"perOrder\":-2}");
        assertThatThrownBy(() -> service.signOffline(1L, "[12]")).isInstanceOf(BizException.class).hasMessageContaining("非负金额");
        verify(contractMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void editingDraftCannotChangeAssignedWarehouse() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, MktServiceContractService.ST_DRAFT, 1));
        MktServiceContract edited = contract(1L, 1, 1); edited.setWarehouseId(999L);
        assertThatThrownBy(() -> service.updateDraft(edited)).isInstanceOf(BizException.class).hasMessageContaining("不可");
        verify(contractMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void lastContractTerminationReleasesDealAfterCustomerSync() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, MktServiceContractService.ST_PERFORMING, 1));
        when(assignmentService.contractClosed(5L)).thenReturn(true);
        updated(1);
        service.terminate(1L, "正常终止", 90);
        verify(lockService).releaseDeal(5L, "合同终止");
    }

    // ---- 履约 / 变更 / 到期 / 续签 ----
    @Test
    void startPerformingFromEffective() {
        updated(1);
        service.startPerforming(1L);
        verify(auditService).log(eq("contract.perform"), any(), eq(1L), isNull());
    }

    @Test
    void startingAmendmentKeepsCurrentTermsUntilSignedCompletion() {
        MktServiceContract c = contract(1L, MktServiceContractService.ST_PERFORMING, 1);
        when(contractMapper.selectById(1L)).thenReturn(c);
        updated(1);
        service.amend(1L, "换仓");
        verify(versionMapper, never()).insert(any(com.zhyq.park.marketing.entity.MktServiceContractVersion.class));
    }

    @Test
    void amendDoneBumpsVersion() {
        MktServiceContract c = contract(1L, MktServiceContractService.ST_AMENDING, 1);
        c.setContractVersion(2);
        when(contractMapper.selectForUpdate(1L)).thenReturn(c);
        updated(1);
        service.amendDone(1L, new MktServiceContractService.Amendment("{\"perOrder\":12}", LocalDate.now().plusYears(1), 3, "[12]", "涨价", LocalDate.now()));
        verify(auditService).log(eq("contract.amend.done"), any(), eq(1L), org.mockito.ArgumentMatchers.contains("版本 3"), any(), any());
        verify(versionMapper).insert(any(com.zhyq.park.marketing.entity.MktServiceContractVersion.class));
    }

    @Test void amendmentCannotRewriteAnAlreadyBilledPeriod() {
        MktServiceContract c = contract(1L, MktServiceContractService.ST_AMENDING, 1);
        when(contractMapper.selectForUpdate(1L)).thenReturn(c);
        when(billMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        assertThatThrownBy(() -> service.amendDone(1L, new MktServiceContractService.Amendment(
                "{\"perOrder\":12}", LocalDate.now().plusYears(1), 3, "[12]", "", LocalDate.now())))
                .isInstanceOf(BizException.class).hasMessageContaining("已生成账单");
        verify(contractMapper, never()).update(any(), any(Wrapper.class));
        verify(versionMapper, never()).insert(any(com.zhyq.park.marketing.entity.MktServiceContractVersion.class));
    }

    @Test void amendmentCannotApplyBackdatedTerms() {
        when(contractMapper.selectForUpdate(1L)).thenReturn(contract(1L, MktServiceContractService.ST_AMENDING, 1));
        assertThatThrownBy(() -> service.amendDone(1L, new MktServiceContractService.Amendment(
                "{\"perOrder\":12}", LocalDate.now().plusYears(1), 3, "[12]", "", LocalDate.now().minusDays(1))))
                .isInstanceOf(BizException.class).hasMessageContaining("不得早于今天");
        verify(contractMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    void renewRejectsBadDatesAndWrongState() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, MktServiceContractService.ST_EXPIRED, 1));
        assertThatThrownBy(() -> service.renew(1L, LocalDate.of(2027, 1, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(BizException.class).hasMessageContaining("日期");
        updated(0);
        assertThatThrownBy(() -> service.renew(1L, LocalDate.of(2027, 1, 1), LocalDate.of(2028, 1, 1)))
                .isInstanceOf(BizException.class).hasMessageContaining("状态已变化");
    }

    // ---- 终止 / 作废 ----
    @Test
    void terminateWithinClawbackWindowClawsBackBonus() {
        MktServiceContract c = contract(1L, MktServiceContractService.ST_PERFORMING, 1);
        c.setEffectiveAt(LocalDateTime.now().minusDays(10));
        when(contractMapper.selectById(1L)).thenReturn(c);
        updated(1);

        service.terminate(1L, "客户跑路", 90);

        verify(commissionService).clawbackBySource(eq(MktCommissionService.SOURCE_CONTRACT_BONUS), eq(1L), anyString());
    }

    @Test
    void terminateAfterClawbackWindowStillVoidsUnsettledCommission() {
        MktServiceContract c = contract(1L, MktServiceContractService.ST_PERFORMING, 1);
        c.setEffectiveAt(LocalDateTime.now().minusDays(120));
        when(contractMapper.selectById(1L)).thenReturn(c);
        updated(1);

        service.terminate(1L, "到期不续", 90);

        verify(commissionService).voidUnsettledBySource(eq(MktCommissionService.SOURCE_CONTRACT_BONUS), eq(1L), anyString());
        verify(commissionService, never()).clawbackBySource(anyInt(), any(), any());
    }

    @Test
    void terminateRequiresReason() {
        assertThatThrownBy(() -> service.terminate(1L, " ", 90)).isInstanceOf(BizException.class);
    }

    @Test
    void voidOnlyBeforeEffective() {
        updated(0);
        assertThatThrownBy(() -> service.voidContract(1L, "客户放弃")).isInstanceOf(BizException.class);
        updated(1);
        service.voidContract(1L, "客户放弃");
        verify(auditService).log(eq("contract.void"), any(), eq(1L), eq("客户放弃"));
    }

    // ---- helpers ----
    private void updated(int rows) {
        lenient().when(contractMapper.update(isNull(), any(Wrapper.class))).thenReturn(rows);
    }

    private static MktServiceContract contract(Long id, int status, int signMode) {
        MktServiceContract c = new MktServiceContract();
        c.setId(id); c.setStatus(status); c.setSignMode(signMode); c.setContractNo("CS-202609-TEST");
        c.setCustomerId(5L); c.setWarehouseId(7L); c.setContractVersion(1);
        c.setServiceType(2); c.setFeeModel(2); c.setPriceTable("{\"perOrder\":10}");
        c.setStartDate(LocalDate.of(2026, 1, 1)); c.setEndDate(LocalDate.of(2026, 12, 31)); c.setPayCycle(3);
        c.setFiles("[12]");
        return c;
    }
}
