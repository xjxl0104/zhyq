package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.entity.MktServiceContractVersion;
import com.zhyq.park.marketing.mapper.MktServiceContractVersionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class MktContractTermsServiceTest {
    @BeforeAll static void tables() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MktServiceContractVersion.class);
    }
    @Test void delayedShipmentUsesSignedOldPricesAndFutureDateUsesNewPrices() {
        var mapper=mock(MktServiceContractVersionMapper.class);var service=new MktContractTermsService(mapper);
        var c=current(); var v=new MktServiceContractVersion();
        v.setSnapshot("{\"startDate\":\"2026-01-01\",\"endDate\":\"2026-12-31\",\"priceTable\":\"{\\\"perOrder\\\":10}\",\"termsEffectiveFrom\":\"2026-01-01\"}");
        when(mapper.selectList(any())).thenReturn(List.of(v));
        assertThat(service.atDate(c, LocalDate.parse("2026-09-29")).getPriceTable()).isEqualTo("{\"perOrder\":10}");
        assertThat(service.atDate(c, LocalDate.parse("2026-10-01")).getPriceTable()).isEqualTo("{\"perOrder\":12}");
        assertThat(c.getPriceTable()).isEqualTo("{\"perOrder\":12}");
    }
    @Test void legacySnapshotWithObjectPriceTableIsStillUsable() {
        var mapper=mock(MktServiceContractVersionMapper.class);var service=new MktContractTermsService(mapper);
        var v=new MktServiceContractVersion();v.setSnapshot("{\"startDate\":\"2026-01-01\",\"priceTable\":{\"perOrder\":8}}");
        when(mapper.selectList(any())).thenReturn(List.of(v));
        assertThat(service.atDate(current(), LocalDate.parse("2026-09-29")).getPriceTable()).isEqualTo("{\"perOrder\":8}");
    }
    @Test void missingHistoricalTermsNeverSilentlyUsesNewPrice() {
        var mapper=mock(MktServiceContractVersionMapper.class);when(mapper.selectList(any())).thenReturn(List.of());
        assertThatThrownBy(()->new MktContractTermsService(mapper).atDate(current(), LocalDate.parse("2026-09-29"))).isInstanceOf(BizException.class);
    }
    private static MktServiceContract current(){var c=new MktServiceContract();c.setId(1L);c.setStartDate(LocalDate.parse("2026-01-01"));c.setTermsEffectiveFrom(LocalDate.parse("2026-10-01"));c.setPriceTable("{\"perOrder\":12}");return c;}
}
