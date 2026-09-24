package com.zhyq.park.marketing.finance;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MktFixedFeeBillingServiceTest {
    @Mock MktServiceContractMapper contracts;
    @Mock BillMapper bills;
    @BeforeAll static void tables(){var a=new MapperBuilderAssistant(new MybatisConfiguration(),"");TableInfoHelper.initTableInfo(a,Bill.class);TableInfoHelper.initTableInfo(a,MktServiceContract.class);}
    @Test void anchorsToOriginalDayAcrossShortMonthAndNeverBillsFutureCycle() {
        var c=contract("2026-01-31","2026-04-30");when(contracts.selectList(any())).thenReturn(List.of(c));
        int count=new MktFixedFeeBillingService(contracts,bills,new com.zhyq.park.marketing.service.MktContractTermsService(mock(com.zhyq.park.marketing.mapper.MktServiceContractVersionMapper.class))).generateDue(LocalDate.parse("2026-03-30"));
        assertThat(count).isEqualTo(2);
        ArgumentCaptor<Bill> capture=ArgumentCaptor.forClass(Bill.class);verify(bills,times(2)).insert(capture.capture());
        assertThat(capture.getAllValues()).extracting(Bill::getPeriodStart).containsExactly(LocalDate.parse("2026-01-31"),LocalDate.parse("2026-02-28"));
        assertThat(capture.getAllValues()).extracting(Bill::getPeriodEnd).containsExactly(LocalDate.parse("2026-02-27"),LocalDate.parse("2026-03-30"));
        assertThat(capture.getAllValues()).allSatisfy(b->assertThat(b.getAmount()).isEqualByComparingTo("310.00"));
    }
    @Test void proratesFinalPartialCycleIncludingLeapDay() {
        var c=contract("2024-02-01","2024-02-15");
        assertThat(MktFixedFeeBillingService.fixedAmount(c,c.getStartDate(),LocalDate.parse("2024-03-01"))).isEqualByComparingTo("160.34");
        assertThat(MktFixedFeeBillingService.periodEnd(c,LocalDate.parse("2024-03-01"))).isEqualTo(c.getEndDate());
    }
    @Test void existingFirstRentKeyIsNotRecreated() {
        var c=contract("2026-09-01","2026-12-31");when(contracts.selectList(any())).thenReturn(List.of(c));when(bills.selectCount(any())).thenReturn(1L);
        assertThat(new MktFixedFeeBillingService(contracts,bills,new com.zhyq.park.marketing.service.MktContractTermsService(mock(com.zhyq.park.marketing.mapper.MktServiceContractVersionMapper.class))).generateDue(LocalDate.parse("2026-09-23"))).isZero();verify(bills,never()).insert(any(Bill.class));
    }
    @Test void noImplicitMonthlyPriceOrStringAmount() {
        var c=contract("2026-09-01","2026-12-31");c.setPriceTable("{\"perOrder\":10}");
        assertThatThrownBy(()->MktFixedFeeBillingService.fixedAmount(c,c.getStartDate(),c.getStartDate().plusMonths(1))).isInstanceOf(BizException.class);
        c.setPriceTable("{\"monthly\":\"100\"}");
        assertThatThrownBy(()->MktFixedFeeBillingService.fixedAmount(c,c.getStartDate(),c.getStartDate().plusMonths(1))).isInstanceOf(BizException.class);
    }
    private MktServiceContract contract(String start,String end){var c=new MktServiceContract();c.setId(8L);c.setSignMode(1);c.setFeeModel(4);c.setStatus(4);c.setStartDate(LocalDate.parse(start));c.setEndDate(LocalDate.parse(end));c.setPriceTable("{\"monthly\":310}");return c;}
}
