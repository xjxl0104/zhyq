package com.zhyq.park.marketing.open;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ErpHeartbeatJob {
    private final ErpReliabilityService service;
    @Scheduled(initialDelay = 600_000, fixedDelay = 600_000)
    public void run() { try { service.heartbeat(LocalDateTime.now()); } catch (Exception e) { log.error("[erp] heartbeat failed", e); } }
}
