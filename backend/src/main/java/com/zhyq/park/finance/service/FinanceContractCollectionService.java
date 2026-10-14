package com.zhyq.park.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.entity.ContractRef;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.finance.mapper.ContractRefMapper;
import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.mapper.ReceivableRegisterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 只读合同收款全景：按合同汇总应收账单的应收、实收与未收，并拆出逾期 / 到期未逾期 / 未到期三段。
 * 账单范围与逾期规则复用 {@link FinanceOverviewService}，两处口径保持一致。
 */
@Service
@RequiredArgsConstructor
public class FinanceContractCollectionService {

    static final String STATE_UNBILLED = "unbilled";
    static final String STATE_OVERDUE = "overdue";
    static final String STATE_SETTLED = "settled";
    static final String STATE_UNPAID = "unpaid";
    static final String STATE_PARTIAL = "partial";
    private static final int COOPERATION_CONTRACT_TYPE = 7;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final BillMapper billMapper;
    private final ReceivableRegisterMapper registerMapper;
    private final ContractRefMapper contractRefMapper;
    private final FinanceViewEnricher viewEnricher;

    @Transactional(readOnly = true)
    public ContractCollection collection() {
        List<Bill> bills = billMapper.selectList(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getDirection, BillMetrics.DIRECTION_RECEIVE)
                .in(Bill::getStatus, FinanceOverviewService.EFFECTIVE_STATUSES));
        viewEnricher.enrichBills(bills);
        List<Long> registerIds = bills.stream().map(Bill::getReceivableRegisterId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, RegisterLink> registers = new HashMap<>();
        if (!registerIds.isEmpty()) {
            for (ReceivableRegister register : registerMapper.selectBatchIds(registerIds)) {
                registers.put(register.getId(),
                        new RegisterLink(register.getContractId(), register.getLateFeeStartDate()));
            }
        }
        List<ContractRef> contracts = contractRefMapper.selectList(new LambdaQueryWrapper<ContractRef>());
        Map<Long, String> tenantNames = viewEnricher.tenantNamesOf(contracts.stream()
                .map(ContractRef::getTenantRefId).filter(Objects::nonNull).distinct().toList());
        return aggregate(LocalDate.now(BUSINESS_ZONE), bills, registers, contracts, tenantNames);
    }

    /** 纯聚合：不写库、不读时钟，金额全部使用 BigDecimal。 */
    static ContractCollection aggregate(LocalDate today, List<Bill> sourceBills, Map<Long, RegisterLink> registers,
                                        List<ContractRef> contracts, Map<Long, String> tenantNames) {
        Map<Long, ContractRef> contractById = new HashMap<>();
        for (ContractRef contract : contracts) {
            if (contract != null && contract.getId() != null && !Integer.valueOf(1).equals(contract.getDeleted())) {
                contractById.put(contract.getId(), contract);
            }
        }
        Map<String, RowTotals> rows = new LinkedHashMap<>();
        Map<String, AgingTotals> aging = newAgingBuckets();

        for (Bill bill : sourceBills) {
            if (!FinanceOverviewService.isEffective(bill)) continue;
            RegisterLink register = bill.getReceivableRegisterId() == null ? null
                    : registers.get(bill.getReceivableRegisterId());
            Long contractId = bill.getContractId() != null ? bill.getContractId()
                    : register == null ? null : register.contractId();
            String key = contractId != null ? "contract:" + contractId
                    : bill.getReceivableRegisterId() != null ? "register:" + bill.getReceivableRegisterId()
                    : bill.getTenantRefId() != null ? "tenant:" + bill.getTenantRefId()
                    : "bill:" + bill.getId();
            RowTotals row = rows.computeIfAbsent(key, ignored -> new RowTotals(key, contractId));
            row.capture(bill);

            BigDecimal outstanding = BillMetrics.outstandingOf(bill);
            LocalDate policyDate = register == null ? null : register.lateFeeStartDate();
            Segment segment = segmentOf(bill.getDueDate(), policyDate, today);
            row.add(bill, outstanding, segment);
            if (outstanding.signum() > 0) aging.get(agingKeyOf(bill.getDueDate(), policyDate, today)).add(outstanding);
        }

        for (ContractRef contract : contractById.values()) {
            String key = "contract:" + contract.getId();
            boolean cooperation = Integer.valueOf(COOPERATION_CONTRACT_TYPE).equals(contract.getContractType());
            if (!rows.containsKey(key) && !cooperation) rows.put(key, new RowTotals(key, contract.getId()));
        }

        List<ContractRow> contractRows = rows.values().stream()
                .map(row -> row.view(contractById.get(row.contractId), tenantNames))
                .sorted(ROW_ORDER).toList();
        List<AgingBucket> agingBuckets = aging.entrySet().stream()
                .map(entry -> new AgingBucket(entry.getKey(), entry.getValue().label,
                        entry.getValue().amount, entry.getValue().billCount)).toList();
        return new ContractCollection(today, summarize(contractRows), contractRows, agingBuckets);
    }

    private static final Comparator<ContractRow> ROW_ORDER = Comparator
            .comparing((ContractRow row) -> STATE_UNBILLED.equals(row.state()))
            .thenComparing(ContractRow::overdueOutstanding, Comparator.reverseOrder())
            .thenComparing(ContractRow::outstanding, Comparator.reverseOrder())
            .thenComparing(ContractRow::contractCode, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ContractRow::key);

    private enum Segment { OVERDUE, DUE, NOT_DUE }

    private static Segment segmentOf(LocalDate dueDate, LocalDate policyDate, LocalDate today) {
        if (dueDate == null || dueDate.isAfter(today)) return Segment.NOT_DUE;
        return FinanceOverviewService.isOverdue(dueDate, policyDate, today) ? Segment.OVERDUE : Segment.DUE;
    }

    private static String agingKeyOf(LocalDate dueDate, LocalDate policyDate, LocalDate today) {
        if (dueDate == null) return "undated";
        if (dueDate.isAfter(today)) return "notDue";
        if (!FinanceOverviewService.isOverdue(dueDate, policyDate, today)) return "grace";
        LocalDate start = policyDate != null && policyDate.isAfter(dueDate) ? policyDate : dueDate;
        long days = ChronoUnit.DAYS.between(start, today);
        return days <= 30 ? "d30" : days <= 90 ? "d90" : "d90plus";
    }

    private static Map<String, AgingTotals> newAgingBuckets() {
        Map<String, AgingTotals> buckets = new LinkedHashMap<>();
        buckets.put("notDue", new AgingTotals("未到期"));
        buckets.put("undated", new AgingTotals("未设应收日"));
        buckets.put("grace", new AgingTotals("宽限期内"));
        buckets.put("d30", new AgingTotals("逾期 1–30 天"));
        buckets.put("d90", new AgingTotals("逾期 31–90 天"));
        buckets.put("d90plus", new AgingTotals("逾期 90 天以上"));
        return buckets;
    }

    private static Summary summarize(List<ContractRow> rows) {
        Map<String, Integer> states = new HashMap<>();
        BigDecimal receivable = BigDecimal.ZERO;
        BigDecimal received = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        int contracts = 0;
        for (ContractRow row : rows) {
            states.merge(row.state(), 1, Integer::sum);
            if (row.contractId() != null) contracts++;
            receivable = receivable.add(row.receivable());
            received = received.add(row.received());
            outstanding = outstanding.add(row.outstanding());
            overdue = overdue.add(row.overdueOutstanding());
        }
        return new Summary(contracts, rows.size() - contracts,
                states.getOrDefault(STATE_SETTLED, 0), states.getOrDefault(STATE_PARTIAL, 0),
                states.getOrDefault(STATE_UNPAID, 0), states.getOrDefault(STATE_OVERDUE, 0),
                states.getOrDefault(STATE_UNBILLED, 0), receivable, received, outstanding, overdue);
    }

    private static final class RowTotals {
        private final String key;
        private final Long contractId;
        private final Map<String, FeeTotals> fees = new HashMap<>();
        private Long tenantRefId;
        private String tenantName;
        private BigDecimal receivable = BigDecimal.ZERO;
        private BigDecimal received = BigDecimal.ZERO;
        private BigDecimal outstanding = BigDecimal.ZERO;
        private BigDecimal overdue = BigDecimal.ZERO;
        private BigDecimal due = BigDecimal.ZERO;
        private BigDecimal notDue = BigDecimal.ZERO;
        private long billCount;

        private RowTotals(String key, Long contractId) {
            this.key = key;
            this.contractId = contractId;
        }

        private void capture(Bill bill) {
            if (tenantRefId == null) tenantRefId = bill.getTenantRefId();
            if ((tenantName == null || tenantName.isBlank()) && bill.getTenantName() != null) {
                tenantName = bill.getTenantName();
            }
        }

        private void add(Bill bill, BigDecimal billOutstanding, Segment segment) {
            receivable = receivable.add(BillMetrics.receivableOf(bill));
            received = received.add(BillMetrics.receivedOf(bill));
            outstanding = outstanding.add(billOutstanding);
            switch (segment) {
                case OVERDUE -> overdue = overdue.add(billOutstanding);
                case DUE -> due = due.add(billOutstanding);
                case NOT_DUE -> notDue = notDue.add(billOutstanding);
            }
            billCount++;
            String feeType = bill.getFeeType() == null || bill.getFeeType().isBlank() ? "其他" : bill.getFeeType().trim();
            fees.computeIfAbsent(feeType, FeeTotals::new).add(bill, billOutstanding, segment == Segment.OVERDUE);
        }

        private String state() {
            if (billCount == 0) return STATE_UNBILLED;
            if (overdue.signum() > 0) return STATE_OVERDUE;
            if (outstanding.signum() == 0) return STATE_SETTLED;
            return received.signum() == 0 ? STATE_UNPAID : STATE_PARTIAL;
        }

        private ContractRow view(ContractRef contract, Map<Long, String> tenantNames) {
            Long tenantId = contract != null && contract.getTenantRefId() != null ? contract.getTenantRefId() : tenantRefId;
            String name = tenantId == null ? null : tenantNames.get(tenantId);
            if (name == null || name.isBlank()) name = tenantName;
            if (name == null || name.isBlank()) name = tenantId == null ? "未关联租客" : "租客 #" + tenantId;
            String code = contract != null ? contract.getCode() : contractId != null ? "合同 #" + contractId : null;
            List<FeeLine> feeLines = fees.values().stream().map(FeeTotals::view)
                    .sorted(Comparator.comparing(FeeLine::receivable).reversed().thenComparing(FeeLine::feeType))
                    .toList();
            return new ContractRow(key, contractId, code, tenantId, name,
                    contract == null ? null : contract.getStatus(),
                    contract == null ? null : contract.getStartDate(),
                    contract == null ? null : contract.getEndDate(), state(),
                    receivable, received, outstanding, overdue, due, notDue, billCount, feeLines);
        }
    }

    private static final class FeeTotals {
        private final String feeType;
        private BigDecimal receivable = BigDecimal.ZERO;
        private BigDecimal received = BigDecimal.ZERO;
        private BigDecimal outstanding = BigDecimal.ZERO;
        private BigDecimal overdue = BigDecimal.ZERO;
        private long billCount;

        private FeeTotals(String feeType) { this.feeType = feeType; }

        private void add(Bill bill, BigDecimal billOutstanding, boolean isOverdue) {
            receivable = receivable.add(BillMetrics.receivableOf(bill));
            received = received.add(BillMetrics.receivedOf(bill));
            outstanding = outstanding.add(billOutstanding);
            if (isOverdue) overdue = overdue.add(billOutstanding);
            billCount++;
        }

        private FeeLine view() { return new FeeLine(feeType, receivable, received, outstanding, overdue, billCount); }
    }

    private static final class AgingTotals {
        private final String label;
        private BigDecimal amount = BigDecimal.ZERO;
        private long billCount;

        private AgingTotals(String label) { this.label = label; }

        private void add(BigDecimal value) {
            amount = amount.add(value);
            billCount++;
        }
    }

    public record RegisterLink(Long contractId, LocalDate lateFeeStartDate) {}

    public record ContractCollection(LocalDate asOfDate, Summary summary, List<ContractRow> contracts,
                                     List<AgingBucket> aging) {}

    public record Summary(int contractCount, int unlinkedGroups, int settled, int partial, int unpaid, int overdue,
                          int unbilled, BigDecimal receivable, BigDecimal received, BigDecimal outstanding,
                          BigDecimal overdueOutstanding) {}

    public record ContractRow(String key, Long contractId, String contractCode, Long tenantRefId, String tenantName,
                              Integer contractStatus, LocalDate startDate, LocalDate endDate, String state,
                              BigDecimal receivable, BigDecimal received, BigDecimal outstanding,
                              BigDecimal overdueOutstanding, BigDecimal dueOutstanding, BigDecimal notDueOutstanding,
                              long billCount, List<FeeLine> fees) {}

    public record FeeLine(String feeType, BigDecimal receivable, BigDecimal received, BigDecimal outstanding,
                          BigDecimal overdueOutstanding, long billCount) {}

    public record AgingBucket(String key, String label, BigDecimal amount, long billCount) {}
}
