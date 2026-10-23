package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktReferralOrder;
import com.zhyq.park.marketing.entity.MktYunshanDailyVolume;
import com.zhyq.park.marketing.entity.MktYunshanVolumeMap;
import com.zhyq.park.marketing.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 按天核对：每个有云山快照的日期，已生成多少票的计佣订单。 */
class MktYunshanVolumeOrderServiceDailyTest {
    private final MktYunshanVolumeMapMapper maps = mock(MktYunshanVolumeMapMapper.class);
    private final MktYunshanDailyVolumeMapper volumes = mock(MktYunshanDailyVolumeMapper.class);
    private final MktReferralOrderMapper orders = mock(MktReferralOrderMapper.class);
    private final MktYunshanVolumeOrderService service = new MktYunshanVolumeOrderService(maps, volumes, mock(MktServiceContractMapper.class),
            mock(CustomerMapper.class), mock(MktWarehouseMapper.class), orders, mock(MktCommissionService.class),
            mock(MktCustomerPricingService.class), mock(MktContractTermsService.class), mock(BizSettings.class),
            mock(MktAuditService.class), mock(MktPromoterMapper.class), mock(PlatformTransactionManager.class));

    @BeforeAll static void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> type : List.of(MktYunshanDailyVolume.class, MktReferralOrder.class, MktYunshanVolumeMap.class)) TableInfoHelper.initTableInfo(assistant, type);
    }
    private static MktYunshanDailyVolume snapshot(String date, long tickets) {
        var row = new MktYunshanDailyVolume(); row.setStatDate(LocalDate.parse(date)); row.setConsignNum(tickets); return row;
    }
    private static MktReferralOrder order(String sourceNo, int tickets) {
        var order = new MktReferralOrder(); order.setSourceNo(sourceNo); order.setCommissionOrderCount(tickets); return order;
    }

    @Test
    void 逐日对比票数与已生成佣金的票数() {
        var map = new MktYunshanVolumeMap(); map.setId(7L); map.setWarehouseId(1L);
        when(maps.selectById(7L)).thenReturn(map);
        when(volumes.selectList(any())).thenReturn(List.of(snapshot("2026-10-17", 3), snapshot("2026-10-18", 7), snapshot("2026-10-19", 4)));
        when(orders.selectList(any())).thenReturn(List.of(
                order("YS-7-20261017-V1", 2), order("YS-7-20261017-V2", 1),   // 17 日：拆成两条订单，合计 3 票
                order("YS-7-20261019-V1", 4)));                                 // 19 日：4 票；18 日没有生成
        var days = service.dailyStatus(7L, LocalDate.parse("2026-10-17"), LocalDate.parse("2026-10-19"));
        assertThat(days).extracting(MktYunshanVolumeOrderService.DayStatus::tickets).containsExactly(3L, 7L, 4L);
        assertThat(days).extracting(MktYunshanVolumeOrderService.DayStatus::commissionTickets).containsExactly(3L, 0L, 4L);
        assertThat(days).extracting(MktYunshanVolumeOrderService.DayStatus::orders).containsExactly(2, 0, 1);
    }

    @Test
    void 日期区间和客户关联必须有效() {
        assertThatThrownBy(() -> service.dailyStatus(7L, LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-17"))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.dailyStatus(7L, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"))).hasMessageContaining("92");
        assertThatThrownBy(() -> service.dailyStatus(99L, LocalDate.parse("2026-10-17"), LocalDate.parse("2026-10-19"))).hasMessageContaining("不存在");
    }
}
