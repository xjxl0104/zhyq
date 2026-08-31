package com.zhyq.park.receivable;

import com.zhyq.park.receivable.service.ReceivableRuleParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceivableRuleParserTest {
    private final ReceivableRuleParser parser = new ReceivableRuleParser();

    @Test
    void parsesContractTermRatesAndEscalation() {
        assertEquals(new ReceivableRuleParser.DateRange(
                        LocalDate.of(2026, 5, 1), LocalDate.of(2032, 4, 30)),
                parser.parseContractTerm("20260501-20320430").orElseThrow());
        assertEquals(new ReceivableRuleParser.Rate("DAY_SQM", new BigDecimal("0.4"), true),
                parser.parseRate("每日每平方含税0.4元").orElseThrow());
        assertEquals(new ReceivableRuleParser.Rate("MONTH_SQM", new BigDecimal("13"), true),
                parser.parseRate("每月每平方含税13元").orElseThrow());
        assertEquals(new ReceivableRuleParser.Escalation(3, new BigDecimal("10")),
                parser.parseEscalation("每3年-10%").orElseThrow());
    }

    @Test
    void parsesLabeledAccountWithChineseOrAsciiColons() {
        var account = parser.parseAccount("户名：示例公司；开户行:示例银行;账号：622200000001").orElseThrow();
        assertEquals("示例公司", account.accountName());
        assertEquals("示例银行", account.bankName());
        assertEquals("622200000001", account.accountNo());
        assertTrue(parser.parseAccount("只有一串数字622200000001").isEmpty());
    }

    @Test
    void parsesAllExplicitWaiverRangesAndChineseDateRanges() {
        assertEquals(List.of(
                        new ReceivableRuleParser.DateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30)),
                        new ReceivableRuleParser.DateRange(LocalDate.of(2027, 12, 1), LocalDate.of(2027, 12, 31)),
                        new ReceivableRuleParser.DateRange(LocalDate.of(2028, 12, 1), LocalDate.of(2028, 12, 31))),
                parser.parseDateRanges("20260801-20260930\n20271201-20271231\n20281201-20281231"));
        assertEquals(List.of(new ReceivableRuleParser.DateRange(
                        LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))),
                parser.parseDateRanges("2026年11月1日至2026年11月30日"));
    }

    @Test
    void parsesSpreadsheetDiscountAndRecurringTerms() {
        assertEquals(new BigDecimal("50"), parser.parseDiscountRate("租金按5折").orElseThrow());
        assertEquals(new BigDecimal("70"), parser.parseDiscountRate("租金及物业管理费按7折收取").orElseThrow());
        assertTrue(parser.isYearlyLastMonthWaiver("每年最后一个月免租一个月"));
        assertEquals(2, parser.parseMonthCount("免租期 2个月").orElseThrow());
    }
}
