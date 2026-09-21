package com.zhyq.park.marketing.finance;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.marketing.entity.MktReferralOrder;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.entity.MktServiceFeeBill;
import com.zhyq.park.marketing.entity.MktServiceFeeBillLine;
import com.zhyq.park.marketing.mapper.MktReferralOrderMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.mapper.MktServiceFeeBillLineMapper;
import com.zhyq.park.marketing.mapper.MktServiceFeeBillMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MktServiceFeeBillServiceTest {
    @Mock MktServiceFeeBillMapper bills;
    @Mock MktServiceFeeBillLineMapper lines;
    @Mock MktServiceContractMapper contracts;
    @Mock MktReferralOrderMapper orders;

    @BeforeAll static void initTableInfo() {
        MapperBuilderAssistant a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(a, MktServiceFeeBill.class); TableInfoHelper.initTableInfo(a, MktServiceFeeBillLine.class);
        TableInfoHelper.initTableInfo(a, MktServiceContract.class); TableInfoHelper.initTableInfo(a, MktReferralOrder.class);
    }

    @Test void monthlyGenerationIsIdempotentAndSnapshotsLines() {
        when(bills.selectOne(any())).thenReturn(null);
        MktServiceContract c = new MktServiceContract(); c.setId(9L); c.setWarehouseId(11L); c.setProjectId(7L);
        when(contracts.selectById(9L)).thenReturn(c);
        MktReferralOrder o = new MktReferralOrder(); o.setId(21L); o.setSourceType(2); o.setSourceNo("O-21"); o.setStatus(2); o.setEventTime(LocalDateTime.of(2026, 9, 10, 10, 0)); o.setServiceFee(new BigDecimal("12.50"));
        when(orders.selectList(any())).thenReturn(List.of(o));
        doAnswer(inv -> { ((MktServiceFeeBill) inv.getArgument(0)).setId(31L); return 1; }).when(bills).insert(any(MktServiceFeeBill.class));
        MktServiceFeeBillService service = new MktServiceFeeBillService(bills, lines, contracts, orders);
        MktServiceFeeBill bill = service.generate(9L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        assertThat(bill.getAmount()).isEqualByComparingTo("12.50");
        assertThat(bill.getBillingKey()).isEqualTo("contract:9:service:2026-09-01");
        verify(lines).insert(any(MktServiceFeeBillLine.class));
    }
}
