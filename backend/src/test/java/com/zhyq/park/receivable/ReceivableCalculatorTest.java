package com.zhyq.park.receivable;

import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.entity.ReceivableRule;
import com.zhyq.park.receivable.service.ReceivableCalculator;
import com.zhyq.park.receivable.service.ReceivableRuleParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReceivableCalculatorTest {
    private final ReceivableCalculator calculator = new ReceivableCalculator();

    @Test
    void dailyRateUsesThirtyDayConvention() {
        assertEquals(new BigDecimal("56400.00"), calculator.baseMonthlyAmount(
                new ReceivableRuleParser.Rate("DAY_SQM", new BigDecimal("0.4"), true),
                new BigDecimal("4700")));
    }

    @Test
    void appliesThreeYearEscalationThenHalfDiscount() {
        ReceivableRule escalation = rule("ESCALATION");
        escalation.setIntervalYears(3);
        escalation.setIncreaseRate(new BigDecimal("10"));
        ReceivableRule discount = rule("DISCOUNT");
        discount.setEffectiveStart(LocalDate.of(2029, 6, 1));
        discount.setEffectiveEnd(LocalDate.of(2029, 6, 30));
        discount.setDiscountRate(new BigDecimal("50"));

        BigDecimal amount = calculator.applyRules(
                new BigDecimal("100000"), YearMonth.of(2029, 6),
                LocalDate.of(2026, 5, 1), List.of(escalation, discount));

        assertEquals(new BigDecimal("55000.00"), amount);
    }

    @Test
    void recurringLastMonthWaiverReturnsZero() {
        ReceivableRule waiver = rule("RECURRING_WAIVER");
        waiver.setRecurrenceRule("YEARLY_LAST_MONTH");

        assertEquals(new BigDecimal("0.00"), calculator.applyRules(
                new BigDecimal("100000"), YearMonth.of(2028, 12),
                LocalDate.of(2026, 6, 1), List.of(waiver)));
    }

    @Test
    void offsetSubtractsFixedAmountInsteadOfWaivingWholeMonth() {
        ReceivableRule offset = rule("OFFSET");
        offset.setEffectiveStart(LocalDate.of(2026, 8, 1));
        offset.setEffectiveEnd(LocalDate.of(2026, 8, 31));
        offset.setFixedAmount(new BigDecimal("1000"));

        assertEquals(new BigDecimal("9000.00"), calculator.applyRules(
                new BigDecimal("10000"), YearMonth.of(2026, 8),
                LocalDate.of(2026, 5, 1), List.of(offset)));
    }

    @Test
    void authoritativeMonthlyAmountIsTheBaseForEscalation() {
        ReceivableRegister register = new ReceivableRegister();
        register.setContractStartDate(LocalDate.of(2026, 5, 1));
        register.setMonthlyRent(new BigDecimal("99999"));
        ReceivableRule authoritative = rule("AUTHORITATIVE_MONTHLY");
        authoritative.setFeeType("RENT");
        authoritative.setFixedAmount(new BigDecimal("100000"));
        ReceivableRule escalation = rule("ESCALATION");
        escalation.setFeeType("RENT");
        escalation.setIntervalYears(3);
        escalation.setIncreaseRate(new BigDecimal("10"));

        assertEquals(new BigDecimal("110000.00"), calculator.amountForMonth(
                register, List.of(authoritative, escalation), "RENT", YearMonth.of(2029, 6)));
    }

    @Test
    void dueDateUsesPreviousMonthDayThirtyAndFirstCollectionFloor() {
        ReceivableRegister register = new ReceivableRegister();
        register.setCollectionTimingRaw("当月30日前收取下个月租金");
        register.setFirstCollectionRaw("20260501前租客开始支付租金");

        assertEquals(LocalDate.of(2026, 5, 1),
                calculator.dueDate(register, YearMonth.of(2026, 5)));
        assertEquals(LocalDate.of(2026, 5, 30),
                calculator.dueDate(register, YearMonth.of(2026, 6)));
    }

    private static ReceivableRule rule(String type) {
        ReceivableRule rule = new ReceivableRule();
        rule.setRuleType(type);
        rule.setStatus("ACTIVE");
        return rule;
    }
}
