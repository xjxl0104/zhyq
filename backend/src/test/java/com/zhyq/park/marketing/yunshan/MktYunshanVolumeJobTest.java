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

    @Test void missingCredentialsSkipsAllDatabaseWork() {
        var client = mock(YunshanClient.class);
        var mapper = mock(MktYunshanVolumeMapMapper.class);
        var service = mock(YunshanVolumeSyncService.class);
        new MktYunshanVolumeJob(client, mapper, service, 7).run();
        verifyNoInteractions(mapper, service);
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
