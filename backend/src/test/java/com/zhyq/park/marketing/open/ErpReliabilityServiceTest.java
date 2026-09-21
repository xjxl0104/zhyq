package com.zhyq.park.marketing.open;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.mapper.Mapper;
import com.zhyq.park.marketing.entity.MktErpEventInbox;
import com.zhyq.park.marketing.entity.MktErpReconcileSnapshot;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseErp;
import com.zhyq.park.marketing.mapper.MktErpDeadLetterMapper;
import com.zhyq.park.marketing.mapper.MktErpEventInboxMapper;
import com.zhyq.park.marketing.mapper.MktErpReconcileSnapshotMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseErpMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
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
class ErpReliabilityServiceTest {
    @Mock MktWarehouseErpMapper erp;
    @Mock MktWarehouseMapper warehouses;
    @Mock MktErpEventInboxMapper inbox;
    @Mock MktErpDeadLetterMapper dead;
    @Mock MktErpReconcileSnapshotMapper reconcile;

    @BeforeAll static void initTableInfo() {
        MapperBuilderAssistant a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(a, MktWarehouse.class);
        TableInfoHelper.initTableInfo(a, MktWarehouseErp.class);
        TableInfoHelper.initTableInfo(a, MktErpEventInbox.class);
        TableInfoHelper.initTableInfo(a, MktErpReconcileSnapshot.class);
    }

    @Test void twoPercentOrMoreFreezesSnapshot() {
        when(reconcile.selectOne(any())).thenReturn(null);
        ErpReliabilityService service = new ErpReliabilityService(erp, warehouses, inbox, dead, reconcile);
        MktErpReconcileSnapshot below = service.snapshot(11L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                new BigDecimal("100"), new BigDecimal("98.01"), 7L);
        MktErpReconcileSnapshot at = service.snapshot(12L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                new BigDecimal("100"), new BigDecimal("98"), 7L);
        assertThat(below.getFrozen()).isZero();
        assertThat(at.getFrozen()).isEqualTo(1);
        verify(reconcile, times(2)).insert(any(MktErpReconcileSnapshot.class));
    }

    @Test void staleWarehouseIsMarkedDisconnected() {
        MktWarehouseErp e = new MktWarehouseErp(); e.setWarehouseId(11L); e.setLastSyncAt(LocalDateTime.now().minusHours(6).minusMinutes(1)); e.setEnv(2);
        MktWarehouse w = new MktWarehouse(); w.setId(11L); w.setErpStatus(2);
        when(erp.selectList(any())).thenReturn(List.of(e)); when(warehouses.selectById(11L)).thenReturn(w); when(warehouses.update(any(), any())).thenReturn(1);
        ErpReliabilityService service = new ErpReliabilityService(erp, warehouses, inbox, dead, reconcile);
        assertThat(service.heartbeat(LocalDateTime.now())).isEqualTo(1);
        verify(warehouses).update(any(), any());
    }
}
