package com.zhyq.park.marketing.settlement;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.marketing.entity.MktDirectSignPayment;
import com.zhyq.park.marketing.entity.MktReferralOrder;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.MktCommissionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MktWarehouseSettlementServiceTest {
    @Mock MktWarehouseSettlementMapper settlements;
    @Mock MktWarehouseSettlementLineMapper lines;
    @Mock MktServiceFeeBillMapper bills;
    @Mock MktDirectSignPaymentMapper payments;
    @Mock MktReferralOrderMapper orders;
    @Mock MktWarehouseMapper warehouses;
    @Mock MktErpReconcileSnapshotMapper reconcile;
    @Mock MktCommissionService commissions;

    @BeforeAll static void initTableInfo() {
        MapperBuilderAssistant a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(a, MktDirectSignPayment.class); TableInfoHelper.initTableInfo(a, MktReferralOrder.class); TableInfoHelper.initTableInfo(a, MktWarehouse.class);
    }

    @Test void directSignPaymentIsIdempotentAndUnfreezesOnlyPlatformOrders() {
        when(payments.selectOne(any())).thenReturn(null);
        MktWarehouse w = new MktWarehouse(); w.setId(11L); w.setProjectId(7L); when(warehouses.selectById(11L)).thenReturn(w);
        MktReferralOrder o = new MktReferralOrder(); o.setId(31L); o.setSourceType(MktCommissionService.SOURCE_PLATFORM_FEE); o.setSourceId(9L); when(orders.selectList(any())).thenReturn(List.of(o));
        doAnswer(inv -> { ((MktDirectSignPayment) inv.getArgument(0)).setId(41L); return 1; }).when(payments).insert(any(MktDirectSignPayment.class));
        MktWarehouseSettlementService service = new MktWarehouseSettlementService(settlements, lines, bills, payments, orders, warehouses, reconcile, commissions);
        MktDirectSignPayment p = service.recordPayment(9L, 11L, "PAY-1", new BigDecimal("88.00"));
        assertThat(p.getStatus()).isEqualTo(1); assertThat(p.getAmount()).isEqualByComparingTo("88.00");
        verify(commissions).unfreezeByOrder(31L);
    }
}
