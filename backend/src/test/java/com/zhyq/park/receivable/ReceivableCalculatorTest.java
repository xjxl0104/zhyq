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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void recurringLastMonthWaiverUsesLeaseYearNotCalendarDecember() {
        ReceivableRule waiver = rule("RECURRING_WAIVER");
        waiver.setRecurrenceRule("YEARLY_LAST_MONTH");

        // 6 月起租:合同年度最后一月是 5 月,免 5 月而不是自然年 12 月
        assertEquals(new BigDecimal("0.00"), calculator.applyRules(
                new BigDecimal("100000"), YearMonth.of(2029, 5),
                LocalDate.of(2026, 6, 1), List.of(waiver)));
        assertEquals(new BigDecimal("100000.00"), calculator.applyRules(
                new BigDecimal("100000"), YearMonth.of(2028, 12),
                LocalDate.of(2026, 6, 1), List.of(waiver)));
    }

    @Test
    void recurringWaiverHonorsEffectiveStartYear() {
        ReceivableRule waiver = rule("RECURRING_WAIVER");
        waiver.setRecurrenceRule("YEARLY_LAST_MONTH");
        waiver.setEffectiveStart(LocalDate.of(2027, 1, 1));

        // 1 月起租且「2027年起」生效:2026-12(首个合同年度末月)不免,2027-12 起才免
        assertEquals(new BigDecimal("100000.00"), calculator.applyRules(
                new BigDecimal("100000"), YearMonth.of(2026, 12),
                LocalDate.of(2026, 1, 1), List.of(waiver)));
        assertEquals(new BigDecimal("0.00"), calculator.applyRules(
                new BigDecimal("100000"), YearMonth.of(2027, 12),
                LocalDate.of(2026, 1, 1), List.of(waiver)));
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
    void infersFreePeriodFromLegacyRegisterWithoutPersistedRules() {
        ReceivableRegister register = new ReceivableRegister();
        register.setContractStartDate(LocalDate.of(2026, 5, 1));
        register.setContractEndDate(LocalDate.of(2032, 4, 30));
        register.setMonthlyRent(new BigDecimal("56400"));
        register.setMonthlyProperty(new BigDecimal("9447"));
        register.setFreePeriodRaw("2026.05.01-2026.06.30");
        ReceivableRule legacyBaseOnly = rule("AUTHORITATIVE_MONTHLY");
        legacyBaseOnly.setFeeType("RENT");
        legacyBaseOnly.setFixedAmount(new BigDecimal("56400.00"));

        assertEquals(new BigDecimal("0.00"), calculator.amountForMonth(
                register, List.of(legacyBaseOnly), "RENT", YearMonth.of(2026, 5)));
        assertEquals(new BigDecimal("9447.00"), calculator.amountForMonth(
                register, List.of(), "PROPERTY", YearMonth.of(2026, 5)));
    }

    @Test
    void proratesPartialMonthAndPartialFreePeriodByCalendarDay() {
        ReceivableRegister register = new ReceivableRegister();
        register.setContractStartDate(LocalDate.of(2026, 5, 10));
        register.setContractEndDate(LocalDate.of(2026, 5, 31));
        register.setMonthlyRent(new BigDecimal("31000"));
        register.setFreePeriodRaw("2026.05.10-2026.05.15");

        assertEquals(new BigDecimal("16000.00"), calculator.amountForMonth(
                register, List.of(), "RENT", YearMonth.of(2026, 5)));
    }

    @Test
    void derivesFreeRangeFromMonthCountWhenExactDatesAreMissing() {
        ReceivableRegister register = new ReceivableRegister();
        register.setContractStartDate(LocalDate.of(2026, 5, 1));
        register.setContractEndDate(LocalDate.of(2026, 12, 31));
        register.setMonthlyRent(new BigDecimal("56400"));
        register.setFreeTermRaw("2个月");

        assertEquals(new BigDecimal("0.00"), calculator.amountForMonth(
                register, List.of(), "RENT", YearMonth.of(2026, 5)));
        assertEquals(new BigDecimal("0.00"), calculator.amountForMonth(
                register, List.of(), "RENT", YearMonth.of(2026, 6)));
        assertEquals(new BigDecimal("56400.00"), calculator.amountForMonth(
                register, List.of(), "RENT", YearMonth.of(2026, 7)));
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

    /**
     * 云山 -001 补充协议(6 月起租)全合同期对账:免租月=每合同年度末月(5月)15次+首年11月,
     * 搬迁补助抵扣 5 个月,每 3 年+8%。逐月合计必须与登记表「合同租金总额」对平。
     */
    @Test
    void yunshanJuneStartLeaseReconcilesWithContractTotal() {
        ReceivableRegister register = yunshanRegister(
                LocalDate.of(2026, 6, 1), LocalDate.of(2041, 5, 31),
                "256500", "27000",
                "20260601-20261130为免缴期，无需支付物业管理服务费，但仍需全额支付公共事业费等其它应付费用（续下）；\n"
                        + "搬迁期补助：补助金额5个月租赁费，抵扣20260601-20261031租赁费；\n"
                        + "免租期计算：2026年11月1日至2026年11月30日，且2027年起至合同期满，每年最后一个月免租一个月，合计15个月；");

        // 2026-06~10 补助抵扣、11 月免租:不收;12 月起收第一笔全月租金
        assertEquals(new BigDecimal("0.00"), rent(register, YearMonth.of(2026, 6)));
        assertEquals(new BigDecimal("0.00"), rent(register, YearMonth.of(2026, 11)));
        assertEquals(new BigDecimal("256500.00"), rent(register, YearMonth.of(2026, 12)));
        // 免每个合同年度最后一月(5月),12 月照常收
        assertEquals(new BigDecimal("0.00"), rent(register, YearMonth.of(2027, 5)));
        assertEquals(new BigDecimal("256500.00"), rent(register, YearMonth.of(2027, 12)));
        assertEquals(new BigDecimal("0.00"), rent(register, YearMonth.of(2041, 5)));
        // 第 13 个合同年度起 ×1.08^4
        assertEquals(new BigDecimal("348965.42"), rent(register, YearMonth.of(2038, 6)));
        // 物业费:免缴期到 2026-11,12 月起每月收;免租月不含物业费
        assertEquals(new BigDecimal("0.00"), property(register, YearMonth.of(2026, 11)));
        assertEquals(new BigDecimal("27000.00"), property(register, YearMonth.of(2026, 12)));
        assertEquals(new BigDecimal("27000.00"), property(register, YearMonth.of(2027, 5)));

        BigDecimal total = totalRent(register, YearMonth.of(2026, 6), YearMonth.of(2041, 5));
        assertTrue(new BigDecimal("48118843.83").subtract(total).abs().compareTo(BigDecimal.ONE) < 0,
                "全期租金合计 " + total + " 应与合同租金总额 48118843.83 相差 <1 元");

        // -002 补充协议:同一日期结构,月租 168,750(12.5元/方),总额 31,657,134.10
        ReceivableRegister third = yunshanRegister(
                LocalDate.of(2026, 6, 1), LocalDate.of(2041, 5, 31),
                "168750", "27000", register.getDiscountRaw());
        BigDecimal thirdTotal = totalRent(third, YearMonth.of(2026, 6), YearMonth.of(2041, 5));
        assertTrue(new BigDecimal("31657134.10").subtract(thirdTotal).abs().compareTo(BigDecimal.ONE) < 0,
                "-002 全期租金合计 " + thirdTotal + " 应与合同租金总额 31657134.10 相差 <1 元");
    }

    /**
     * 云山 -004 补充协议(7 月起租)对账:免租月=每合同年度末月(6月)+首年12月,
     * 总额应对平 30,390,848.73。
     */
    @Test
    void yunshanJulyStartLeaseReconcilesWithContractTotal() {
        ReceivableRegister register = yunshanRegister(
                LocalDate.of(2026, 7, 1), LocalDate.of(2041, 6, 30),
                "162000", "27000",
                "20260701-20261231为免缴期，无需支付物业管理服务费，但仍需全额支付公共事业费等其它应付费用；（续下）\n"
                        + "搬迁期补助：补助金额5个月租赁费，抵扣20260701-20261130租赁费；\n"
                        + "免租期计算：2026年12月1日至2026年12月31日，且2027年起至合同期满，每年最后一个月免租一个月，合计15个月；");

        assertEquals(new BigDecimal("0.00"), rent(register, YearMonth.of(2026, 12)));
        assertEquals(new BigDecimal("162000.00"), rent(register, YearMonth.of(2027, 1)));
        // 免每个合同年度最后一月(6月),12 月(非首年)照常收
        assertEquals(new BigDecimal("0.00"), rent(register, YearMonth.of(2027, 6)));
        assertEquals(new BigDecimal("0.00"), rent(register, YearMonth.of(2041, 6)));
        assertEquals(new BigDecimal("220399.21"), rent(register, YearMonth.of(2038, 12)));

        BigDecimal total = totalRent(register, YearMonth.of(2026, 7), YearMonth.of(2041, 6));
        assertTrue(new BigDecimal("30390848.73").subtract(total).abs().compareTo(BigDecimal.ONE) < 0,
                "全期租金合计 " + total + " 应与合同租金总额 30390848.73 相差 <1 元");

        // -003 补充协议:同一日期结构,月租 105,600(8,800方×12元),总额 19,810,331.03
        ReceivableRegister sixth = yunshanRegister(
                LocalDate.of(2026, 7, 1), LocalDate.of(2041, 6, 30),
                "105600", "17600", register.getDiscountRaw());
        BigDecimal sixthTotal = totalRent(sixth, YearMonth.of(2026, 7), YearMonth.of(2041, 6));
        assertTrue(new BigDecimal("19810331.03").subtract(sixthTotal).abs().compareTo(BigDecimal.ONE) < 0,
                "-003 全期租金合计 " + sixthTotal + " 应与合同租金总额 19810331.03 相差 <1 元");
    }

    @Test
    void displayRulesLeadWithMonthlyBillingThenInferredTerms() {
        ReceivableRegister register = yunshanRegister(
                LocalDate.of(2026, 6, 1), LocalDate.of(2041, 5, 31),
                "256500", "27000",
                "免租期计算：2026年11月1日至2026年11月30日，且2027年起至合同期满，每年最后一个月免租一个月，合计15个月；");
        register.setRentRateRaw("每月每平方含税19元");
        register.setPropertyRateRaw("每月每平方含税2元");

        List<ReceivableRule> rules = calculator.displayRules(register);

        // 首两行固定是「按月计费」:每月每客户一张租金 + 一张物业费
        assertEquals("AUTHORITATIVE_MONTHLY", rules.get(0).getRuleType());
        assertEquals("RENT", rules.get(0).getFeeType());
        assertEquals(0, new BigDecimal("256500.00").compareTo(rules.get(0).getFixedAmount()));
        assertEquals("MONTH_SQM", rules.get(0).getRateUnit());
        assertTrue(rules.get(0).getRawText().contains("每月按客户生成一张租金账单"));
        assertEquals("PROPERTY", rules.get(1).getFeeType());
        assertEquals(0, new BigDecimal("27000.00").compareTo(rules.get(1).getFixedAmount()));
        // 免租/递增条款以同一推断源展示
        assertTrue(rules.stream().anyMatch(r -> "RECURRING_WAIVER".equals(r.getRuleType())));
        assertTrue(rules.stream().anyMatch(r -> "ESCALATION".equals(r.getRuleType())));
        assertTrue(rules.stream().allMatch(r -> r.getRawText() != null && !r.getRawText().isBlank()));
    }

    @Test
    void pairsDateOnlyLineWithFollowingDiscountLine() {
        // 李万能行的真实写法:日期一行、"租金按5折"另一行,拆行后也要配得上对
        ReceivableRegister register = new ReceivableRegister();
        register.setContractStartDate(LocalDate.of(2026, 5, 1));
        register.setContractEndDate(LocalDate.of(2032, 4, 30));
        register.setMonthlyRent(new BigDecimal("56400"));
        register.setMonthlyProperty(new BigDecimal("9447"));
        register.setFreePeriodRaw("20260501-20260531");
        register.setDiscountRaw("20260601-20260930\n租金按5折");

        assertEquals(new BigDecimal("0.00"), rent(register, YearMonth.of(2026, 5)));
        assertEquals(new BigDecimal("28200.00"), rent(register, YearMonth.of(2026, 6)));
        assertEquals(new BigDecimal("28200.00"), rent(register, YearMonth.of(2026, 9)));
        assertEquals(new BigDecimal("56400.00"), rent(register, YearMonth.of(2026, 10)));
        // 条款只写了租金打折,物业费不打折
        assertEquals(new BigDecimal("9447.00"), property(register, YearMonth.of(2026, 6)));
    }

    @Test
    void waivesPropertyDuringFreePeriodWhenPhrasedAsRentAndProperty() {
        // 昌泰行的真实措辞:"免租期内,无需支付租赁费及物业管理费"
        ReceivableRegister register = new ReceivableRegister();
        register.setContractStartDate(LocalDate.of(2026, 6, 1));
        register.setContractEndDate(LocalDate.of(2032, 5, 31));
        register.setMonthlyRent(new BigDecimal("56400"));
        register.setMonthlyProperty(new BigDecimal("9447"));
        register.setFreePeriodRaw("20260601-20260731");
        register.setDiscountRaw("拆迁费补助为3个月租金，抵扣20260801-20261031租金（续下）；\n"
                + "免租期内，无需支付租赁费及物业管理费，但承租人仍需全额支付水电等其它实际产生的费用；\n"
                + "每交租满12个月免租0.5个月");

        assertEquals(new BigDecimal("0.00"), rent(register, YearMonth.of(2026, 6)));
        assertEquals(new BigDecimal("0.00"), property(register, YearMonth.of(2026, 7)));
        // 拆迁补助抵扣只针对租金;物业费照收
        assertEquals(new BigDecimal("0.00"), rent(register, YearMonth.of(2026, 8)));
        assertEquals(new BigDecimal("9447.00"), property(register, YearMonth.of(2026, 8)));
        assertEquals(new BigDecimal("56400.00"), rent(register, YearMonth.of(2026, 11)));
    }

    private ReceivableRegister yunshanRegister(LocalDate start, LocalDate end,
                                               String monthlyRent, String monthlyProperty,
                                               String discountRaw) {
        ReceivableRegister register = new ReceivableRegister();
        register.setContractStartDate(start);
        register.setContractEndDate(end);
        register.setMonthlyRent(new BigDecimal(monthlyRent));
        register.setMonthlyProperty(new BigDecimal(monthlyProperty));
        register.setFreeTermRaw("15个月");
        register.setFreePeriodRaw("每年最后一个月免租一个月");
        register.setEscalationRaw("每3年-8%");
        register.setDiscountRaw(discountRaw);
        register.setCollectionTimingRaw("当月13号前缴纳当月租金");
        return register;
    }

    private BigDecimal rent(ReceivableRegister register, YearMonth period) {
        return calculator.amountForMonth(register, List.of(), "RENT", period);
    }

    private BigDecimal property(ReceivableRegister register, YearMonth period) {
        return calculator.amountForMonth(register, List.of(), "PROPERTY", period);
    }

    private BigDecimal totalRent(ReceivableRegister register, YearMonth first, YearMonth last) {
        BigDecimal total = BigDecimal.ZERO;
        for (YearMonth period = first; !period.isAfter(last); period = period.plusMonths(1)) {
            total = total.add(rent(register, period));
        }
        return total;
    }

    private static ReceivableRule rule(String type) {
        ReceivableRule rule = new ReceivableRule();
        rule.setRuleType(type);
        rule.setStatus("ACTIVE");
        return rule;
    }
}
