package com.zhyq.park.energy.service;

import com.zhyq.park.energy.entity.EnergyAllocation;
import com.zhyq.park.energy.entity.UtilityBill;
import com.zhyq.park.energy.mapper.EnergyAllocationMapper;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UtilitySettlementServiceTest {

    @Mock private EnergyAllocationMapper allocationMapper;
    @Mock private BillMapper billMapper;

    @Test
    void confirmedUtilityBillUsesFinancialReceiptsInsteadOfAllocationAmount() {
        UtilityBill utilityBill = utilityBill(1L, "电", "2026-08", UtilityBill.ST_CONFIRMED);
        EnergyAllocation first = allocation(1L, 101L);
        EnergyAllocation second = allocation(1L, 102L);
        Bill settled = bill(101L, "100", "100", "0");
        Bill partiallyPaid = bill(102L, "200", "50", "10");
        when(allocationMapper.selectList(any())).thenReturn(List.of(first, second));
        when(billMapper.selectBatchIds(any())).thenReturn(List.of(settled, partiallyPaid));

        new UtilitySettlementService(allocationMapper, billMapper).enrich(List.of(utilityBill));

        assertThat(utilityBill.getSettlementReceivableAmount()).isEqualByComparingTo("310");
        assertThat(utilityBill.getSettlementReceivedAmount()).isEqualByComparingTo("150");
        assertThat(utilityBill.getSettlementOutstandingAmount()).isEqualByComparingTo("160");
        assertThat(utilityBill.getSettlementSettledBillCount()).isEqualTo(1);
        assertThat(utilityBill.getSettlementBillCount()).isEqualTo(2);
        assertThat(utilityBill.getSettlementStatus()).isEqualTo(UtilitySettlementService.ST_PARTIAL_RECEIPT);
    }

    @Test
    void latestSettledPeriodOnlyIncludesFullyCollectedConfirmedBills() {
        UtilityBill electric = utilityBill(1L, "电", "2026-08", UtilityBill.ST_CONFIRMED);
        EnergyAllocation allocation = allocation(1L, 101L);
        when(allocationMapper.selectList(any())).thenReturn(List.of(allocation));
        when(billMapper.selectBatchIds(any())).thenReturn(List.of(bill(101L, "88", "88", "0")));

        UtilitySettlementService.SettlementSummary summary =
                new UtilitySettlementService(allocationMapper, billMapper).summarize(List.of(electric));

        assertThat(summary.latestElectricSettledPeriod()).isEqualTo("2026-08");
        assertThat(summary.latestWaterSettledPeriod()).isNull();
        assertThat(summary.pendingSettlementCount()).isZero();
    }

    private static UtilityBill utilityBill(Long id, String energyType, String period, String status) {
        UtilityBill bill = new UtilityBill();
        bill.setId(id);
        bill.setEnergyType(energyType);
        bill.setPeriod(period);
        bill.setStatus(status);
        bill.setInvoiceAmountExTax(new BigDecimal("100"));
        bill.setTaxRate(new BigDecimal("13"));
        return bill;
    }

    private static EnergyAllocation allocation(Long utilityBillId, Long billId) {
        EnergyAllocation allocation = new EnergyAllocation();
        allocation.setUtilityBillId(utilityBillId);
        allocation.setBillId(billId);
        allocation.setMeterRole("TENANT");
        allocation.setTenantRefId(99L);
        allocation.setTotalFee(new BigDecimal("100"));
        return allocation;
    }

    private static Bill bill(Long id, String amount, String paidAmount, String lateFee) {
        Bill bill = new Bill();
        bill.setId(id);
        bill.setDirection(1);
        bill.setAmount(new BigDecimal(amount));
        bill.setPaidAmount(new BigDecimal(paidAmount));
        bill.setLateFee(new BigDecimal(lateFee));
        return bill;
    }
}
