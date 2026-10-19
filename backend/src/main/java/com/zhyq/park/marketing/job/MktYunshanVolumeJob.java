package com.zhyq.park.marketing.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.marketing.entity.MktYunshanVolumeMap;
import com.zhyq.park.marketing.mapper.MktYunshanVolumeMapMapper;
import com.zhyq.park.marketing.yunshan.YunshanClient;
import com.zhyq.park.marketing.yunshan.YunshanVolumeSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** 云山 T+1 数据，按上海时间每天09:30回拉最近数日以发现历史修正。 */
@Slf4j
@Component
public class MktYunshanVolumeJob {
    private final YunshanClient client;
    private final MktYunshanVolumeMapMapper mapMapper;
    private final YunshanVolumeSyncService syncService;
    private final int syncDays;

    public MktYunshanVolumeJob(YunshanClient client, MktYunshanVolumeMapMapper mapMapper,
                             YunshanVolumeSyncService syncService, @Value("${zhyq.yunshan.sync-days:7}") int syncDays) {
        this.client = client; this.mapMapper = mapMapper; this.syncService = syncService; this.syncDays = syncDays;
    }

    @Scheduled(cron = "0 30 9 * * ?", zone = "Asia/Shanghai")
    public void run() {
        if (syncDays < 1 || syncDays > 92) {
            log.error("[yunshan] sync-days 必须为1至92，已跳过定时同步");
            return;
        }
        LocalDate today = LocalDate.now(YunshanVolumeSyncService.SHANGHAI);
        for (MktYunshanVolumeMap mapping : mapMapper.selectList(new LambdaQueryWrapper<MktYunshanVolumeMap>()
                .eq(MktYunshanVolumeMap::getTenantId, MyMetaObjectHandler.DEFAULT_TENANT_ID)
                .eq(MktYunshanVolumeMap::getStatus, 1).orderByAsc(MktYunshanVolumeMap::getId))) {
            if (mapping.getConnectionBaseUrl() == null && !client.configured()) continue;
            try {
                syncService.sync(mapping.getId(), today.minusDays(syncDays), today.minusDays(1), YunshanVolumeSyncService.TRIGGER_SCHEDULED);
            } catch (RuntimeException failure) {
                log.error("[yunshan] 映射 {} 定时同步失败", mapping.getId(), failure);
            }
        }
    }
}
