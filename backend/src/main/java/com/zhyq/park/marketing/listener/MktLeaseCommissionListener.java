package com.zhyq.park.marketing.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.contract.entity.Contract;
import com.zhyq.park.contract.mapper.ContractMapper;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.finance.service.BillMetrics;
import com.zhyq.park.crm.entity.Commission;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CommissionMapper;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.engine.PoolCalculator;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Lease commission changes commit atomically with contract approval / receipt / termination. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MktLeaseCommissionListener {

    static final String MODULE = "marketing";
    static final String DEFAULT_GRADE = "D";
    static final String LEASE_NO_PREFIX = "LEASE-";
    static final String SERVICE_BILL_SOURCE = "mkt_service";

    private final ContractMapper contractMapper;
    private final BillMapper billMapper;
    private final BizTenantMapper tenantMapper;
    private final CustomerMapper customerMapper;
    private final CommissionMapper legacyCommissionMapper;
    private final MktCustomerGradeMapper gradeMapper;
    private final MktReferralOrderMapper orderMapper;
    private final MktCommissionService commissionService;
    private final MktLockService lockService;
    private final MktServiceContractService serviceContractService;
    private final BizSettings bizSettings;
    private final com.zhyq.park.marketing.mapper.MktServiceContractMapper serviceContracts;

    @EventListener
    public void onContractApproved(DomainEvent.ContractApproved e) {
        createLeaseCommission(e.contractId(), e.projectId());
    }

    @EventListener
    public void onPaymentReceived(DomainEvent.PaymentReceived e) {
        if (e.contractId() != null) unfreezeForContract(e.contractId(), e.billId());
    }

    @EventListener
    public void onPaymentReversed(DomainEvent.PaymentReversed e) {
        if (e.contractId() != null) refreezeForContract(e.contractId(), e.billId());
    }

    @EventListener
    public void onContractTerminated(DomainEvent.ContractTerminated e) {
        Contract c = contractMapper.selectById(e.contractId());
        if (c == null || c.getStartDate() == null) return;
        int days = Math.max(0, bizSettings.getInt(MODULE, "lease_clawback_days", 90));
        java.time.LocalDate ended = c.getTerminateDate() != null ? c.getTerminateDate() : e.occurredAt().toLocalDate();
        if (!ended.isAfter(c.getStartDate().plusDays(days)))
            commissionService.clawbackBySource(MktCommissionService.SOURCE_LEASE, c.getId(), "租赁合同在 " + days + " 天内退租");
        else commissionService.voidUnsettledBySource(MktCommissionService.SOURCE_LEASE, c.getId(), "租赁合同退租,未结算佣金作废");
    }

    void createLeaseCommission(Long contractId, Long projectId) {
        Contract c = contractMapper.selectById(contractId);
        if (c == null || c.getRentPrice() == null || c.getRentArea() == null) {
            log.info("[mkt] 合同 {} 无租金/面积,不计佣", contractId);
            return;
        }
        Customer customer = findReferredCustomer(c.getTenantRefId());
        if (customer == null || customer.getReferrerId() == null) {
            log.info("[mkt] 合同 {} 的租客无推荐伙伴,不计佣", contractId);
            return;
        }
        // 租赁合同审批也要结束对应客户的活锁,并校验锁客伙伴与推荐伙伴一致。
        lockService.markDeal(customer.getId(), customer.getReferrerId());
        if (legacyCommissionExists(contractId) && bizSettings.getBoolean(MODULE, "old_channel_exclusive", true)) {
            log.warn("[mkt] 合同 {} 已有老渠道佣金,互斥开关开,跳过全民营销计佣", contractId);
            return;
        }
        String gradeCode = StringUtils.hasText(c.getGrade()) ? c.getGrade()
                : StringUtils.hasText(customer.getGrade()) ? customer.getGrade() : DEFAULT_GRADE;
        MktCustomerGrade grade = gradeMapper.selectOne(new LambdaQueryWrapper<MktCustomerGrade>()
                .eq(MktCustomerGrade::getCode, gradeCode).last("limit 1"));
        BigDecimal months = grade == null ? new BigDecimal("0.5") : grade.getLeaseCommissionMonths();
        BigDecimal monthlyRent = c.getRentPrice().multiply(c.getRentArea());
        BigDecimal pool = PoolCalculator.leasePool(c.getRentPrice(), c.getRentArea(), months);

        commissionService.createAndSplitInTransaction(new CommissionEvent(
                MktCommissionService.SOURCE_LEASE, LEASE_NO_PREFIX + contractId, contractId,
                customer.getId(), customer.getReferrerId(), gradeCode, months, monthlyRent, pool,
                LocalDateTime.now(), null, projectId, null));
        log.info("[mkt] 合同 {} 租赁一次性佣金已生成,池 {}", contractId, pool);
    }

    void unfreezeForContract(Long contractId, Long billId) {
        // fin_bill.contract_id 来自两张合同表，必须先按 source 分流，避免自增 id 撞号。
        Bill bill = billMapper.selectById(billId);
        if (isFirstServiceBill(bill, contractId) && fullyReceived(bill)) {
            unfreezeOrders(contractId, MktCommissionService.SOURCE_CONTRACT_BONUS);
            serviceContractService.startPerforming(contractId);
            return;
        }
        if (bill != null && SERVICE_BILL_SOURCE.equals(bill.getSource())) return;
        List<Bill> first = firstLeaseRentBills(contractId);
        if (bill != null && first.stream().anyMatch(b -> java.util.Objects.equals(b.getId(), billId))
                && !first.isEmpty() && first.stream().allMatch(MktLeaseCommissionListener::fullyReceived))
            unfreezeOrders(contractId, MktCommissionService.SOURCE_LEASE);
    }

    void refreezeForContract(Long contractId, Long billId) {
        Bill bill = billMapper.selectById(billId);
        if (bill == null) return;
        boolean serviceFirst = isFirstServiceBill(bill, contractId);
        if (SERVICE_BILL_SOURCE.equals(bill.getSource()) && !serviceFirst) return;
        if (serviceFirst && fullyReceived(bill)) return;
        if (!serviceFirst) {
            List<Bill> first = firstLeaseRentBills(contractId);
            if (first.stream().noneMatch(b -> java.util.Objects.equals(b.getId(), billId))
                    || first.stream().allMatch(MktLeaseCommissionListener::fullyReceived)) return;
        }
        int sourceType = serviceFirst ? MktCommissionService.SOURCE_CONTRACT_BONUS : MktCommissionService.SOURCE_LEASE;
        List<MktReferralOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getSourceId, contractId)
                .eq(MktReferralOrder::getSourceType, sourceType));
        for (MktReferralOrder o : orders) commissionService.refreezeByOrder(o.getId());
    }

    /** The first positive rent period may have several room bills; every one must be paid in cash. */
    private List<Bill> firstLeaseRentBills(Long contractId) {
        List<Bill> rent = billMapper.selectList(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getContractId, contractId).eq(Bill::getDirection, 1).eq(Bill::getFeeType, "租金")
                .ne(Bill::getStatus, 8).gt(Bill::getAmount, BigDecimal.ZERO)
                .and(q -> q.isNull(Bill::getSource).or().ne(Bill::getSource, SERVICE_BILL_SOURCE))
                .isNotNull(Bill::getPeriodStart).orderByAsc(Bill::getPeriodStart).orderByAsc(Bill::getId).last("FOR UPDATE"));
        if (rent.isEmpty()) return List.of();
        var first = rent.get(0).getPeriodStart();
        return rent.stream().filter(b -> java.util.Objects.equals(first, b.getPeriodStart())).toList();
    }

    private static boolean fullyReceived(Bill bill) {
        return Integer.valueOf(BillMetrics.STATUS_SETTLED).equals(bill.getStatus()) && bill.getAmount() != null
                && bill.getAmount().signum() > 0 && bill.getPaidAmount() != null
                && bill.getPaidAmount().compareTo(bill.getAmount()) >= 0;
    }

    private boolean isFirstServiceBill(Bill bill, Long contractId) {
        if (bill == null || !SERVICE_BILL_SOURCE.equals(bill.getSource())) {
            return false;
        }
        boolean feeEligible = "租金".equals(bill.getFeeType())
                || (bill.getFeeType() != null && bill.getFeeType().contains("保证金"));
        if (!feeEligible) return false;
        if (("mkt_service:" + contractId + ":first").equals(bill.getBillingKey())) return true;
        var contract = serviceContracts.selectById(contractId);
        return contract != null && contract.getStartDate() != null
                && ("mkt_service:" + contractId + ":rent:" + contract.getStartDate()).equals(bill.getBillingKey());
    }

    private void unfreezeOrders(Long contractId, int sourceType) {
        List<MktReferralOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getSourceId, contractId)
                .eq(MktReferralOrder::getSourceType, sourceType));
        for (MktReferralOrder o : orders) {
            int n = commissionService.unfreezeByOrderInTransaction(o.getId());
            if (n > 0) {
                log.info("[mkt] 合同 {} 到账,订单 {} 解冻 {} 行", contractId, o.getId(), n);
            }
        }
    }

    /** 租客 → crm_customer:按手机号匹配(客户建档与租客建档是两张表,靠手机号关联)。 */
    private Customer findReferredCustomer(Long tenantRefId) {
        if (tenantRefId == null) {
            return null;
        }
        BizTenant tenant = tenantMapper.selectById(tenantRefId);
        if (tenant == null || !StringUtils.hasText(tenant.getPhone())) {
            return null;
        }
        return customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getPhone, tenant.getPhone())
                .isNotNull(Customer::getReferrerId)
                .last("limit 1"));
    }

    private boolean legacyCommissionExists(Long contractId) {
        return legacyCommissionMapper.selectCount(new LambdaQueryWrapper<Commission>()
                .eq(Commission::getContractId, contractId)) > 0;
    }
}
