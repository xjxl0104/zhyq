package com.zhyq.park.receivable.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ReceivableRuleParser {
    private static final Pattern NUMBER = Pattern.compile("(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern ESCALATION = Pattern.compile("每\\s*(\\d+)\\s*年[^%]*?(-?\\d+(?:\\.\\d+)?)\\s*%");

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

    public record DateRange(LocalDate start, LocalDate end) {}
    public record Rate(String unit, BigDecimal value, boolean taxIncluded) {}
    public record Escalation(int intervalYears, BigDecimal increasePercent) {}
    public record Account(String accountName, String bankName, String accountNo, String raw) {}
}
