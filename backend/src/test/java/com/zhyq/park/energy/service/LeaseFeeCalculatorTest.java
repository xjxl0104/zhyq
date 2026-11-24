package com.zhyq.park.energy.service;

import com.zhyq.park.energy.entity.LeaseContract;
import com.zhyq.park.energy.entity.LeaseFeeNotice;
import com.zhyq.park.energy.entity.LeaseFeePeriod;
import com.zhyq.park.energy.service.LeaseFeeCalculator.ContractMonthRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LeaseFeeCalculatorTest {

    private static List<LeaseContract> seedContracts() {
        return List.of(
                contract(1L, 1, "广东分享智联供应链管理有限公司", "DIPARK第六层的储存区一", "4700", "2026-05-01", "2032-04-30", "12", "2", "0.10", 36,
                        "{\"periods\":[{\"from\":\"2026-05\",\"to\":\"2026-05\",\"rentFactor\":0,\"propertyFactor\":0,\"remark\":\"免租期\"},{\"from\":\"2026-06\",\"to\":\"2026-09\",\"rentFactor\":0.5,\"propertyFactor\":0.5,\"remark\":\"优惠期，租金及物业费按5折收取\"}]}"),
                contract(2L, 2, "广州昌泰供应链科技有限公司", "DIPARK第五层", "13500", "2026-06-01", "2032-05-31", "12", "2", "0.10", 36,
                        "{\"periods\":[{\"from\":\"2026-06\",\"to\":\"2026-07\",\"rentFactor\":0,\"propertyFactor\":0,\"remark\":\"免租期\"},{\"from\":\"2026-08\",\"to\":\"2026-10\",\"rentFactor\":0,\"propertyFactor\":1,\"remark\":\"搬迁补助抵扣租金\"}],\"everyPaidMonthsHalfRent\":{\"paidCount\":12,\"rentFactor\":0.5,\"propertyFactor\":1,\"remark\":\"每交租满12个月，本月租金按5折收取（0.5个月免租）\"}}"),
                contract(3L, 3, "广州微新供应链管理有限公司", "DIPARK第四层", "13500", "2026-07-01", "2032-06-30", "12.8", "2", "0.10", 36,
                        "{\"periods\":[{\"from\":\"2026-07\",\"to\":\"2026-11\",\"rentFactor\":0,\"propertyFactor\":0,\"remark\":\"免租期\"}]}"),
                contract(4L, 4, "中印国际供应链（广州）有限公司", "DIPARK第一层防火分区三", "3500", "2026-06-01", "2032-05-31", "34", "2", "0.10", 36,
                        "{\"periods\":[{\"from\":\"2026-06\",\"to\":\"2026-07\",\"rentFactor\":0,\"propertyFactor\":0,\"remark\":\"免租期\"},{\"from\":\"2026-08\",\"to\":\"2026-09\",\"rentFactor\":0.7,\"propertyFactor\":0.7,\"remark\":\"优惠期，租金及物业费按7折收取\"}]}"),
                contract(5L, 5, "广东云山供应链科技有限公司", "DI PARK数智云仓产业园第二层", "13500", "2026-06-01", "2041-05-31", "19", "2", "0.08", 36,
                        "{\"periods\":[{\"from\":\"2026-06\",\"to\":\"2026-11\",\"rentFactor\":0,\"propertyFactor\":0,\"remark\":\"免租期\"}],\"annualMonthFree\":{\"fromYear\":2027,\"toYear\":2040,\"month\":12,\"rentFactor\":0,\"propertyFactor\":1,\"remark\":\"每年最后一个月免租\"}}"),
                contract(6L, 6, "广东云山供应链科技有限公司", "DI PARK数智云仓产业园第三层", "13500", "2026-06-01", "2041-05-31", "12.5", "2", "0.08", 36,
                        "{\"periods\":[{\"from\":\"2026-06\",\"to\":\"2026-11\",\"rentFactor\":0,\"propertyFactor\":0,\"remark\":\"免租期\"}],\"annualMonthFree\":{\"fromYear\":2027,\"toYear\":2040,\"month\":12,\"rentFactor\":0,\"propertyFactor\":1,\"remark\":\"每年最后一个月免租\"}}"),
                contract(7L, 7, "广东云山供应链科技有限公司", "DI PARK数智云仓产业园第六层储存区二及储存区三", "8800", "2026-07-01", "2041-06-30", "12", "2", "0.08", 36,
                        "{\"periods\":[{\"from\":\"2026-07\",\"to\":\"2026-12\",\"rentFactor\":0,\"propertyFactor\":0,\"remark\":\"免租期\"}],\"annualMonthFree\":{\"fromYear\":2027,\"toYear\":2040,\"month\":12,\"rentFactor\":0,\"propertyFactor\":1,\"remark\":\"每年最后一个月免租\"}}"),
                contract(8L, 8, "广东云山供应链科技有限公司", "DI PARK数智云仓产业园第七层", "13500", "2026-07-01", "2041-06-30", "12", "2", "0.08", 36,
                        "{\"periods\":[{\"from\":\"2026-07\",\"to\":\"2026-12\",\"rentFactor\":0,\"propertyFactor\":0,\"remark\":\"免租期\"}],\"annualMonthFree\":{\"fromYear\":2027,\"toYear\":2040,\"month\":12,\"rentFactor\":0,\"propertyFactor\":1,\"remark\":\"每年最后一个月免租\"}}"),
                contract(9L, 9, "广东云山供应链科技有限公司", "DI PARK数智云仓产业园首层B区", "3600", "2026-08-08", "2041-08-07", "28.555555555556", "2", "0.08", 36,
                        "{\"periods\":[{\"from\":\"2026-08\",\"to\":\"2026-12\",\"rentFactor\":0,\"propertyFactor\":0,\"remark\":\"免租期\"}],\"annualMonthFree\":{\"fromYear\":2027,\"toYear\":2040,\"month\":12,\"rentFactor\":0,\"propertyFactor\":0,\"remark\":\"每年12月免租\"}}"),
                contract(10L, 10, "广州鑫晨供应链有限公司", "DIPARK第五层储存区二及储存区三", "9000", "2026-08-01", "2041-06-30", "13", "2", "0.10", 36,
                        "{\"periods\":[{\"from\":\"2026-08\",\"to\":\"2026-09\",\"rentFactor\":0,\"propertyFactor\":0,\"remark\":\"免租期\"},{\"from\":\"2027-12\",\"to\":\"2027-12\",\"rentFactor\":0,\"propertyFactor\":0,\"remark\":\"免租期\"},{\"from\":\"2028-12\",\"to\":\"2028-12\",\"rentFactor\":0,\"propertyFactor\":0,\"remark\":\"免租期\"}]}"),
                contract(11L, 11, "广州云帕供应链管理有限公司", "DI PARK数智云仓产业园首层A区", "3300", "2026-07-01", "2032-06-30", "30", "2", "0.10", 36,
                        "{\"periods\":[{\"from\":\"2026-07\",\"to\":\"2026-11\",\"rentFactor\":0,\"propertyFactor\":0,\"remark\":\"免租期\"}]}")
        );
    }

    private static LeaseContract contract(Long id, int seq, String tenant, String unit, String area,
                                          String start, String end, String rent, String prop,
                                          String rate, int months, String rules) {
        LeaseContract c = new LeaseContract();
        c.setId(id);
        c.setSeqNo(seq);
        c.setTenantName(tenant);
        c.setUnitName(unit);
        c.setLeaseArea(new BigDecimal(area));
        c.setStartDate(LocalDate.parse(start));
        c.setEndDate(LocalDate.parse(end));
        c.setRentUnitPrice(new BigDecimal(rent));
        c.setPropertyUnitPrice(new BigDecimal(prop));
        c.setEscalationRate(new BigDecimal(rate));
        c.setEscalationMonths(months);
        c.setRulesJson(rules);
        c.setStatus(1);
        return c;
    }

    private static BigDecimal[] monthTotals(List<LeaseContract> list, String ym) {
        List<ContractMonthRow> rows = LeaseFeeCalculator.calculateMonth(YearMonth.parse(ym), list, Map.of());
        BigDecimal rent = rows.stream().map(ContractMonthRow::getPayableRent).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal prop = rows.stream().map(ContractMonthRow::getPayableProperty).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BigDecimal[]{rent, prop};
    }

    @Test
    void matchesReferenceSpreadsheetKeyMonthsExactly() {
        List<LeaseContract> list = seedContracts();
        // 2026-05: 全部免租
        assertThat(monthTotals(list, "2026-05")).containsExactly(new BigDecimal("0.00"), new BigDecimal("0.00"));
        // 2026-06: 分享智联5折 (28200 / 4700)
        assertThat(monthTotals(list, "2026-06")).containsExactly(new BigDecimal("28200.00"), new BigDecimal("4700.00"));
        // 2026-08: 分享智联5折 + 昌泰补助抵扣(物业费27000) + 中印7折
        assertThat(monthTotals(list, "2026-08")).containsExactly(new BigDecimal("111500.00"), new BigDecimal("36600.00"));
        // 2026-10: 分享全额 + 昌泰物业费 + 中印全额 + 鑫晨全额
        assertThat(monthTotals(list, "2026-10")).containsExactly(new BigDecimal("292400.00"), new BigDecimal("61400.00"));
        // 2026-12: 微新、云山2/3层、云帕开始计租
        assertThat(monthTotals(list, "2026-12")).containsExactly(new BigDecimal("1151450.00"), new BigDecimal("149000.00"));
        // 2027-01: 全部11份合同全额计租
        assertThat(monthTotals(list, "2027-01")).containsExactly(new BigDecimal("1521850.00"), new BigDecimal("200800.00"));
        // 2027-11: 昌泰每交租满12个月5折
        assertThat(monthTotals(list, "2027-11")).containsExactly(new BigDecimal("1440850.00"), new BigDecimal("200800.00"));
        // 2027-12: 云山5份免租(原4份物业照收98600, B区免), 鑫晨免租
        assertThat(monthTotals(list, "2027-12")).containsExactly(new BigDecimal("609200.00"), new BigDecimal("175600.00"));
        // 2028-12: 昌泰5折 + 云山/鑫晨免租
        assertThat(monthTotals(list, "2028-12")).containsExactly(new BigDecimal("528200.00"), new BigDecimal("175600.00"));
        // 2029-05: 分享智联满36个月+10%
        assertThat(monthTotals(list, "2029-05")).containsExactly(new BigDecimal("1527490.00"), new BigDecimal("201740.00"));
        // 2029-08: 云山首层B区8日递增按天数加权 + 鑫晨+10%
        assertThat(monthTotals(list, "2029-08")).containsExactly(new BigDecimal("1656264.97"), new BigDecimal("218633.94"));
        // 2030-01: 昌泰第3次5折
        assertThat(monthTotals(list, "2030-01")).containsExactly(new BigDecimal("1569022.00"), new BigDecimal("218764.00"));
        // 2041-08: 云山首层B区末月按7/31折算
        assertThat(monthTotals(list, "2041-08")).containsExactly(new BigDecimal("33536.88"), new BigDecimal("2348.89"));
    }

    @Test
    void buildsSeparateNoticesForRentAndPropertyWithDistinctPayees() {
        LeaseFeePeriod p = new LeaseFeePeriod();
        p.setId(9L);
        p.setPeriod("2027-01");
        p.setNoticePrefix("DIPARK");
        p.setRentPayeeName("广州租金收取有限公司");
        p.setRentPaymentAccount("租金账户 6222-RENT");
        p.setPropertyPayeeName("广州物业管理有限公司");
        p.setPropertyPaymentAccount("物业账户 6222-PROP");

        List<ContractMonthRow> rows = LeaseFeeCalculator.calculateMonth(YearMonth.of(2027, 1), seedContracts(), Map.of());
        List<LeaseFeeNotice> notices = LeaseFeeCalculator.buildNotices(p, rows);

        // 7 个租户 => 7 张租金通知单 + 7 张物业管理费通知单 = 14 张
        assertThat(notices).hasSize(14);
        List<LeaseFeeNotice> rentNotices = notices.stream().filter(n -> LeaseFeeNotice.CAT_RENT.equals(n.getFeeCategory())).toList();
        List<LeaseFeeNotice> propNotices = notices.stream().filter(n -> LeaseFeeNotice.CAT_PROPERTY.equals(n.getFeeCategory())).toList();
        assertThat(rentNotices).hasSize(7).allSatisfy(n -> {
            assertThat(n.getPayeeName()).isEqualTo("广州租金收取有限公司");
            assertThat(n.getPaymentAccount()).isEqualTo("租金账户 6222-RENT");
        });
        assertThat(propNotices).hasSize(7).allSatisfy(n -> {
            assertThat(n.getPayeeName()).isEqualTo("广州物业管理有限公司");
            assertThat(n.getPaymentAccount()).isEqualTo("物业账户 6222-PROP");
        });

        // 广东云山 5 份合同合并列在同一张租金通知单和同一张物业费通知单内
        LeaseFeeNotice yunshanRent = rentNotices.stream().filter(n -> n.getTenantName().contains("广东云山")).findFirst().orElseThrow();
        assertThat(yunshanRent.getItems()).hasSize(5);
        assertThat(yunshanRent.getTotalDue()).isEqualByComparingTo("795650.00");

        LeaseFeeNotice yunshanProp = propNotices.stream().filter(n -> n.getTenantName().contains("广东云山")).findFirst().orElseThrow();
        assertThat(yunshanProp.getItems()).hasSize(5);
        assertThat(yunshanProp.getTotalDue()).isEqualByComparingTo("105800.00");
    }

    @Test
    void fixedMonthlyRentOverridesUnitPriceAndEscalatesFromFixedTotal() {
        LeaseContract c = new LeaseContract();
        c.setId(99L);
        c.setSeqNo(99);
        c.setTenantName("固定月租测试公司");
        c.setUnitName("首层B区");
        c.setLeaseArea(new BigDecimal("3600"));
        c.setRentUnitPrice(new BigDecimal("99.99")); // 应被月固定总价覆盖
        c.setFixedMonthlyRent(new BigDecimal("102800.00"));
        c.setPropertyUnitPrice(new BigDecimal("2.00"));
        c.setEscalationRate(new BigDecimal("0.08"));
        c.setEscalationMonths(36);
        c.setStartDate(LocalDate.of(2026, 8, 1));
        c.setEndDate(LocalDate.of(2035, 7, 31));

        // 起始期按固定总价 102800.00
        ContractMonthRow initial = LeaseFeeCalculator.calculateContractRow(YearMonth.of(2026, 9), c, null);
        assertThat(initial.getFixedMonthlyRent()).isEqualByComparingTo("102800.00");
        assertThat(initial.getStandardRent()).isEqualByComparingTo("102800.00");
        assertThat(initial.getPayableRent()).isEqualByComparingTo("102800.00");
        assertThat(initial.getStandardProperty()).isEqualByComparingTo("7200.00");

        // 满36个月首次递增 8% -> 102800 * 1.08 = 111024.00
        ContractMonthRow esc1 = LeaseFeeCalculator.calculateContractRow(YearMonth.of(2029, 8), c, null);
        assertThat(esc1.getStandardRent()).isEqualByComparingTo("111024.00");
        assertThat(esc1.getPayableRent()).isEqualByComparingTo("111024.00");

        // 满72个月二次复利递增 -> 102800 * 1.08^2 = 119905.92
        ContractMonthRow esc2 = LeaseFeeCalculator.calculateContractRow(YearMonth.of(2032, 8), c, null);
        assertThat(esc2.getStandardRent()).isEqualByComparingTo("119905.92");
        assertThat(esc2.getPayableRent()).isEqualByComparingTo("119905.92");
    }
}
