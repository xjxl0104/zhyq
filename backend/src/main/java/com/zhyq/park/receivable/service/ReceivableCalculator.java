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
        List<ReceivableRule> rules = allRules.stream()
                .filter(rule -> feeType.equals(rule.getFeeType()))
                .filter(this::active)
                .toList();
        BigDecimal base = rules.stream()
                .filter(rule -> "AUTHORITATIVE_MONTHLY".equals(rule.getRuleType()))
                .map(ReceivableRule::getFixedAmount)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> fallbackBase(register, rules, feeType));
        LocalDate contractStart = Optional.ofNullable(register.getContractStartDate())
                .orElse(period.atDay(1));
        return applyRules(base, period, contractStart, rules);
    }

    public BigDecimal applyRules(BigDecimal baseAmount, YearMonth period, LocalDate contractStart,
                                 List<ReceivableRule> rules) {
        BigDecimal amount = zero(baseAmount);

        for (ReceivableRule rule : rules) {
            if (!active(rule) || !"ESCALATION".equals(rule.getRuleType())
                    || rule.getIntervalYears() == null || rule.getIntervalYears() <= 0
                    || rule.getIncreaseRate() == null) {
                continue;
            }
            long completeYears = Math.max(0, ChronoUnit.YEARS.between(contractStart, period.atEndOfMonth()));
            int steps = (int) (completeYears / rule.getIntervalYears());
            if (steps > 0) {
                BigDecimal factor = BigDecimal.ONE.add(
                        rule.getIncreaseRate().divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP));
                amount = amount.multiply(factor.pow(steps));
            }
        }

        if (rules.stream().anyMatch(rule -> isWaived(rule, period))) {
            return money(BigDecimal.ZERO);
        }
        for (ReceivableRule rule : rules) {
            if (!appliesToPeriod(rule, period) || !"DISCOUNT".equals(rule.getRuleType())
                    || rule.getDiscountRate() == null) {
                continue;
            }
            amount = amount.multiply(rule.getDiscountRate())
                    .divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP);
        }
        for (ReceivableRule rule : rules) {
            if (!appliesToPeriod(rule, period) || rule.getFixedAmount() == null) continue;
            if ("OFFSET".equals(rule.getRuleType())) amount = amount.subtract(rule.getFixedAmount());
            if ("FIXED_ADJUSTMENT".equals(rule.getRuleType())) amount = amount.add(rule.getFixedAmount());
        }
        return money(amount.max(BigDecimal.ZERO));
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

    private boolean isWaived(ReceivableRule rule, YearMonth period) {
        if (!active(rule)) return false;
        if ("WAIVER".equals(rule.getRuleType())) return appliesToPeriod(rule, period);
        return "RECURRING_WAIVER".equals(rule.getRuleType())
                && "YEARLY_LAST_MONTH".equals(rule.getRecurrenceRule())
                && period.getMonthValue() == 12;
    }

    private boolean appliesToPeriod(ReceivableRule rule, YearMonth period) {
        if (!active(rule)) return false;
        LocalDate start = period.atDay(1);
        LocalDate end = period.atEndOfMonth();
        return (rule.getEffectiveStart() == null || !rule.getEffectiveStart().isAfter(end))
                && (rule.getEffectiveEnd() == null || !rule.getEffectiveEnd().isBefore(start));
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
}
