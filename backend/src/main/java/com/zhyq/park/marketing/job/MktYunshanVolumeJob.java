package com.zhyq.park.marketing.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.marketing.entity.MktYunshanSyncLog;
import com.zhyq.park.marketing.entity.MktYunshanVolumeMap;
import com.zhyq.park.marketing.mapper.MktYunshanVolumeMapMapper;
import com.zhyq.park.marketing.service.MktAlertService;
import com.zhyq.park.marketing.yunshan.YunshanClient;
import com.zhyq.park.marketing.yunshan.YunshanVolumeSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** 云山 T+1 数据，按上海时间每天09:30回拉最近数日以发现历史修正；拉取失败会推送告警。 */
@Slf4j
@Component
public class MktYunshanVolumeJob {
    private final YunshanClient client;
    private final MktYunshanVolumeMapMapper mapMapper;
    private final YunshanVolumeSyncService syncService;
    private final int syncDays;
    private final MktAlertService alerts;

    @Autowired
    public MktYunshanVolumeJob(YunshanClient client, MktYunshanVolumeMapMapper mapMapper,
                             YunshanVolumeSyncService syncService, @Value("${zhyq.yunshan.sync-days:35}") int syncDays,
                             MktAlertService alerts) {
        this.client = client; this.mapMapper = mapMapper; this.syncService = syncService; this.syncDays = syncDays; this.alerts = alerts;
    }

    public MktYunshanVolumeJob(YunshanClient client, MktYunshanVolumeMapMapper mapMapper,
                             YunshanVolumeSyncService syncService, int syncDays) {
        this(client, mapMapper, syncService, syncDays, new MktAlertService("", ""));
    }

    @Scheduled(cron = "0 30 9 * * ?", zone = "Asia/Shanghai")
    public void run() {
        if (syncDays < 1 || syncDays > 92) {
            log.error("[yunshan] sync-days 必须为1至92，已跳过定时同步");
            alerts.alert("yunshan-sync-days", "云山发货量定时同步未运行", "YUNSHAN_SYNC_DAYS 必须为 1 至 92，当前为 " + syncDays + "，已跳过今日拉取。");
            return;
        }
        LocalDate today = LocalDate.now(YunshanVolumeSyncService.SHANGHAI);
        for (MktYunshanVolumeMap mapping : mapMapper.selectList(new LambdaQueryWrapper<MktYunshanVolumeMap>()
                .eq(MktYunshanVolumeMap::getTenantId, MyMetaObjectHandler.DEFAULT_TENANT_ID)
                .eq(MktYunshanVolumeMap::getStatus, 1).orderByAsc(MktYunshanVolumeMap::getId))) {
            try {
                MktYunshanSyncLog result = syncService.sync(mapping.getId(), today.minusDays(syncDays), today.minusDays(1), YunshanVolumeSyncService.TRIGGER_SCHEDULED);
                if (result != null && !Integer.valueOf(1).equals(result.getOk())) {
                    alerts.alert("yunshan-sync-" + mapping.getId(), "云山发货量拉取失败",
                            "客户关联 #" + mapping.getId() + " 今日定时拉取失败：" + describe(result) + "。佣金按发货票数计算，请检查接口配置或稍后手动补拉。");
                }
            } catch (RuntimeException failure) {
                log.error("[yunshan] 映射 {} 定时同步失败", mapping.getId(), failure);
                alerts.alert("yunshan-sync-" + mapping.getId(), "云山发货量拉取异常",
                        "客户关联 #" + mapping.getId() + " 定时拉取抛出异常：" + failure.getMessage());
            }
        }
    }

    private static String describe(MktYunshanSyncLog log) {
        String message = log.getErrorMsg() != null ? log.getErrorMsg() : log.getErrorCode();
        return message == null ? "未知原因" : message;
    }
}
