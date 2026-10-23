package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 云山发货票数 → 计佣汇总订单：生成、补差额、下调重建、跳过与防重复。 */
class MktYunshanVolumeOrderServiceGenerateTest {
    private final MktYunshanVolumeMapMapper maps = mock(MktYunshanVolumeMapMapper.class);
    private final MktYunshanDailyVolumeMapper volumes = mock(MktYunshanDailyVolumeMapper.class);
    private final MktServiceContractMapper contracts = mock(MktServiceContractMapper.class);
    private final CustomerMapper customers = mock(CustomerMapper.class);
    private final MktWarehouseMapper warehouses = mock(MktWarehouseMapper.class);
    private final MktReferralOrderMapper orders = mock(MktReferralOrderMapper.class);
    private final MktCommissionService commissionService = mock(MktCommissionService.class);
    private final MktCustomerPricingService pricing = mock(MktCustomerPricingService.class);
    private final MktContractTermsService terms = mock(MktContractTermsService.class);
    private final BizSettings settings = mock(BizSettings.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final LocalDate day = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(3);
    private final String dayNo = day.format(DateTimeFormatter.BASIC_ISO_DATE);
    private MktServiceContract contract;
    private MktYunshanVolumeOrderService service;

    @BeforeAll static void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> type : List.of(MktYunshanVolumeMap.class, MktYunshanDailyVolume.class, MktServiceContract.class, MktReferralOrder.class)) TableInfoHelper.initTableInfo(assistant, type);
    }

    @BeforeEach void setup() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new MktYunshanVolumeOrderService(maps, volumes, contracts, customers, warehouses, orders, commissionService,
                pricing, terms, settings, mock(MktAuditService.class), transactions);
        var map = new MktYunshanVolumeMap(); map.setId(7L); map.setCustomerId(10L); map.setWarehouseId(1L); map.setProjectId(3L); map.setStatus(1);
        when(maps.selectList(any())).thenReturn(List.of(map));
        contract = new MktServiceContract(); contract.setId(100L); contract.setWarehouseId(1L); contract.setPartnerId(2L); contract.setSignMode(2); contract.setProjectId(3L);
        when(contracts.selectOne(any())).thenReturn(contract);
        when(terms.atDate(any(), any())).thenAnswer(call -> call.getArgument(0));
        var customer = new Customer(); customer.setId(10L); customer.setReferrerId(2L);
        when(customers.selectById(10L)).thenReturn(customer);
        var warehouse = new MktWarehouse(); warehouse.setId(1L); warehouse.setJoinStatus(5);
        when(warehouses.selectById(1L)).thenReturn(warehouse);
        when(pricing.forAccrual(10L, 100L)).thenReturn(new MktCustomerPricingService.PricingSnapshot(9L, 4L, "P4", bd("0.100"), bd("0.050"), bd("0.005"), List.of()));
        when(settings.getInt("marketing", "freeze_days", 7)).thenReturn(7);
        when(orders.selectCount(any())).thenReturn(0L);
        when(orders.selectList(any())).thenReturn(List.of());
        when(orders.update(any(), any())).thenReturn(1);
        when(commissionService.createAggregatedOutboundInTransaction(any(), anyInt())).thenAnswer(call -> { var order = new MktReferralOrder(); order.setId(55L); return order; });
        shipped(5);
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
    private void shipped(long tickets) {
        var row = new MktYunshanDailyVolume(); row.setMapId(7L); row.setStatDate(day); row.setConsignNum(tickets);
        when(volumes.selectList(any())).thenReturn(List.of(row));
    }
    private MktReferralOrder existing(long id, String suffix, int tickets) {
        var order = new MktReferralOrder(); order.setId(id); order.setSourceNo("YS-7-" + dayNo + "-" + suffix); order.setStatus(2); order.setCommissionOrderCount(tickets); return order;
    }
    private MktCommissionService.CommissionEvent createdEvent(int expectedCount) {
        var event = ArgumentCaptor.forClass(MktCommissionService.CommissionEvent.class);
        verify(commissionService).createAggregatedOutboundInTransaction(event.capture(), eq(expectedCount));
        return event.getValue();
    }

    @Test void 按票数生成一条汇总订单并按每单金额计算佣金池() {
        var result = service.generate(null, null, null);
        assertThat(result).extracting("created", "rebuilt", "unchanged", "skipped", "failed").containsExactly(1, 0, 0, 0, 0);
        var event = createdEvent(5);
        assertThat(event.sourceNo()).isEqualTo("YS-7-" + dayNo + "-V1");
        assertThat(event.poolAmount()).isEqualByComparingTo("0.475");     // (0.100 - 0.005) × 5 票
        assertThat(event.baseAmount()).isEqualByComparingTo("0");         // 只算佣金，不生成服务费
        assertThat(event.eventTime()).isEqualTo(day.atStartOfDay());
        assertThat(event.unfreezeAt()).isEqualTo(day.plusDays(7).atStartOfDay());
        assertThat(event).extracting("customerId", "sellerPromoterId", "warehouseId", "sourceId").containsExactly(10L, 2L, 1L, 100L);
    }

    @Test void 票数没变化时不重复生成() {
        when(orders.selectList(any())).thenReturn(List.of(existing(40, "V1", 5)));
        var result = service.generate(null, null, null);
        assertThat(result).extracting("created", "unchanged").containsExactly(0, 1);
        verify(commissionService, never()).createAggregatedOutboundInTransaction(any(), anyInt());
    }

    @Test void 票数增加时只补发差额订单() {
        shipped(8);
        when(orders.selectList(any())).thenReturn(List.of(existing(40, "V1", 5)));
        service.generate(null, null, null);
        var event = createdEvent(3);
        assertThat(event.sourceNo()).isEqualTo("YS-7-" + dayNo + "-V2");
        assertThat(event.poolAmount()).isEqualByComparingTo("0.285");
        verify(commissionService, never()).clawback(anyLong(), anyString());
    }

    @Test void 票数下调时作废原订单并扣回再按新票数重建() {
        shipped(3);
        when(orders.selectList(any())).thenReturn(List.of(existing(40, "V1", 5)));
        var result = service.generate(null, null, null);
        assertThat(result.rebuilt()).isEqualTo(1);
        verify(commissionService).clawback(eq(40L), anyString());
        var event = createdEvent(3);
        assertThat(event.sourceNo()).isEqualTo("YS-7-" + dayNo + "-V2");
        assertThat(event.poolAmount()).isEqualByComparingTo("0.285");
    }

    @Test void 票数下调为零时只作废扣回不再生成() {
        shipped(0);
        when(orders.selectList(any())).thenReturn(List.of(existing(40, "V1", 5)));
        var result = service.generate(null, null, null);
        assertThat(result.rebuilt()).isEqualTo(1);
        verify(commissionService).clawback(eq(40L), anyString());
        verify(commissionService, never()).createAggregatedOutboundInTransaction(any(), anyInt());
    }

    @Test void 已作废的订单不计入已生成票数() {
        var cancelled = existing(40, "V1", 5); cancelled.setStatus(4);
        when(orders.selectList(any())).thenReturn(List.of(cancelled));
        service.generate(null, null, null);
        var event = createdEvent(5);
        assertThat(event.sourceNo()).isEqualTo("YS-7-" + dayNo + "-V2");   // 版本号沿用已有订单数，避免订单号冲突
    }

    @Test void 没有合同或签约方式不符或没有成交伙伴或云仓未上线都跳过() {
        when(contracts.selectOne(any())).thenReturn(null);
        assertThat(service.generate(null, null, null).skipped()).isEqualTo(1);
        when(contracts.selectOne(any())).thenReturn(contract);
        contract.setSignMode(3);
        assertThat(service.generate(null, null, null).skipped()).isEqualTo(1);
        contract.setSignMode(1); contract.setPartnerId(null);
        var noReferrer = new Customer(); noReferrer.setId(10L);
        when(customers.selectById(10L)).thenReturn(noReferrer);
        assertThat(service.generate(null, null, null).skipped()).isEqualTo(1);
        contract.setPartnerId(2L);
        var offline = new MktWarehouse(); offline.setId(1L); offline.setJoinStatus(6);
        when(warehouses.selectById(1L)).thenReturn(offline);
        assertThat(service.generate(null, null, null).skipped()).isEqualTo(1);
        verify(commissionService, never()).createAggregatedOutboundInTransaction(any(), anyInt());
    }

    @Test void 同一天已有手工导入或ERP计佣订单时不重复计佣() {
        when(orders.selectCount(any())).thenReturn(1L);
        var result = service.generate(null, null, null);
        assertThat(result.skipped()).isEqualTo(1);
        verify(commissionService, never()).createAggregatedOutboundInTransaction(any(), anyInt());
    }

    @Test void 未配置每单佣金时记为失败并说明原因且不生成() {
        when(pricing.forAccrual(10L, 100L)).thenThrow(new BizException("客户尚未设置 P4 每单佣金"));
        var result = service.generate(null, null, null);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors()).singleElement().asString().contains("尚未设置");
        verify(commissionService, never()).createAggregatedOutboundInTransaction(any(), anyInt());
    }

    @Test void 日票数异常时拒绝生成() {
        shipped(2_000_000);
        var result = service.generate(null, null, null);
        assertThat(result.failed()).isEqualTo(1);
        verify(commissionService, never()).createAggregatedOutboundInTransaction(any(), anyInt());
    }
}
