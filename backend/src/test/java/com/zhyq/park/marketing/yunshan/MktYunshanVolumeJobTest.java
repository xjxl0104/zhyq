package com.zhyq.park.marketing.yunshan;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.marketing.entity.MktYunshanVolumeMap;
import com.zhyq.park.marketing.job.MktYunshanVolumeJob;
import com.zhyq.park.marketing.mapper.MktYunshanVolumeMapMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MktYunshanVolumeJobTest {
    @BeforeAll static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MktYunshanVolumeMap.class);
    }

    @Test void missingSharedCredentialsStillRunsCustomerDedicatedConnections() {
        var client = mock(YunshanClient.class);
        var mapper = mock(MktYunshanVolumeMapMapper.class);
        var service = mock(YunshanVolumeSyncService.class);
        var shared = new MktYunshanVolumeMap(); shared.setId(1L);
        var dedicated = new MktYunshanVolumeMap(); dedicated.setId(2L); dedicated.setConnectionBaseUrl("https://customer.example/api");
        when(mapper.selectList(any())).thenReturn(List.of(shared, dedicated));
        new MktYunshanVolumeJob(client, mapper, service, 7).run();
        LocalDate today = LocalDate.now(YunshanVolumeSyncService.SHANGHAI);
        verify(service).sync(2L, today.minusDays(7), today.minusDays(1), 1);
        verify(service, never()).sync(eq(1L), any(), any(), anyInt());
    }

    @Test void failedOrThrowingSyncPushesAnAlertAndDefaultWindowIs35Days() throws Exception {
        var client = mock(YunshanClient.class);
        var mapper = mock(MktYunshanVolumeMapMapper.class);
        var service = mock(YunshanVolumeSyncService.class);
        var alerts = mock(com.zhyq.park.marketing.service.MktAlertService.class);
        when(client.configured()).thenReturn(true);
        var first = new MktYunshanVolumeMap(); first.setId(1L);
        var second = new MktYunshanVolumeMap(); second.setId(2L);
        when(mapper.selectList(any())).thenReturn(List.of(first, second));
        var failed = new com.zhyq.park.marketing.entity.MktYunshanSyncLog(); failed.setOk(0); failed.setErrorMsg("云山接口返回 500");
        LocalDate today = LocalDate.now(YunshanVolumeSyncService.SHANGHAI);
        when(service.sync(1L, today.minusDays(35), today.minusDays(1), 1)).thenReturn(failed);
        when(service.sync(2L, today.minusDays(35), today.minusDays(1), 1)).thenThrow(new IllegalStateException("连接超时"));
        new MktYunshanVolumeJob(client, mapper, service, 35, alerts).run();
        verify(alerts).alert(eq("yunshan-sync-1"), anyString(), contains("云山接口返回 500"));
        verify(alerts).alert(eq("yunshan-sync-2"), anyString(), contains("连接超时"));
        var constructor = MktYunshanVolumeJob.class.getConstructor(YunshanClient.class, MktYunshanVolumeMapMapper.class, YunshanVolumeSyncService.class, int.class, com.zhyq.park.marketing.service.MktAlertService.class);
        assertThat(constructor.getParameters()[3].getAnnotation(org.springframework.beans.factory.annotation.Value.class).value()).isEqualTo("${zhyq.yunshan.sync-days:35}");
    }

    @Test void invalidWindowSkipsInsteadOfClampingSilently() {
        var client = mock(YunshanClient.class);
        var mapper = mock(MktYunshanVolumeMapMapper.class);
        var service = mock(YunshanVolumeSyncService.class);
        when(client.configured()).thenReturn(true);
        new MktYunshanVolumeJob(client, mapper, service, 93).run();
        verifyNoInteractions(mapper, service);
    }

    @Test void scheduledRangeUsesShanghaiYesterdayAndOneFailureDoesNotStopOtherMappings() throws Exception {
        var client = mock(YunshanClient.class);
        var mapper = mock(MktYunshanVolumeMapMapper.class);
        var service = mock(YunshanVolumeSyncService.class);
        when(client.configured()).thenReturn(true);
        var first = new MktYunshanVolumeMap(); first.setId(1L);
        var second = new MktYunshanVolumeMap(); second.setId(2L);
        when(mapper.selectList(any())).thenReturn(List.of(first, second));
        LocalDate today = LocalDate.now(YunshanVolumeSyncService.SHANGHAI);
        when(service.sync(1L, today.minusDays(7), today.minusDays(1), 1)).thenThrow(new IllegalStateException("failure"));
        new MktYunshanVolumeJob(client, mapper, service, 7).run();
        verify(service).sync(2L, today.minusDays(7), today.minusDays(1), 1);
        Scheduled schedule = MktYunshanVolumeJob.class.getMethod("run").getAnnotation(Scheduled.class);
        assertThat(schedule.zone()).isEqualTo("Asia/Shanghai");
        assertThat(schedule.cron()).isEqualTo("0 30 9 * * ?");
    }
}
