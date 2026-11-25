package com.zhyq.park.marketing.job;

import com.zhyq.park.marketing.service.MktPublicSeaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 每小时巡检内部公海池认领单：超 72h（可配置 sea_sla_hours）未登记首通核实纪要自动弹回公海。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MktPublicSeaSlaJob {

    private final MktPublicSeaService publicSeaService;

    @Scheduled(cron = "0 15 * * * *")
    public void reclaimOverdue() {
        int reclaimed = publicSeaService.expireOverdueClaims(LocalDateTime.now());
        if (reclaimed > 0) {
            log.info("[mkt-public-sea] 首触达超时自动弹回内部公海 {} 户", reclaimed);
        }
    }
}
