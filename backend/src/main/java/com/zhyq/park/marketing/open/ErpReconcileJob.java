package com.zhyq.park.marketing.open;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ErpReconcileJob {
    private final ErpReliabilityService service;
    @Scheduled(cron = "0 30 2 * * ?")
    public void run() { log.debug("[erp] reconcile snapshot job ready; snapshots are generated per settlement cycle"); }
}
