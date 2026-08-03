package com.zhyq.park.receivable;

import com.zhyq.park.receivable.service.ReceivableRuleParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

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
}
