package com.zhyq.park.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.entity.Payment;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.finance.mapper.PaymentMapper;
import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.mapper.ReceivableRegisterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 只读财务总览。账期实收是该账期账单的累计实收；现金到账按收款发生月计算。
 * 当前待收覆盖所有账期（含未来账期），不随选择月份变化。范围为当前系统租户的全部项目。
 */
@Service
@RequiredArgsConstructor
public class FinanceOverviewService {

    private static final List<Integer> EFFECTIVE_STATUSES = List.of(3, 4, 5, 6);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final BillMapper billMapper;
    private final PaymentMapper paymentMapper;
    private final ReceivableRegisterMapper registerMapper;
    private final FinanceViewEnricher viewEnricher;

    @Transactional(readOnly = true)
    public Overview overview(String month) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        YearMonth selectedMonth = parseMonth(month, today);
        // 不接收 projectId：登记表账单可能没有 project_id，不能据此漏掉实际应收。
        List<Bill> bills = billMapper.selectList(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getDirection, BillMetrics.DIRECTION_RECEIVE)
                .in(Bill::getStatus, EFFECTIVE_STATUSES));
        // BaseMapper 自动过滤逻辑删除；聚合器同样校验，便于离线验证与保持统一入口。
        viewEnricher.enrichBills(bills);
        List<Long> registerIds = bills.stream().map(Bill::getReceivableRegisterId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, LocalDate> policyDates = new HashMap<>();
        if (!registerIds.isEmpty()) {
            for (ReceivableRegister register : registerMapper.selectBatchIds(registerIds)) {
                if (register.getLateFeeStartDate() != null) {
                    policyDates.put(register.getId(), register.getLateFeeStartDate());
                }
            }
        }
        // 不过滤 void_status：被撤原单的正额与当月红冲的负额共同构成净现金，跨月各归发生月。
        List<Payment> payments = paymentMapper.selectList(new LambdaQueryWrapper<Payment>()
                .ge(Payment::getPayTime, selectedMonth.atDay(1).atStartOfDay())
                .lt(Payment::getPayTime, selectedMonth.plusMonths(1).atDay(1).atStartOfDay()));
        return aggregate(selectedMonth, today, bills, payments, policyDates);
    }

    static YearMonth parseMonth(String month, LocalDate today) {
        if (month == null || month.isBlank()) return YearMonth.from(today);
        if (!month.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
            throw new BizException("月份格式应为 YYYY-MM");
        }
        try {
            YearMonth result = YearMonth.parse(month);
            if (result.getYear() < 1) throw new BizException("月份年份必须大于 0");
            return result;
        } catch (DateTimeParseException exception) {
            throw new BizException("月份格式应为 YYYY-MM");
        }
    }

    /** 纯聚合：不写库、不依赖当前时钟，金额全部使用 BigDecimal。 */
    static Overview aggregate(YearMonth month, LocalDate today, List<Bill> sourceBills,
                              List<Payment> payments, Map<Long, LocalDate> policyDates) {
        Totals period = new Totals();
        Map<String, Totals> fees = new HashMap<>();
        Map<YearMonth, Totals> monthly = new LinkedHashMap<>();
        for (int offset = 5; offset >= 0; offset--) monthly.put(month.minusMonths(offset), new Totals());
        Map<String, DebtorTotals> debtors = new HashMap<>();
        Set<Long> effectiveBillIds = new HashSet<>();
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        BigDecimal dueOutstanding = BigDecimal.ZERO;
        BigDecimal upcomingOutstanding = BigDecimal.ZERO;
        BigDecimal undatedOutstanding = BigDecimal.ZERO;
        BigDecimal overdueOutstanding = BigDecimal.ZERO;
        BigDecimal depositOutstanding = BigDecimal.ZERO;
        long overdueBillCount = 0;

        for (Bill bill : sourceBills) {
            if (!isEffective(bill)) continue;
            if (bill.getId() != null) effectiveBillIds.add(bill.getId());
            String feeType = bill.getFeeType() == null || bill.getFeeType().isBlank()
                    ? "其他" : bill.getFeeType().trim();
            YearMonth billMonth = bill.getPeriodStart() == null ? null : YearMonth.from(bill.getPeriodStart());
            if (month.equals(billMonth)) {
                period.add(bill);
                fees.computeIfAbsent(feeType, ignored -> new Totals()).add(bill);
            }
            Totals trendMonth = monthly.get(billMonth);
            if (trendMonth != null) trendMonth.add(bill);

            BigDecimal outstanding = BillMetrics.outstandingOf(bill);
            if (outstanding.signum() <= 0) continue;
            totalOutstanding = totalOutstanding.add(outstanding);
            if (bill.getDueDate() == null) {
                undatedOutstanding = undatedOutstanding.add(outstanding);
            } else if (!bill.getDueDate().isAfter(today)) {
                dueOutstanding = dueOutstanding.add(outstanding);
            } else {
                upcomingOutstanding = upcomingOutstanding.add(outstanding);
            }
            LocalDate policyDate = bill.getReceivableRegisterId() == null ? null
                    : policyDates.get(bill.getReceivableRegisterId());
            boolean overdue = isOverdue(bill.getDueDate(), policyDate, today);
            if (overdue) {
                overdueOutstanding = overdueOutstanding.add(outstanding);
                overdueBillCount++;
            }
            if (feeType.contains("保证金")) depositOutstanding = depositOutstanding.add(outstanding);

            // 不按显示名称合并不同租客；未关联档案时按登记表或账单隔离。
            String debtorKey = bill.getTenantRefId() != null ? "tenant:" + bill.getTenantRefId()
                    : bill.getReceivableRegisterId() != null ? "register:" + bill.getReceivableRegisterId()
                    : "bill:" + bill.getId();
            DebtorTotals debtor = debtors.computeIfAbsent(debtorKey, ignored -> new DebtorTotals(bill));
            debtor.outstanding = debtor.outstanding.add(outstanding);
            if (overdue) debtor.overdueOutstanding = debtor.overdueOutstanding.add(outstanding);
            debtor.billCount++;
        }

        BigDecimal cashReceived = BigDecimal.ZERO;
        for (Payment payment : payments) {
            if (payment != null && !Integer.valueOf(1).equals(payment.getDeleted())
                    && effectiveBillIds.contains(payment.getBillId()) && payment.getPayTime() != null
                    && month.equals(YearMonth.from(payment.getPayTime()))) {
                cashReceived = cashReceived.add(BillMetrics.nz(payment.getAmount()));
            }
        }
        List<FeeStructure> feeStructure = fees.entrySet().stream()
                .map(entry -> new FeeStructure(entry.getKey(), entry.getValue().receivable,
                        entry.getValue().received, entry.getValue().outstanding))
                .sorted(Comparator.comparing(FeeStructure::receivable).reversed()
                        .thenComparing(FeeStructure::feeType)).toList();
        List<MonthTrend> trend = new ArrayList<>();
        monthly.forEach((key, value) -> trend.add(new MonthTrend(key.toString(), value.receivable,
                value.received, value.outstanding)));
        List<Debtor> topDebtors = debtors.values().stream().map(DebtorTotals::view)
                .sorted(Comparator.comparing(Debtor::outstanding).reversed()
                        .thenComparing(Debtor::tenantName)
                        .thenComparing(Debtor::tenantRefId, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(5).toList();
        BigDecimal rate = period.receivable.signum() > 0
                ? period.received.divide(period.receivable, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        return new Overview(month.toString(), today, "全园区 · 全部项目", period.receivable,
                period.received, period.outstanding, rate, cashReceived, totalOutstanding,
                dueOutstanding, upcomingOutstanding, undatedOutstanding, overdueOutstanding,
                overdueBillCount, depositOutstanding, feeStructure, List.copyOf(trend), topDebtors);
    }

    private static boolean isEffective(Bill bill) {
        return BillMetrics.isReceivable(bill) && !Integer.valueOf(1).equals(bill.getDeleted())
                && bill.getStatus() != null && EFFECTIVE_STATUSES.contains(bill.getStatus());
    }

    private static boolean isOverdue(LocalDate dueDate, LocalDate policyDate, LocalDate today) {
        if (dueDate == null) return false;
        LocalDate start = policyDate != null && policyDate.isAfter(dueDate) ? policyDate : dueDate;
        return start.isBefore(today);
    }

    private static final class Totals {
        private BigDecimal receivable = BigDecimal.ZERO;
        private BigDecimal received = BigDecimal.ZERO;
        private BigDecimal outstanding = BigDecimal.ZERO;

        private void add(Bill bill) {
            receivable = receivable.add(BillMetrics.receivableOf(bill));
            received = received.add(BillMetrics.receivedOf(bill));
            outstanding = outstanding.add(BillMetrics.outstandingOf(bill));
        }
    }

    private static final class DebtorTotals {
        private final Long tenantRefId;
        private final String tenantName;
        private BigDecimal outstanding = BigDecimal.ZERO;
        private BigDecimal overdueOutstanding = BigDecimal.ZERO;
        private long billCount;

        private DebtorTotals(Bill bill) {
            tenantRefId = bill.getTenantRefId();
            tenantName = bill.getTenantName() != null && !bill.getTenantName().isBlank()
                    ? bill.getTenantName() : tenantRefId == null ? "未关联租客 · 账单 " + bill.getCode()
                    : "租客 #" + tenantRefId;
        }

        private Debtor view() { return new Debtor(tenantRefId, tenantName, outstanding, overdueOutstanding, billCount); }
    }

    public record Overview(String month, LocalDate asOfDate, String scopeLabel,
                           BigDecimal periodReceivable, BigDecimal periodReceived, BigDecimal periodOutstanding,
                           BigDecimal collectionRate, BigDecimal cashReceived, BigDecimal totalOutstanding,
                           BigDecimal dueOutstanding, BigDecimal upcomingOutstanding, BigDecimal undatedOutstanding,
                           BigDecimal overdueOutstanding, long overdueBillCount, BigDecimal depositOutstanding,
                           List<FeeStructure> feeStructure, List<MonthTrend> trend, List<Debtor> topDebtors) {}

    public record FeeStructure(String feeType, BigDecimal receivable, BigDecimal received, BigDecimal outstanding) {}
    public record MonthTrend(String month, BigDecimal receivable, BigDecimal received, BigDecimal outstanding) {}
    public record Debtor(Long tenantRefId, String tenantName, BigDecimal outstanding,
                         BigDecimal overdueOutstanding, long billCount) {}
}
