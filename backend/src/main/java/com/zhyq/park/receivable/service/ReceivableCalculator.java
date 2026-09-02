package com.zhyq.park.receivable.service;

import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.entity.ReceivableRule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ReceivableCalculator {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final Pattern DAY_OF_MONTH = Pattern.compile("当月\\s*(\\d{1,2})\\s*(?:日|号)前.*当月");

    public BigDecimal baseMonthlyAmount(ReceivableRuleParser.Rate rate, BigDecimal chargeArea) {
        BigDecimal area = zero(chargeArea);
        BigDecimal result = switch (rate.unit()) {
            case "DAY_SQM" -> rate.value().multiply(area).multiply(new BigDecimal("30"));
            case "MONTH_SQM" -> rate.value().multiply(area);
            case "MONTH_FIXED" -> rate.value();
            default -> throw new IllegalArgumentException("不支持的单价单位: " + rate.unit());
        };
        return money(result);
    }

    public BigDecimal amountForMonth(ReceivableRegister register, List<ReceivableRule> allRules,
                                     String feeType, YearMonth period) {
        List<ReceivableRule> rules = new java.util.ArrayList<>(
                Optional.ofNullable(allRules).orElse(List.of()).stream()
                .filter(rule -> feeType.equals(rule.getFeeType()))
                .filter(this::active)
                .toList());
        // ver5.1 允许登记明细先于正式合同建档。旧数据可能完全没有规则，或只有
        // 基础金额而缺少免租规则；从原始合同条款补齐缺项，避免整月租金误计。
        for (ReceivableRule inferred : inferRules(register, feeType)) {
            if (rules.stream().noneMatch(existing -> equivalent(existing, inferred))) {
                rules.add(inferred);
            }
        }
        List<ReceivableRule> resolvedRules = rules;
        BigDecimal base = resolvedRules.stream()
                .filter(rule -> "AUTHORITATIVE_MONTHLY".equals(rule.getRuleType()))
                .map(ReceivableRule::getFixedAmount)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> fallbackBase(register, resolvedRules, feeType));
        LocalDate contractStart = Optional.ofNullable(register.getContractStartDate())
                .orElse(period.atDay(1));
        LocalDate contractEnd = Optional.ofNullable(register.getContractEndDate())
                .orElse(period.atEndOfMonth());
        return applyRules(base, period, contractStart, contractEnd, resolvedRules);
    }

    public BigDecimal applyRules(BigDecimal baseAmount, YearMonth period, LocalDate contractStart,
                                 List<ReceivableRule> rules) {
        return applyRules(baseAmount, period, contractStart, period.atEndOfMonth(), rules);
    }

    private BigDecimal applyRules(BigDecimal baseAmount, YearMonth period, LocalDate contractStart,
                                  LocalDate contractEnd, List<ReceivableRule> rules) {
        LocalDate activeStart = max(contractStart, period.atDay(1));
        LocalDate activeEnd = min(contractEnd, period.atEndOfMonth());
        if (activeEnd.isBefore(activeStart)) return money(BigDecimal.ZERO);

        BigDecimal dailyBase = zero(baseAmount).divide(
                BigDecimal.valueOf(period.lengthOfMonth()), 12, RoundingMode.HALF_UP);
        BigDecimal amount = BigDecimal.ZERO;
        for (LocalDate day = activeStart; !day.isAfter(activeEnd); day = day.plusDays(1)) {
            LocalDate current = day; // lambda 只能捕获 effectively-final 变量
            BigDecimal daily = applyEscalation(dailyBase, contractStart, day, rules);
            if (rules.stream().anyMatch(rule -> isWaived(rule, current, contractStart))) continue;
            for (ReceivableRule rule : rules) {
                if (!appliesToDate(rule, day) || !"DISCOUNT".equals(rule.getRuleType())
                        || rule.getDiscountRate() == null) continue;
                daily = daily.multiply(rule.getDiscountRate())
                        .divide(ONE_HUNDRED, 12, RoundingMode.HALF_UP);
            }
            amount = amount.add(daily);
        }

        for (ReceivableRule rule : rules) {
            if (!active(rule) || rule.getFixedAmount() == null
                    || (!"OFFSET".equals(rule.getRuleType())
                    && !"FIXED_ADJUSTMENT".equals(rule.getRuleType()))) continue;
            long overlapDays = overlapDays(rule, activeStart, activeEnd);
            if (overlapDays <= 0) continue;
            BigDecimal adjustment = rule.getFixedAmount()
                    .multiply(BigDecimal.valueOf(overlapDays))
                    .divide(BigDecimal.valueOf(period.lengthOfMonth()), 12, RoundingMode.HALF_UP);
            amount = "OFFSET".equals(rule.getRuleType())
                    ? amount.subtract(adjustment) : amount.add(adjustment);
        }
        return money(amount.max(BigDecimal.ZERO));
    }

    private BigDecimal applyEscalation(BigDecimal amount, LocalDate contractStart, LocalDate day,
                                       List<ReceivableRule> rules) {
        BigDecimal result = amount;
        for (ReceivableRule rule : rules) {
            if (!active(rule) || !"ESCALATION".equals(rule.getRuleType())
                    || rule.getIntervalYears() == null || rule.getIntervalYears() <= 0
                    || rule.getIncreaseRate() == null) continue;
            long completeYears = Math.max(0, ChronoUnit.YEARS.between(contractStart, day));
            int steps = (int) (completeYears / rule.getIntervalYears());
            if (steps > 0) {
                BigDecimal factor = BigDecimal.ONE.add(
                        rule.getIncreaseRate().divide(ONE_HUNDRED, 12, RoundingMode.HALF_UP));
                result = result.multiply(factor.pow(steps));
            }
        }
        return result;
    }

    public LocalDate dueDate(ReceivableRegister register, YearMonth period) {
        String timing = Optional.ofNullable(register.getCollectionTimingRaw()).orElse("")
                .replaceAll("\\s+", "");
        LocalDate due;
        if (timing.contains("当月30日前收取下个月")) {
            YearMonth previous = period.minusMonths(1);
            due = previous.atDay(Math.min(30, previous.lengthOfMonth()));
        } else {
            Matcher matcher = DAY_OF_MONTH.matcher(timing);
            if (matcher.find()) {
                int day = Integer.parseInt(matcher.group(1));
                due = period.atDay(Math.min(day, period.lengthOfMonth()));
            } else {
                due = period.atDay(1);
            }
        }
        LocalDate firstCollection = parseDate(register.getFirstCollectionRaw());
        return firstCollection != null && due.isBefore(firstCollection) ? firstCollection : due;
    }

    private BigDecimal fallbackBase(ReceivableRegister register, List<ReceivableRule> rules, String feeType) {
        BigDecimal authoritative = "RENT".equals(feeType)
                ? register.getMonthlyRent() : register.getMonthlyProperty();
        if (authoritative != null) return authoritative;
        return rules.stream()
                .filter(rule -> "BASE_RATE".equals(rule.getRuleType()))
                .filter(rule -> rule.getRateValue() != null && rule.getRateUnit() != null)
                .findFirst()
                .map(rule -> baseMonthlyAmount(
                        new ReceivableRuleParser.Rate(rule.getRateUnit(), rule.getRateValue(), true),
                        register.getChargeArea()))
                .orElse(BigDecimal.ZERO);
    }

    private boolean isWaived(ReceivableRule rule, LocalDate day, LocalDate contractStart) {
        if (!active(rule)) return false;
        if ("WAIVER".equals(rule.getRuleType())) return appliesToDate(rule, day);
        if (!"RECURRING_WAIVER".equals(rule.getRuleType())
                || !"YEARLY_LAST_MONTH".equals(rule.getRecurrenceRule())
                || !appliesToDate(rule, day)) {
            return false;
        }
        // 「每年最后一个月免租」= 每个合同年度的最后一个月(6月起租免5月、7月起租免6月),
        // 不是自然年 12 月:云山四份补充协议的「合同租金总额」只有按合同年度口径才对得平
        long monthIndex = ChronoUnit.MONTHS.between(
                YearMonth.from(contractStart), YearMonth.from(day));
        return monthIndex >= 0 && monthIndex % 12 == 11;
    }

    private boolean appliesToDate(ReceivableRule rule, LocalDate day) {
        if (!active(rule)) return false;
        return (rule.getEffectiveStart() == null || !rule.getEffectiveStart().isAfter(day))
                && (rule.getEffectiveEnd() == null || !rule.getEffectiveEnd().isBefore(day));
    }

    private long overlapDays(ReceivableRule rule, LocalDate activeStart, LocalDate activeEnd) {
        LocalDate start = rule.getEffectiveStart() == null
                ? activeStart : max(activeStart, rule.getEffectiveStart());
        LocalDate end = rule.getEffectiveEnd() == null
                ? activeEnd : min(activeEnd, rule.getEffectiveEnd());
        return end.isBefore(start) ? 0 : ChronoUnit.DAYS.between(start, end) + 1;
    }

    private List<ReceivableRule> inferRules(ReceivableRegister register, String feeType) {
        ReceivableRuleParser parser = new ReceivableRuleParser();
        java.util.ArrayList<ReceivableRule> inferred = new java.util.ArrayList<>();
        parser.parseEscalation(register.getEscalationRaw()).ifPresent(value -> {
            ReceivableRule rule = rule(feeType, "ESCALATION");
            rule.setIntervalYears(value.intervalYears());
            rule.setIncreaseRate(value.increasePercent());
            inferred.add(rule);
        });
        String combined = text(register.getFreePeriodRaw()) + "；" + text(register.getDiscountRaw());
        boolean recurringLastMonth = parser.isYearlyLastMonthWaiver(combined);
        boolean waiveProperty = containsAny(register.getDiscountRaw(),
                "免物业", "无需支付物业", "免缴物业", "物业管理费按0");
        if ("RENT".equals(feeType) || waiveProperty) {
            List<ReceivableRuleParser.DateRange> freeRanges = new java.util.ArrayList<>(
                    parser.parseDateRanges(register.getFreePeriodRaw()));
            // 免租月按年循环时,「免租期N个月」是循环月数的合计,不能当成起租日起连免N个月
            if (freeRanges.isEmpty() && !recurringLastMonth && register.getContractStartDate() != null) {
                parser.parseMonthCount(register.getFreeTermRaw()).ifPresent(months -> freeRanges.add(
                        new ReceivableRuleParser.DateRange(register.getContractStartDate(),
                                register.getContractStartDate().plusMonths(months).minusDays(1))));
            }
            freeRanges.forEach(range -> inferred.add(dateRule(feeType, "WAIVER", range)));
        }
        if ("RENT".equals(feeType) && recurringLastMonth) {
            ReceivableRule rule = rule(feeType, "RECURRING_WAIVER");
            rule.setRecurrenceRule("YEARLY_LAST_MONTH");
            parser.parseRecurringWaiverStart(combined).ifPresent(rule::setEffectiveStart);
            inferred.add(rule);
        }
        for (String clause : text(register.getDiscountRaw()).split("[；;\\n]+")) {
            List<ReceivableRuleParser.DateRange> ranges = parser.parseDateRanges(clause);
            if (ranges.isEmpty() || !mentionsFee(clause, feeType)) continue;
            parser.parseDiscountRate(clause).ifPresent(rate -> ranges.forEach(range -> {
                ReceivableRule rule = dateRule(feeType, "DISCOUNT", range);
                rule.setDiscountRate(rate);
                inferred.add(rule);
            }));
            boolean offset = clause.contains("抵扣");
            if (offset) ranges.forEach(range -> {
                ReceivableRule rule = dateRule(feeType, "OFFSET", range);
                BigDecimal monthly = "RENT".equals(feeType)
                        ? register.getMonthlyRent() : register.getMonthlyProperty();
                rule.setFixedAmount(parser.parseCurrencyAmount(clause).map(total -> {
                    long months = ChronoUnit.MONTHS.between(
                            YearMonth.from(range.start()), YearMonth.from(range.end())) + 1;
                    return months <= 0 ? total : total.divide(
                            BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
                }).orElse(zero(monthly)));
                inferred.add(rule);
            });
            boolean waiver = !offset && (containsAny(clause, "免租期", "免租金", "无需支付租赁费")
                    || ("PROPERTY".equals(feeType)
                    && containsAny(clause, "免缴", "无需支付", "免物业", "免管理费")));
            if (waiver) ranges.forEach(range -> inferred.add(dateRule(feeType, "WAIVER", range)));
        }
        return inferred;
    }

    private static ReceivableRule rule(String feeType, String ruleType) {
        ReceivableRule rule = new ReceivableRule();
        rule.setFeeType(feeType);
        rule.setRuleType(ruleType);
        rule.setStatus("ACTIVE");
        return rule;
    }

    private static ReceivableRule dateRule(String feeType, String ruleType,
                                           ReceivableRuleParser.DateRange range) {
        ReceivableRule rule = rule(feeType, ruleType);
        rule.setEffectiveStart(range.start());
        rule.setEffectiveEnd(range.end());
        return rule;
    }

    private static boolean equivalent(ReceivableRule first, ReceivableRule second) {
        return Objects.equals(first.getFeeType(), second.getFeeType())
                && Objects.equals(first.getRuleType(), second.getRuleType())
                && Objects.equals(first.getEffectiveStart(), second.getEffectiveStart())
                && Objects.equals(first.getEffectiveEnd(), second.getEffectiveEnd())
                && numericEquals(first.getDiscountRate(), second.getDiscountRate())
                && numericEquals(first.getFixedAmount(), second.getFixedAmount())
                && Objects.equals(first.getIntervalYears(), second.getIntervalYears())
                && numericEquals(first.getIncreaseRate(), second.getIncreaseRate())
                && Objects.equals(first.getRecurrenceRule(), second.getRecurrenceRule());
    }

    private static boolean numericEquals(BigDecimal first, BigDecimal second) {
        return first == null ? second == null : second != null && first.compareTo(second) == 0;
    }

    private static boolean mentionsFee(String raw, String feeType) {
        boolean property = containsAny(raw, "物业", "管理费");
        boolean rent = containsAny(raw, "租金", "租赁费", "免租");
        return "PROPERTY".equals(feeType) ? property : rent || !property;
    }

    private static boolean containsAny(String raw, String... tokens) {
        if (raw == null || raw.isBlank()) return false;
        for (String token : tokens) if (raw.contains(token)) return true;
        return false;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private boolean active(ReceivableRule rule) {
        return rule != null && (rule.getStatus() == null || "ACTIVE".equals(rule.getStatus()));
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() < 8) return null;
        try {
            return LocalDate.of(
                    Integer.parseInt(digits.substring(0, 4)),
                    Integer.parseInt(digits.substring(4, 6)),
                    Integer.parseInt(digits.substring(6, 8)));
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate max(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private static LocalDate min(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }
}
