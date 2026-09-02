package com.zhyq.park.receivable.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ReceivableRuleParser {
    private static final Pattern NUMBER = Pattern.compile("(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern ESCALATION = Pattern.compile("每\\s*(\\d+)\\s*年[^%]*?(-?\\d+(?:\\.\\d+)?)\\s*%");
    private static final Pattern COMPACT_DATE_RANGE = Pattern.compile(
            "(\\d{8})\\s*[-—~～至]\\s*(\\d{8})");
    private static final Pattern SEPARATED_DATE_RANGE = Pattern.compile(
            "(\\d{4})[./年-](\\d{1,2})[./月-](\\d{1,2})日?\\s*(?:至|到|[-—~～])\\s*"
                    + "(\\d{4})[./年-](\\d{1,2})[./月-](\\d{1,2})日?");
    private static final Pattern DISCOUNT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*折");
    private static final Pattern CURRENCY_AMOUNT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*元");
    private static final Pattern MONTH_COUNT = Pattern.compile("(\\d+)\\s*个?月");
    private static final Pattern YEAR_FROM = Pattern.compile("(\\d{4})\\s*年起");

    public Optional<DateRange> parseContractTerm(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() != 16) {
            return Optional.empty();
        }
        try {
            return Optional.of(new DateRange(parseDate(digits.substring(0, 8)), parseDate(digits.substring(8))));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    public Optional<Rate> parseRate(String raw) {
        if (raw == null || !raw.contains("平方")) {
            return Optional.empty();
        }
        Matcher matcher = NUMBER.matcher(raw);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String unit;
        if (raw.contains("每日") || raw.contains("每天") || raw.contains("/日")) {
            unit = "DAY_SQM";
        } else if (raw.contains("每月") || raw.contains("/月")) {
            unit = "MONTH_SQM";
        } else {
            return Optional.empty();
        }
        return Optional.of(new Rate(unit, new BigDecimal(matcher.group(1)), raw.contains("含税")));
    }

    public Optional<Escalation> parseEscalation(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher matcher = ESCALATION.matcher(raw);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(new Escalation(
                Integer.parseInt(matcher.group(1)), new BigDecimal(matcher.group(2)).abs()));
    }

    public List<DateRange> parseDateRanges(String raw) {
        List<DateRange> ranges = new ArrayList<>();
        if (raw == null || raw.isBlank()) return ranges;
        Matcher compact = COMPACT_DATE_RANGE.matcher(raw);
        while (compact.find()) {
            addRange(ranges, compact.group(1), compact.group(2));
        }
        Matcher separated = SEPARATED_DATE_RANGE.matcher(raw);
        while (separated.find()) {
            try {
                ranges.add(new DateRange(
                        LocalDate.of(Integer.parseInt(separated.group(1)), Integer.parseInt(separated.group(2)),
                                Integer.parseInt(separated.group(3))),
                        LocalDate.of(Integer.parseInt(separated.group(4)), Integer.parseInt(separated.group(5)),
                                Integer.parseInt(separated.group(6)))));
            } catch (DateTimeException ignored) {
                // Invalid source dates remain available in raw_text for review.
            }
        }
        return ranges;
    }

    public Optional<BigDecimal> parseDiscountRate(String raw) {
        if (raw == null) return Optional.empty();
        Matcher matcher = DISCOUNT.matcher(raw);
        if (!matcher.find()) return Optional.empty();
        BigDecimal percent = new BigDecimal(matcher.group(1)).multiply(BigDecimal.TEN);
        return percent.signum() >= 0 && percent.compareTo(new BigDecimal("100")) <= 0
                ? Optional.of(percent) : Optional.empty();
    }

    public boolean isYearlyLastMonthWaiver(String raw) {
        return raw != null && raw.replaceAll("\\s+", "").contains("每年最后一个月免租一个月");
    }

    /** 「2027年起…每年最后一个月免租」里的生效年份,循环免租从该年 1 月 1 日起才适用 */
    public Optional<LocalDate> parseRecurringWaiverStart(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher matcher = YEAR_FROM.matcher(raw.replaceAll("\\s+", ""));
        return matcher.find()
                ? Optional.of(LocalDate.of(Integer.parseInt(matcher.group(1)), 1, 1))
                : Optional.empty();
    }

    public Optional<Integer> parseMonthCount(String raw) {
        if (raw == null) return Optional.empty();
        Matcher matcher = MONTH_COUNT.matcher(raw);
        if (!matcher.find()) return Optional.empty();
        int months = Integer.parseInt(matcher.group(1));
        return months > 0 ? Optional.of(months) : Optional.empty();
    }

    public Optional<BigDecimal> parseCurrencyAmount(String raw) {
        if (raw == null) return Optional.empty();
        Matcher matcher = CURRENCY_AMOUNT.matcher(raw.replace(",", "").replace("，", ""));
        return matcher.find() ? Optional.of(new BigDecimal(matcher.group(1))) : Optional.empty();
    }

    public Optional<Account> parseAccount(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (String part : raw.split("[;；\\n]") ) {
            String[] pair = part.split("[:：]", 2);
            if (pair.length == 2) {
                fields.put(pair[0].replaceAll("\\s+", ""), pair[1].trim());
            }
        }
        String name = first(fields, "户名", "账户名", "账户名称");
        String bank = first(fields, "开户行", "银行", "开户银行");
        String number = first(fields, "账号", "帐号", "账户");
        if (name == null || bank == null || number == null) {
            return Optional.empty();
        }
        return Optional.of(new Account(name, bank, number.replaceAll("\\s+", ""), raw));
    }

    private static String first(Map<String, String> fields, String... keys) {
        for (String key : keys) {
            String value = fields.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static LocalDate parseDate(String digits) {
        return LocalDate.of(
                Integer.parseInt(digits.substring(0, 4)),
                Integer.parseInt(digits.substring(4, 6)),
                Integer.parseInt(digits.substring(6, 8)));
    }

    private static void addRange(List<DateRange> ranges, String start, String end) {
        try {
            ranges.add(new DateRange(parseDate(start), parseDate(end)));
        } catch (DateTimeException ignored) {
            // Invalid source dates remain available in raw_text for review.
        }
    }

    public record DateRange(LocalDate start, LocalDate end) {}
    public record Rate(String unit, BigDecimal value, boolean taxIncluded) {}
    public record Escalation(int intervalYears, BigDecimal increasePercent) {}
    public record Account(String accountName, String bankName, String accountNo, String raw) {}
}
