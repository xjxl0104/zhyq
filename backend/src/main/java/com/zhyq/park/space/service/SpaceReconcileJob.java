package com.zhyq.park.space.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpaceReconcileJob {
    private final SpaceSyncService syncService;

    /** 每日 03:17 全量对账，抓增量遗漏/手工改库漂移 */
    @Scheduled(cron = "0 17 3 * * ?")
    public void nightlyReconcile() {
        try { syncService.reconcile(); }
        catch (Exception e) { log.error("[space] nightly reconcile failed", e); }
    }
}
