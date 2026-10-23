package com.zhyq.park.marketing.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.marketing.entity.MktYunshanVolumeMap;
import com.zhyq.park.marketing.mapper.MktYunshanVolumeMapMapper;
import com.zhyq.park.marketing.service.MktAlertService;
import com.zhyq.park.marketing.yunshan.YunshanVolumeSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 每天 11:00 巡检云山发货量同步（09:30 拉取、10:00 生成佣金之后）：
 * 启用中的客户关联如果最近一次拉取失败，或者超过 36 小时没有拉取过（定时任务没跑/服务停过），推送告警。
 */
@Slf4j
@Component
public class MktYunshanHealthJob {
    static final Duration STALE_AFTER = Duration.ofHours(36);

    private final MktYunshanVolumeMapMapper mapMapper;
    private final MktAlertService alerts;

    public MktYunshanHealthJob(MktYunshanVolumeMapMapper mapMapper, MktAlertService alerts) {
        this.mapMapper = mapMapper; this.alerts = alerts;
    }

    @Scheduled(cron = "0 0 11 * * ?", zone = "Asia/Shanghai")
    public void run() {
        try {
            check(LocalDateTime.now(YunshanVolumeSyncService.SHANGHAI));
        } catch (RuntimeException failure) {
            log.error("[yunshan] 同步巡检失败", failure);
        }
    }

    public int check(LocalDateTime now) {
        int problems = 0;
        for (MktYunshanVolumeMap mapping : mapMapper.selectList(new LambdaQueryWrapper<MktYunshanVolumeMap>()
                .eq(MktYunshanVolumeMap::getTenantId, MyMetaObjectHandler.DEFAULT_TENANT_ID)
                .eq(MktYunshanVolumeMap::getStatus, 1).orderByAsc(MktYunshanVolumeMap::getId))) {
            if (mapping.getLastSyncAt() == null || Duration.between(mapping.getLastSyncAt(), now).compareTo(STALE_AFTER) > 0) {
                problems++;
                alerts.alert("yunshan-stale-" + mapping.getId(), "云山发货量长时间没有拉取",
                        "客户关联 #" + mapping.getId() + " 最近一次拉取：" + (mapping.getLastSyncAt() == null ? "从未拉取" : mapping.getLastSyncAt())
                                + "，已超过 36 小时。请检查后端是否正常运行、接口是否可用。");
            } else if (Integer.valueOf(0).equals(mapping.getLastSyncOk())) {
                problems++;
                // 与 09:30 拉取失败告警共用 key，12 小时内不重复推送
                alerts.alert("yunshan-sync-" + mapping.getId(), "云山发货量拉取失败",
                        "客户关联 #" + mapping.getId() + " 最近一次拉取（" + mapping.getLastSyncAt() + "）失败，请检查接口配置或手动补拉。");
            }
        }
        return problems;
    }
}
