package com.zhyq.park.marketing.open;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ErpReconcileJob {
    private final ErpReconciliationService service;
    @Scheduled(cron = "0 30 2 * * ?")
    public void run() {
        for (var period : service.periodsToRefresh()) {
            try { service.refreshPeriod(period.warehouseId(), period.periodStart(), period.periodEnd()); }
            catch (Exception e) { log.warn("[erp] reconciliation refresh failed for warehouse {} period {} to {}",
                    period.warehouseId(), period.periodStart(), period.periodEnd(), e); }
        }
    }
}
