package com.zhyq.park.receivable.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.receivable.dto.ReceivableGenerateResult;
import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.entity.ReceivableRule;
import com.zhyq.park.receivable.mapper.ReceivableRegisterMapper;
import com.zhyq.park.receivable.mapper.ReceivableRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReceivablePlanService {
    private final ReceivableRegisterMapper registerMapper;
    private final ReceivableRuleMapper ruleMapper;
    private final BillMapper billMapper;
    private final ReceivableCalculator calculator;

    @Transactional(rollbackFor = Exception.class)
    public ReceivableGenerateResult generate(long registerId) {
        ReceivableRegister register = requireConfirmed(registerId);
        List<ReceivableRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<ReceivableRule>()
                .eq(ReceivableRule::getRegisterId, registerId)
                .eq(ReceivableRule::getStatus, "ACTIVE")
                .orderByAsc(ReceivableRule::getPriority));
        List<Bill> candidates = buildBills(register, rules);
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        for (Bill bill : candidates) {
            Bill existing = find(bill.getBillingKey());
            if (existing != null) {
                if (canSynchronize(existing)) {
                    bill.setId(existing.getId());
                    // 覆盖只同步生成侧字段(金额/账期/应收日/规则/备注)。滞纳金/逾期
                    // 天数/状态/实收/开票是运行时状态:滞纳金已计入应收,重跑生成把它
                    // 清零等于免掉欠款;置 null 后 MyBatis-Plus 非空策略不会写这些列
                    bill.setStatus(null);
                    bill.setPaidAmount(null);
                    bill.setLateFee(null);
                    bill.setOverdueDays(null);
                    bill.setInvoiceStatus(null);
                    // 条件更新:canSynchronize 用的是进循环前的快照,这中间可能刚落了
                    // 一笔收款。新实体不带 version,乐观锁拦截器不会介入 —— 前置条件
                    // 必须显式写进 WHERE,否则会把真实收款覆盖回 paid_amount=0。
                    // 条件不满足(updated==0)按跳过计,不覆盖
                    int changed = billMapper.update(bill, new LambdaUpdateWrapper<Bill>()
                            .eq(Bill::getId, existing.getId())
                            .in(Bill::getStatus, 1, 2, 3, 6)
                            .apply("paid_amount = 0"));
                    if (changed == 1) {
                        updated++;
                    } else {
                        skipped++;
                    }
                } else {
                    skipped++;
                }
                continue;
            }
            try {
                billMapper.insert(bill);
                inserted++;
            } catch (DuplicateKeyException race) {
                if (find(bill.getBillingKey()) == null) throw race;
                skipped++;
            }
        }
        return new ReceivableGenerateResult(candidates.size(), inserted, updated, skipped);
    }

    private ReceivableRegister requireConfirmed(long registerId) {
        ReceivableRegister register = registerMapper.selectByIdForUpdate(registerId);
        if (register == null) throw new BizException("应收登记表不存在");
        if (!"CONFIRMED".equals(register.getStatus()) && !"ACTIVE".equals(register.getStatus())) {
            throw new BizException("仅已确认或已生效的应收登记表可生成账单");
        }
        if (register.getTenantRefId() == null
                || (register.getSpaceId() == null && register.getRoomId() == null)
                || register.getContractStartDate() == null || register.getContractEndDate() == null
                || register.getContractEndDate().isBefore(register.getContractStartDate())) {
            throw new BizException("应收登记表的租户、空间或合同期限尚未完整绑定");
        }
        return register;
    }

    private List<Bill> buildBills(ReceivableRegister register, List<ReceivableRule> rules) {
        List<Bill> bills = new ArrayList<>();
        YearMonth first = YearMonth.from(register.getContractStartDate());
        YearMonth last = YearMonth.from(register.getContractEndDate());
        for (YearMonth period = first; !period.isAfter(last); period = period.plusMonths(1)) {
            bills.add(monthlyBill(register, rules, "RENT", "租金", period));
            bills.add(monthlyBill(register, rules, "PROPERTY", "物业费", period));
        }
        if (positive(register.getRentDeposit())) {
            bills.add(depositBill(register, "RENT_DEPOSIT", "租金保证金", register.getRentDeposit()));
        }
        if (positive(register.getPropertyDeposit())) {
            bills.add(depositBill(register, "PROPERTY_DEPOSIT", "物业保证金", register.getPropertyDeposit()));
        }
        return bills;
    }

    private Bill monthlyBill(ReceivableRegister register, List<ReceivableRule> rules,
                             String feeCode, String feeName, YearMonth period) {
        BigDecimal amount = calculator.amountForMonth(register, rules, feeCode, period);
        LocalDate periodStart = max(register.getContractStartDate(), period.atDay(1));
        LocalDate periodEnd = min(register.getContractEndDate(), period.atEndOfMonth());
        int version = version(register);
        Bill bill = baseBill(register);
        bill.setBillingKey("receivable:%d:v%d:%s:%s".formatted(register.getId(), version, feeCode, period));
        bill.setCode("RR%dV%d%s%s".formatted(register.getId(), version,
                "RENT".equals(feeCode) ? "R" : "P", period.toString().replace("-", "")));
        bill.setFeeType(feeName);
        bill.setAmount(amount);
        bill.setPeriodStart(periodStart);
        bill.setPeriodEnd(periodEnd);
        bill.setDueDate(calculator.dueDate(register, period));
        List<ReceivableRule> feeRules = rules.stream()
                .filter(rule -> feeCode.equals(rule.getFeeType())).toList();
        bill.setReceivableRuleId(feeRules.stream()
                .filter(rule -> "AUTHORITATIVE_MONTHLY".equals(rule.getRuleType()))
                .map(ReceivableRule::getId).filter(Objects::nonNull).findFirst().orElse(null));
        bill.setRemark(ruleRemark(register, feeRules));
        return bill;
    }

    private Bill depositBill(ReceivableRegister register, String feeCode,
                             String feeName, BigDecimal amount) {
        int version = version(register);
        Bill bill = baseBill(register);
        bill.setBillingKey("receivable:%d:v%d:%s".formatted(register.getId(), version, feeCode));
        bill.setCode("RR%dV%d%s".formatted(register.getId(), version,
                "RENT_DEPOSIT".equals(feeCode) ? "RD" : "PD"));
        bill.setFeeType(feeName);
        bill.setAmount(amount.setScale(2, java.math.RoundingMode.HALF_UP));
        bill.setPeriodStart(register.getContractStartDate());
        bill.setPeriodEnd(register.getContractStartDate());
        bill.setDueDate(calculator.dueDate(register, YearMonth.from(register.getContractStartDate())));
        bill.setRemark("来源应收登记表 " + register.getInternalCode());
        return bill;
    }

    private Bill baseBill(ReceivableRegister register) {
        Bill bill = new Bill();
        bill.setTenantId(register.getTenantId());
        bill.setContractId(register.getContractId());
        bill.setReceivableRegisterId(register.getId());
        bill.setTenantRefId(register.getTenantRefId());
        bill.setRoomId(register.getRoomId());
        bill.setDirection(1);
        bill.setSource("应收登记表");
        bill.setStatus(3);
        bill.setPaidAmount(BigDecimal.ZERO.setScale(2));
        bill.setLateFee(BigDecimal.ZERO.setScale(2));
        bill.setOverdueDays(0);
        bill.setInvoiceStatus(0);
        return bill;
    }

    private String ruleRemark(ReceivableRegister register, List<ReceivableRule> rules) {
        String ids = rules.stream().map(ReceivableRule::getId).filter(Objects::nonNull)
                .map(String::valueOf).collect(Collectors.joining(","));
        return "来源应收登记表 " + register.getInternalCode()
                + (ids.isEmpty() ? "" : "；规则ID " + ids);
    }

    private Bill find(String billingKey) {
        return billMapper.selectOne(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getBillingKey, billingKey));
    }

    private static boolean canSynchronize(Bill bill) {
        BigDecimal paid = bill.getPaidAmount() == null ? BigDecimal.ZERO : bill.getPaidAmount();
        return paid.signum() == 0 && (bill.getStatus() == null
                || bill.getStatus() == 1 || bill.getStatus() == 2
                || bill.getStatus() == 3 || bill.getStatus() == 6);
    }

    private static int version(ReceivableRegister register) {
        return register.getSourceVersion() == null ? 1 : register.getSourceVersion();
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static LocalDate max(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private static LocalDate min(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }
}
