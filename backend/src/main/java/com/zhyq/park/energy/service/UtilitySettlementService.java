package com.zhyq.park.energy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.energy.entity.EnergyAllocation;
import com.zhyq.park.energy.entity.UtilityBill;
import com.zhyq.park.energy.mapper.EnergyAllocationMapper;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.finance.service.BillMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 水电结算台账。
 *
 * <p>这里不再保存一套可手工修改的“结算金额”。每个数都从
 * {@code eng_utility_bill -> eng_allocation -> fin_bill} 实时汇总：
 * 分摊确认后形成租户能源应收，财务收款后由 {@link BillMetrics} 统一计算实收与待收。
 * 这样水电台账和收银台、财务报表始终是同一笔钱。</p>
 */
@Service
@RequiredArgsConstructor
public class UtilitySettlementService {

    public static final String ST_NOT_BILLED = "NOT_BILLED";
    public static final String ST_NO_RECEIVABLE = "NO_RECEIVABLE";
    public static final String ST_BILL_EXCEPTION = "BILL_EXCEPTION";
    public static final String ST_PENDING_RECEIPT = "PENDING_RECEIPT";
    public static final String ST_PARTIAL_RECEIPT = "PARTIAL_RECEIPT";
    public static final String ST_SETTLED = "SETTLED";

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final EnergyAllocationMapper allocationMapper;
    private final BillMapper billMapper;

    /** 为账期列表填充财务结算字段；只读，不更新任何业务数据。 */
    public void enrich(List<UtilityBill> utilityBills) {
        if (utilityBills == null || utilityBills.isEmpty()) {
            return;
        }
        Set<Long> utilityBillIds = utilityBills.stream().map(UtilityBill::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (utilityBillIds.isEmpty()) {
            return;
        }

        Map<Long, List<EnergyAllocation>> allocationsByUtilityBill = allocationMapper.selectList(
                        new LambdaQueryWrapper<EnergyAllocation>()
                                .in(EnergyAllocation::getUtilityBillId, utilityBillIds))
                .stream().collect(Collectors.groupingBy(EnergyAllocation::getUtilityBillId));

        Set<Long> billIds = allocationsByUtilityBill.values().stream().flatMap(Collection::stream)
                .map(EnergyAllocation::getBillId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Bill> billsById = billIds.isEmpty() ? Map.of() : billMapper.selectBatchIds(billIds).stream()
                .collect(Collectors.toMap(Bill::getId, b -> b, (left, right) -> left, HashMap::new));

        for (UtilityBill utilityBill : utilityBills) {
            fillSettlement(utilityBill, allocationsByUtilityBill.getOrDefault(utilityBill.getId(), List.of()), billsById);
        }
    }

    /** 汇总结算页顶部的跨账期指标；“最近全额结算账期”不暗示中间账期没有断档。 */
    public SettlementSummary summarize(List<UtilityBill> utilityBills) {
        enrich(utilityBills);
        BigDecimal receivable = BigDecimal.ZERO;
        BigDecimal received = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        int pendingCount = 0;
        String latestElectric = null;
        String latestWater = null;

        for (UtilityBill bill : utilityBills) {
            if (!UtilityBill.ST_CONFIRMED.equals(bill.getStatus())) {
                continue;
            }
            receivable = receivable.add(nz(bill.getSettlementReceivableAmount()));
            received = received.add(nz(bill.getSettlementReceivedAmount()));
            outstanding = outstanding.add(nz(bill.getSettlementOutstandingAmount()));
            if (ST_SETTLED.equals(bill.getSettlementStatus())) {
                if ("电".equals(bill.getEnergyType()) && laterThan(bill.getPeriod(), latestElectric)) {
                    latestElectric = bill.getPeriod();
                }
                if ("水".equals(bill.getEnergyType()) && laterThan(bill.getPeriod(), latestWater)) {
                    latestWater = bill.getPeriod();
                }
            } else if (!ST_NO_RECEIVABLE.equals(bill.getSettlementStatus())) {
                pendingCount++;
            }
        }
        return new SettlementSummary(latestElectric, latestWater, receivable, received, outstanding, pendingCount);
    }

    private void fillSettlement(UtilityBill utilityBill, List<EnergyAllocation> allocations, Map<Long, Bill> billsById) {
        BigDecimal taxFactor = BigDecimal.ONE.add(nz(utilityBill.getTaxRate())
                .divide(HUNDRED, 6, RoundingMode.HALF_UP));
        utilityBill.setSettlementInvoiceAmount(nz(utilityBill.getInvoiceAmountExTax())
                .multiply(taxFactor).setScale(2, RoundingMode.HALF_UP));

        List<EnergyAllocation> receivableAllocations = allocations.stream()
                .filter(this::shouldCreateTenantBill).toList();
        BigDecimal receivable = BigDecimal.ZERO;
        BigDecimal received = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        int linkedBillCount = 0;
        int settledBillCount = 0;
        boolean billMissingOrInvalid = false;

        for (EnergyAllocation allocation : receivableAllocations) {
            Bill bill = allocation.getBillId() == null ? null : billsById.get(allocation.getBillId());
            if (!BillMetrics.isReceivable(bill)) {
                billMissingOrInvalid = true;
                continue;
            }
            linkedBillCount++;
            BigDecimal billReceivable = BillMetrics.receivableOf(bill);
            BigDecimal billReceived = BillMetrics.receivedOf(bill);
            BigDecimal billOutstanding = BillMetrics.outstandingOf(bill);
            receivable = receivable.add(billReceivable);
            received = received.add(billReceived);
            outstanding = outstanding.add(billOutstanding);
            if (billOutstanding.signum() == 0) {
                settledBillCount++;
            }
        }

        utilityBill.setSettlementReceivableAmount(receivable);
        utilityBill.setSettlementReceivedAmount(received);
        utilityBill.setSettlementOutstandingAmount(outstanding);
        utilityBill.setSettlementBillCount(linkedBillCount);
        utilityBill.setSettlementSettledBillCount(settledBillCount);
        utilityBill.setSettlementStatus(resolveStatus(utilityBill, receivableAllocations.size(), billMissingOrInvalid,
                received, outstanding));
    }

    private boolean shouldCreateTenantBill(EnergyAllocation allocation) {
        return "TENANT".equals(allocation.getMeterRole())
                && allocation.getTenantRefId() != null
                && nz(allocation.getTotalFee()).signum() > 0;
    }

    private String resolveStatus(UtilityBill bill, int expectedBillCount, boolean billMissingOrInvalid,
                                 BigDecimal received, BigDecimal outstanding) {
        if (!UtilityBill.ST_CONFIRMED.equals(bill.getStatus())) {
            return ST_NOT_BILLED;
        }
        if (expectedBillCount == 0) {
            return ST_NO_RECEIVABLE;
        }
        if (billMissingOrInvalid) {
            return ST_BILL_EXCEPTION;
        }
        if (outstanding.signum() == 0) {
            return ST_SETTLED;
        }
        return received.signum() == 0 ? ST_PENDING_RECEIPT : ST_PARTIAL_RECEIPT;
    }

    private static boolean laterThan(String candidate, String baseline) {
        return candidate != null && (baseline == null || Comparator.<String>naturalOrder().compare(candidate, baseline) > 0);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record SettlementSummary(String latestElectricSettledPeriod, String latestWaterSettledPeriod,
                                    BigDecimal receivableAmount, BigDecimal receivedAmount,
                                    BigDecimal outstandingAmount, int pendingSettlementCount) {}
}
