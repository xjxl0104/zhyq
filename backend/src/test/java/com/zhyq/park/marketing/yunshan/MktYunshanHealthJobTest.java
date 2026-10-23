package com.zhyq.park.marketing.yunshan;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.marketing.entity.MktYunshanVolumeMap;
import com.zhyq.park.marketing.job.MktYunshanHealthJob;
import com.zhyq.park.marketing.mapper.MktYunshanVolumeMapMapper;
import com.zhyq.park.marketing.service.MktAlertService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 每天 11:00 巡检云山拉取：失败或超过 36 小时没拉取要告警。 */
class MktYunshanHealthJobTest {
    private final MktYunshanVolumeMapMapper maps = mock(MktYunshanVolumeMapMapper.class);
    private final MktAlertService alerts = mock(MktAlertService.class);
    private final MktYunshanHealthJob job = new MktYunshanHealthJob(maps, alerts);
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 23, 11, 0);

    @BeforeAll static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MktYunshanVolumeMap.class);
    }
    private static MktYunshanVolumeMap map(long id, LocalDateTime lastSyncAt, Integer ok) {
        var map = new MktYunshanVolumeMap(); map.setId(id); map.setLastSyncAt(lastSyncAt); map.setLastSyncOk(ok); return map;
    }

    @Test void 刚成功拉取过的关联不告警() {
        when(maps.selectList(any())).thenReturn(List.of(map(7, now.minusHours(1), 1)));
        assertThat(job.check(now)).isZero();
        verifyNoInteractions(alerts);
    }

    @Test void 从未拉取或超过36小时没拉取要告警() {
        when(maps.selectList(any())).thenReturn(List.of(map(7, null, null), map(8, now.minusHours(40), 1)));
        assertThat(job.check(now)).isEqualTo(2);
        verify(alerts).alert(eq("yunshan-stale-7"), anyString(), contains("从未拉取"));
        verify(alerts).alert(eq("yunshan-stale-8"), anyString(), contains("36 小时"));
    }

    @Test void 最近一次拉取失败要告警且与拉取任务共用去重key() {
        when(maps.selectList(any())).thenReturn(List.of(map(7, now.minusHours(2), 0)));
        assertThat(job.check(now)).isEqualTo(1);
        verify(alerts).alert(eq("yunshan-sync-7"), anyString(), contains("失败"));
    }
}
