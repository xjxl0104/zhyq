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
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 租赁线的计佣(PARK-MKT-001 §2.7a 园区入驻):
 * <ul>
 *   <li>{@code contract.approved} → 找到该租客对应的 crm_customer 推荐伙伴 → 佣金池 = 单价 × 面积 × 评级佣金月数 → 一次性佣金(冻结);
 *       老渠道佣金(crm_commission)已有该合同且互斥开关开 → 跳过并写日志;</li>
 *   <li>{@code payment.received} → 该合同的租赁计佣订单解冻(首期租金/保证金任一到账即算,§2.4);
 *       同时服务合同的签约奖(source_type=4)也在这里解冻 —— 服务合同的账单 contract_id 指向 crm_service_contract。</li>
 * </ul>
 * 用 AFTER_COMMIT:合同审批 / 收款事务失败时不会误计佣;fallbackExecution 让无事务上下文的调用(如测试)也能触发。
 */
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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onContractApproved(DomainEvent.ContractApproved e) {
        try {
            createLeaseCommission(e.contractId(), e.projectId());
        } catch (Exception ex) {
            log.error("[mkt] 租赁计佣失败 contractId={}", e.contractId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaymentReceived(DomainEvent.PaymentReceived e) {
        if (e.contractId() == null) {
            return;
        }
        try {
            unfreezeForContract(e.contractId(), e.billId());
        } catch (Exception ex) {
            log.error("[mkt] 到账解冻失败 contractId={}", e.contractId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaymentReversed(DomainEvent.PaymentReversed e) {
        if (e.contractId() == null) return;
        try {
            refreezeForContract(e.contractId(), e.billId());
        } catch (Exception ex) {
            log.error("[mkt] 收款红冲回冻失败 contractId={}", e.contractId(), ex);
        }
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

        commissionService.createAndSplit(new CommissionEvent(
                MktCommissionService.SOURCE_LEASE, LEASE_NO_PREFIX + contractId, contractId,
                customer.getId(), customer.getReferrerId(), gradeCode, months, monthlyRent, pool,
                LocalDateTime.now(), null, projectId, null));
        log.info("[mkt] 合同 {} 租赁一次性佣金已生成,池 {}", contractId, pool);
    }

    void unfreezeForContract(Long contractId, Long billId) {
        // fin_bill.contract_id 来自两张合同表，必须先按 source 分流，避免自增 id 撞号。
        Bill bill = billMapper.selectById(billId);
        if (isFirstServiceBill(bill, contractId) && Integer.valueOf(BillMetrics.STATUS_SETTLED).equals(bill.getStatus())) {
            unfreezeOrders(contractId, MktCommissionService.SOURCE_CONTRACT_BONUS);
            serviceContractService.startPerforming(contractId);
            return;
        }
        if (bill != null && SERVICE_BILL_SOURCE.equals(bill.getSource())) return;
        unfreezeOrders(contractId, MktCommissionService.SOURCE_LEASE);
    }

    void refreezeForContract(Long contractId, Long billId) {
        Bill bill = billMapper.selectById(billId);
        if (bill == null || (bill.getPaidAmount() != null && bill.getPaidAmount().signum() > 0)) return;
        boolean serviceFirst = isFirstServiceBill(bill, contractId);
        if (SERVICE_BILL_SOURCE.equals(bill.getSource()) && !serviceFirst) return;
        int sourceType = serviceFirst ? MktCommissionService.SOURCE_CONTRACT_BONUS : MktCommissionService.SOURCE_LEASE;
        List<MktReferralOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getSourceId, contractId)
                .eq(MktReferralOrder::getSourceType, sourceType));
        for (MktReferralOrder o : orders) commissionService.refreezeByOrder(o.getId());
    }

    private boolean isFirstServiceBill(Bill bill, Long contractId) {
        if (bill == null || !SERVICE_BILL_SOURCE.equals(bill.getSource())
) {
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
            int n = commissionService.unfreezeByOrder(o.getId());
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
