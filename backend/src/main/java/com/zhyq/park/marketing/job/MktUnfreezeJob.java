package com.zhyq.park.marketing.job;

import com.zhyq.park.marketing.service.MktCommissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 路径 B 解冻(PARK-MKT-001 §5.5):每小时把 unfreeze_at 已到、订单仍是已确认的冻结流水转为可结算。
 * 单机 @Scheduled,与 contract/service/ContractExpiryJob 同款,无分布式锁(部署只有一个后端实例)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MktUnfreezeJob {

    private final MktCommissionService commissionService;

    @Scheduled(initialDelay = 300_000, fixedDelay = 3_600_000)
    public void run() {
        try {
            int n = commissionService.unfreezeDue(LocalDateTime.now());
            if (n > 0) {
                log.info("[mkt] UnfreezeJob 解冻 {} 行", n);
            }
        } catch (Exception e) {
            log.error("[mkt] UnfreezeJob 失败", e);
        }
    }
}
