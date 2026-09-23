package com.zhyq.park.marketing.service;

import com.zhyq.park.marketing.entity.MktPromoter;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.marketing.engine.LadderResolver;
import com.zhyq.park.marketing.entity.MktCustomerLock;
import com.zhyq.park.marketing.entity.MktPromoterCommission;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseOnboarding;
import com.zhyq.park.marketing.entity.MktWithdrawal;
import com.zhyq.park.marketing.mapper.MktCustomerGradeMapper;
import com.zhyq.park.marketing.mapper.MktCustomerLockMapper;
import com.zhyq.park.marketing.mapper.MktPositionMapper;
import com.zhyq.park.marketing.mapper.MktPromoterCommissionMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktReferralOrderMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractVersionMapper;
import com.zhyq.park.marketing.mapper.MktSettleBatchMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseOnboardingMapper;
import com.zhyq.park.marketing.mapper.MktWithdrawalMapper;
import com.zhyq.park.marketing.support.WrapperAssert;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.zhyq.park.marketing.service.MktCommissionService.C_FROZEN;
import static com.zhyq.park.marketing.service.MktCommissionService.C_SETTLEABLE;
import static com.zhyq.park.marketing.service.MktCommissionService.C_SETTLED;
import static com.zhyq.park.marketing.service.MktCommissionService.C_VOID;
import static com.zhyq.park.marketing.service.MktLockService.LS_DEAL;
import static com.zhyq.park.marketing.service.MktLockService.LS_LOCKED;
import static com.zhyq.park.marketing.service.MktLockService.LS_PRELOCK;
import static com.zhyq.park.marketing.service.MktLockService.LS_RELEASED;
import static com.zhyq.park.marketing.service.MktServiceContractService.ST_AMENDING;
import static com.zhyq.park.marketing.service.MktServiceContractService.ST_DRAFT;
import static com.zhyq.park.marketing.service.MktServiceContractService.ST_EFFECTIVE;
import static com.zhyq.park.marketing.service.MktServiceContractService.ST_EXPIRED;
import static com.zhyq.park.marketing.service.MktServiceContractService.ST_PENDING_AUDIT;
import static com.zhyq.park.marketing.service.MktServiceContractService.ST_PENDING_SIGN;
import static com.zhyq.park.marketing.service.MktServiceContractService.ST_PERFORMING;
import static com.zhyq.park.marketing.service.MktServiceContractService.ST_TERMINATED;
import static com.zhyq.park.marketing.service.MktServiceContractService.ST_VOID;
import static com.zhyq.park.marketing.service.MktWarehouseOnboardingService.ERP_LIVE;
import static com.zhyq.park.marketing.service.MktWarehouseOnboardingService.ERP_SANDBOX;
import static com.zhyq.park.marketing.service.MktWarehouseOnboardingService.JS_APPLIED;
import static com.zhyq.park.marketing.service.MktWarehouseOnboardingService.JS_ERP_CONNECTING;
import static com.zhyq.park.marketing.service.MktWarehouseOnboardingService.JS_EXITED;
import static com.zhyq.park.marketing.service.MktWarehouseOnboardingService.JS_ONLINE;
import static com.zhyq.park.marketing.service.MktWarehouseOnboardingService.JS_PAUSED;
import static com.zhyq.park.marketing.service.MktWarehouseOnboardingService.JS_PENDING_AGREEMENT;
import static com.zhyq.park.marketing.service.MktWarehouseOnboardingService.JS_QUALIFYING;
import static com.zhyq.park.marketing.service.MktWithdrawalService.WS_APPROVED;
import static com.zhyq.park.marketing.service.MktWithdrawalService.WS_PAID;
import static com.zhyq.park.marketing.service.MktWithdrawalService.WS_PENDING;
import static com.zhyq.park.marketing.service.MktWithdrawalService.WS_REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * 五个状态机的「条件更新前态集合」守卫。
 *
 * <p>现有 *ServiceTest 只 mock 了 {@code update} 的返回值,前态集合写宽/写窄一个都抓不到。
 * 这里把每次 update 捕获下来,用 {@link WrapperAssert} 还原 WHERE / SET,按规范
 * PARK-MKT-001 §2.4(合同)/ §2.5(加盟)/ §2.2a(锁客)/ §2.8(佣金流水)/ §2.9(提现)逐条断言。
 * 断言的是规范,不是现行实现 —— 实现比规范宽的地方这里应该红,红了要么改实现要么改规范并同步这里。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MktTransitionGuardTest {

    @BeforeAll
    static void initMp() {
        MapperBuilderAssistant a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> c : List.of(Customer.class, MktServiceContract.class, MktCustomerLock.class, MktWarehouse.class,
                MktWarehouseOnboarding.class, MktPromoterCommission.class, MktWithdrawal.class)) {
            TableInfoHelper.initTableInfo(a, c);
        }
    }

    // ============================================================ 合同 §2.4
    @Nested
    class ServiceContract {
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
        ArgumentCaptor<Wrapper<MktServiceContract>> captor;
        MktServiceContractService service;

        @BeforeEach
        void setUp() {
            service = new MktServiceContractService(contractMapper, versionMapper, gradeMapper, customerMapper,
                    billMapper, commissionService, lockService, auditService, eventPublisher, assignmentService);
            captor = ArgumentCaptor.forClass(Wrapper.class);
            when(contractMapper.update(isNull(), captor.capture())).thenReturn(1);
            MktServiceContract c = new MktServiceContract();
            c.setId(1L); c.setStatus(ST_PENDING_SIGN); c.setCustomerId(5L); c.setContractVersion(1);
            c.setEffectiveAt(LocalDateTime.now().minusDays(1)); c.setSignMode(1);
            c.setWarehouseId(7L); c.setServiceType(2); c.setFeeModel(2); c.setPayCycle(3); c.setPriceTable("{\"perOrder\":10}");
            c.setStartDate(LocalDate.of(2025, 1, 1)); c.setEndDate(LocalDate.now().minusDays(1));
            when(customerMapper.selectForUpdate(anyLong())).thenReturn(new Customer());
            var warehouse = new MktWarehouse(); warehouse.setFeeModel("{\"perOrder\":5}");
            when(assignmentService.requireAvailableWarehouse(any(), any())).thenReturn(warehouse);
            var grade = new com.zhyq.park.marketing.entity.MktCustomerGrade(); grade.setErpTotalRate(new java.math.BigDecimal("8"));
            when(gradeMapper.selectOne(any(Wrapper.class))).thenReturn(grade);
            when(contractMapper.selectById(anyLong())).thenReturn(c);
        }

        private WrapperAssert last() {
            return WrapperAssert.of((LambdaUpdateWrapper<?>) captor.getValue());
        }

        @Test @DisplayName("草稿 → 待审核")
        void submit() { service.submit(1L); last().isStatusTransition("status", ST_PENDING_AUDIT, ST_DRAFT).hasWhereId(1L); }

        @Test @DisplayName("待审核 → 待客户签(通过)")
        void auditPass() { service.audit(1L, true, null); last().isStatusTransition("status", ST_PENDING_SIGN, ST_PENDING_AUDIT); }

        @Test @DisplayName("待审核 → 草稿(驳回)")
        void auditReject() { service.audit(1L, false, "缺资质"); last().isStatusTransition("status", ST_DRAFT, ST_PENDING_AUDIT); }

        @Test @DisplayName("待客户签 → 已生效(线下签署)")
        void signOffline() { service.signOffline(1L, "[1]"); last().isStatusTransition("status", ST_EFFECTIVE, ST_PENDING_SIGN); }

        @Test @DisplayName("已生效 → 履约中")
        void perform() { service.startPerforming(1L); last().isStatusTransition("status", ST_PERFORMING, ST_EFFECTIVE); }

        @Test @DisplayName("履约中 → 变更中")
        void amend() { service.amend(1L, "换仓"); last().isStatusTransition("status", ST_AMENDING, ST_PERFORMING); }

        @Test @DisplayName("变更中 → 履约中(新版本生效)")
        void amendDone() { service.amendDone(1L); last().isStatusTransition("status", ST_PERFORMING, ST_AMENDING); }

        @Test @DisplayName("履约中 → 到期")
        void expire() { service.expire(1L); last().isStatusTransition("status", ST_EXPIRED, ST_EFFECTIVE, ST_PERFORMING, ST_AMENDING); }

        @Test @DisplayName("到期 → 履约中(续签)")
        void renew() {
            service.renew(1L, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
            last().isStatusTransition("status", ST_PERFORMING, ST_EXPIRED);
            assertThat(last().setValue("contract_version")).isEqualTo("2");
        }

        @Test @DisplayName("终止可从已生效、履约中或变更中出发，不能从到期出发")
        void terminateOnlyFromEffectiveOrPerforming() {
            service.terminate(1L, "退租", 90);
            last().isStatusTransition("status", ST_TERMINATED, ST_EFFECTIVE, ST_PERFORMING, ST_AMENDING);
        }

        @Test @DisplayName("§2.4 作废只能从 待客户签 出发 —— 草稿、待审核不是合法前态")
        void voidOnlyFromPendingSign() {
            service.voidContract(1L, "客户放弃");
            last().isStatusTransition("status", ST_VOID, ST_PENDING_SIGN);
        }
    }

    // ============================================================ 锁客 §2.2a
    @Nested
    class CustomerLock {
        @Mock MktCustomerLockMapper lockMapper;
        @Mock MktPromoterMapper promoterMapper;
        @Mock MktPositionMapper positionMapper;
        @Mock BizSettings bizSettings;
        @Mock MktAuditService auditService;
        @Mock ApplicationEventPublisher eventPublisher;
        @Mock CustomerMapper customerMapper;
        @Mock MktCustomerAssignmentService assignmentService;
        ArgumentCaptor<Wrapper<MktCustomerLock>> captor;
        MktLockService service;

        @BeforeEach
        void setUp() {
            service = new MktLockService(lockMapper, promoterMapper, positionMapper, bizSettings, auditService, eventPublisher, customerMapper, assignmentService);
            Customer customer = new Customer(); customer.setId(5L); customer.setReferrerId(9L);
            when(customerMapper.selectForUpdate(5L)).thenReturn(customer);
            captor = ArgumentCaptor.forClass(Wrapper.class);
            when(lockMapper.update(isNull(), captor.capture())).thenReturn(1);
            when(bizSettings.getInt(anyString(), anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(2));
            MktCustomerLock l = new MktCustomerLock();
            l.setId(7L); l.setCustomerId(5L); l.setPromoterId(9L); l.setStatus(LS_LOCKED); l.setExtendedCount(0);
            l.setLockUntil(LocalDateTime.now().plusDays(100));
            when(lockMapper.selectById(anyLong())).thenReturn(l);
            when(lockMapper.selectOne(any())).thenReturn(l);
        }

        private WrapperAssert last() {
            return WrapperAssert.of((LambdaUpdateWrapper<?>) captor.getValue());
        }

        @Test @DisplayName("预锁 → 有效锁定(专员确认)")
        void confirm() { service.confirm(7L, "专员A"); last().isStatusTransition("status", LS_LOCKED, LS_PRELOCK).hasWhereId(7L); }

        @Test @DisplayName("延期只对 有效锁定 生效,状态不变,extended_count 做 CAS")
        void extend() {
            service.extend(7L, "近 60 天有到访");
            assertThat(last().whereIn("status")).containsExactly(LS_LOCKED);
            assertThat(last().whereEq("extended_count")).isEqualTo("0");
            assertThat(last().setValue("status")).isNull();
            assertThat(last().setValue("extended_count")).isEqualTo("1");
        }

        @Test @DisplayName("§2.2a 成交只能从 有效锁定 出发 —— 预锁未经专员确认不能直接成交")
        void markDealOnlyFromLocked() {
            service.markDeal(5L);
            last().isStatusTransition("status", LS_DEAL, LS_LOCKED);
        }

        @Test @DisplayName("释放可从 预锁 / 有效锁定 出发")
        void release() { service.release(7L, "客户拒绝", "运营"); last().isStatusTransition("status", LS_RELEASED, LS_PRELOCK, LS_LOCKED); }

        @Test @DisplayName("到期释放按每条记录当前状态做 CAS,不会把已成交的锁释放掉")
        void expireUsesRowStatusAsGuard() {
            MktCustomerLock pre = new MktCustomerLock(); pre.setId(1L); pre.setStatus(LS_PRELOCK);
            MktCustomerLock locked = new MktCustomerLock(); locked.setId(2L); locked.setStatus(LS_LOCKED);
            when(lockMapper.selectList(any())).thenReturn(List.of(pre, locked));

            service.expire(LocalDateTime.now());

            List<Wrapper<MktCustomerLock>> all = captor.getAllValues();
            assertThat(all).hasSize(2);
            WrapperAssert.of((LambdaUpdateWrapper<?>) all.get(0)).isStatusTransition("status", LS_RELEASED, LS_PRELOCK).hasWhereId(1L);
            WrapperAssert.of((LambdaUpdateWrapper<?>) all.get(1)).isStatusTransition("status", LS_RELEASED, LS_LOCKED).hasWhereId(2L);
        }
    }

    // ============================================================ 加盟 §2.5
    @Nested
    class WarehouseOnboarding {
        @Mock MktWarehouseMapper warehouseMapper;
        @Mock MktWarehouseOnboardingMapper stepMapper;
        @Mock MktAuditService auditService;
        ArgumentCaptor<Wrapper<MktWarehouse>> captor;
        MktWarehouseOnboardingService service;

        @BeforeEach
        void setUp() {
            service = new MktWarehouseOnboardingService(warehouseMapper, stepMapper, auditService);
            captor = ArgumentCaptor.forClass(Wrapper.class);
            when(warehouseMapper.update(isNull(), captor.capture())).thenReturn(1);
            MktWarehouse w = new MktWarehouse(); w.setId(3L); w.setJoinStatus(JS_PAUSED); w.setErpStatus(ERP_LIVE); w.setName("测试仓"); w.setContact("联系人"); w.setPhone("13800000000"); w.setRegion("杭州"); w.setAddress("杭州园区");
            when(warehouseMapper.selectById(anyLong())).thenReturn(w);
        }

        private WrapperAssert first() {
            return WrapperAssert.of((LambdaUpdateWrapper<?>) captor.getAllValues().get(0));
        }

        @Test @DisplayName("资质审核 → ERP 对接中(通过)")
        void qualifyPass() { service.passQualification(3L, 1); first().isStatusTransition("join_status", JS_ERP_CONNECTING, JS_QUALIFYING); assertThat(first().whereEq("version")).isEqualTo("1"); }

        @Test @DisplayName("资质审核 → 申请(驳回,可重提)")
        void qualifyReject() { service.rejectQualification(3L, "资质不全"); first().isStatusTransition("join_status", JS_APPLIED, JS_QUALIFYING); }

        @Test @DisplayName("人工订单模式 → 待签协议,ERP 保持未连接")
        void erpMarked() {
            service.useManualOrders(3L);
            first().isStatusTransition("join_status", JS_PENDING_AGREEMENT, JS_ERP_CONNECTING);
            assertThat(first().setValue("erp_status")).isEqualTo(String.valueOf(MktWarehouseOnboardingService.ERP_NONE));
        }

        @Test @DisplayName("待签协议 → 已上线,ERP 切正式")
        void signAgreement() {
            service.signAgreement(3L, "file:1");
            first().isStatusTransition("join_status", JS_ONLINE, JS_PENDING_AGREEMENT);
            assertThat(first().setValue("erp_status")).isEqualTo(String.valueOf(ERP_LIVE));
        }

        @Test @DisplayName("已上线 → 暂停")
        void pause() { service.pause(3L, "对账差异"); first().isStatusTransition("join_status", JS_PAUSED, JS_ONLINE); }

        @Test @DisplayName("暂停 → 已上线(恢复)")
        void resume() { service.resume(3L); first().isStatusTransition("join_status", JS_ONLINE, JS_PAUSED); }

        @Test @DisplayName("退出可从 已上线 / 暂停 出发")
        void exit() { service.exit(3L, "不再合作"); first().isStatusTransition("join_status", JS_EXITED, JS_ONLINE, JS_PAUSED); }
    }

    // ============================================================ 佣金流水 §2.8
    @Nested
    class Commission {
        @Mock MktReferralOrderMapper orderMapper;
        @Mock MktPromoterCommissionMapper commissionMapper;
        @Mock MktSettleBatchMapper batchMapper;
        @Mock MktPromoterMapper promoterMapper;
        @Mock LadderResolver ladderResolver;
        @Mock MktAuditService auditService;
        @Mock ApplicationEventPublisher eventPublisher;
        ArgumentCaptor<Wrapper<MktPromoterCommission>> captor;
        MktCommissionService service;

        @BeforeEach
        void setUp() {
            service = new MktCommissionService(orderMapper, commissionMapper, batchMapper, promoterMapper,
                    ladderResolver, auditService, eventPublisher);
            captor = ArgumentCaptor.forClass(Wrapper.class);
            when(commissionMapper.update(isNull(), captor.capture())).thenReturn(1);
            MktPromoterCommission c = new MktPromoterCommission();
            c.setId(11L); c.setPromoterId(9L); c.setStatus(C_SETTLEABLE); c.setSign(1); c.setAmount(new BigDecimal("100.00"));
            when(commissionMapper.selectById(anyLong())).thenReturn(c);
            when(commissionMapper.selectList(any())).thenReturn(List.of(c));
        }

        private WrapperAssert last() {
            return WrapperAssert.of((LambdaUpdateWrapper<?>) captor.getValue());
        }

        @Test @DisplayName("冻结 → 可结算(按订单解冻)")
        void unfreeze() {
            MktPromoterCommission frozen = new MktPromoterCommission(); frozen.setId(11L); frozen.setStatus(C_FROZEN);
            when(commissionMapper.selectList(any())).thenReturn(List.of(frozen));
            service.unfreezeByOrder(100L);
            last().isStatusTransition("status", C_SETTLEABLE, C_FROZEN).hasWhereId(11L);
        }

        @Test @DisplayName("作废可从 冻结 / 可结算 出发")
        void voidCommission() { service.voidCommission(11L, "订单取消"); last().isStatusTransition("status", C_VOID, C_FROZEN, C_SETTLEABLE); }

        @Test @DisplayName("§2.8 结算:可结算 → 已结算,且只结正向流水(sign = 1);扣回行只能在打款时抵扣")
        void settleOnlyPositiveSettleableRows() {
            service.settle(List.of(11L), "财务");
            last().isStatusTransition("status", C_SETTLED, C_SETTLEABLE).hasWhereId(11L);
            assertThat(last().whereIn("sign")).as("结算的 WHERE 必须带 sign = 1,否则扣回行会被洗成已结算").containsExactly(1);
        }
    }

    // ============================================================ 提现 §2.9
    @Nested
    class Withdrawal {
        @Mock MktWithdrawalMapper withdrawalMapper;
        @Mock MktPromoterCommissionMapper commissionMapper;
        @Mock MktPromoterMapper promoterMapper;
        @Mock BizSettings bizSettings;
        @Mock MktAuditService auditService;
        @Mock com.zhyq.park.marketing.mapper.MktPromoterAccountMapper accounts;
        @Mock MktPaymentProofService proofs;
        ArgumentCaptor<Wrapper<MktWithdrawal>> captor;
        MktWithdrawalService service;

        @BeforeEach
        void setUp() {
            service = new MktWithdrawalService(withdrawalMapper, commissionMapper, promoterMapper, bizSettings, auditService, accounts, proofs);
            captor = ArgumentCaptor.forClass(Wrapper.class);
            when(withdrawalMapper.update(isNull(), captor.capture())).thenReturn(1);
            MktWithdrawal w = new MktWithdrawal(); w.setId(21L); w.setPromoterId(9L); w.setStatus(WS_APPROVED);
            w.setAmount(new BigDecimal("500.00"));w.setAccountNoEnc("encrypted");w.setAccountVerifiedAt(LocalDateTime.now());
            when(withdrawalMapper.selectById(anyLong())).thenReturn(w);
            when(withdrawalMapper.selectOne(any())).thenReturn(null);
        }

        private WrapperAssert first() {
            return WrapperAssert.of((LambdaUpdateWrapper<?>) captor.getAllValues().get(0));
        }

        @Test @DisplayName("待审核 → 已审核")
        void approve() { service.approve(21L, "运营"); first().isStatusTransition("status", WS_APPROVED, WS_PENDING).hasWhereId(21L); }

        @Test @DisplayName("驳回可从 待审核 / 已审核 出发")
        void reject() { service.reject(21L, "账户信息有误", "运营"); first().isStatusTransition("status", WS_REJECTED, WS_PENDING, WS_APPROVED); }

        @Test @DisplayName("已审核 → 已打款,写 pay_no")
        void pay() {
            var promoter = new MktPromoter(); promoter.setId(9L); promoter.setStatus(1);
            when(promoterMapper.selectForUpdate(9L)).thenReturn(promoter);
            MktPromoterCommission row = new MktPromoterCommission();
            row.setAmount(new BigDecimal("500.00")); row.setWithdrawalId(21L);row.setPromoterId(9L);row.setStatus(C_SETTLED);row.setSign(1);
            when(commissionMapper.update(any(),any())).thenReturn(1);
            when(commissionMapper.selectList(any())).thenReturn(List.of(row));
            service.pay(21L, "PAY-20260921-001", "file:10", "财务");
            first().isStatusTransition("status", WS_PAID, WS_APPROVED).hasWhereId(21L);
            assertThat(first().setValue("pay_no")).isEqualTo("PAY-20260921-001");
        }
    }
}
