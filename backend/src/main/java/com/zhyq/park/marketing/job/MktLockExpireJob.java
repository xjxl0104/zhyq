package com.zhyq.park.marketing.job;

import com.zhyq.park.common.notify.NotificationService;
import com.zhyq.park.marketing.entity.MktCustomerLock;
import com.zhyq.park.marketing.service.MktLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 锁客到期(PARK-MKT-001 §5.5):每日 02:00 释放到期锁定;到期前 15 天给伙伴发站内提醒。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MktLockExpireJob {

    private static final int REMIND_DAYS = 15;

    private final MktLockService lockService;
    private final com.zhyq.park.marketing.service.MktPartnerNoticeService notificationService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void run() {
        try {
            int n = lockService.expire(LocalDateTime.now());
            if (n > 0) {
                log.info("[mkt] LockExpireJob 释放 {} 条锁定", n);
            }
            for (MktCustomerLock l : lockService.expiringWithin(LocalDateTime.now(), REMIND_DAYS)) {
                notificationService.push(l.getPromoterId(), "客户锁定即将到期",
                        "您报备的客户锁定将于 " + l.getLockUntil().toLocalDate() + " 到期,请尽快推进");
            }
        } catch (Exception e) {
            log.error("[mkt] LockExpireJob 失败", e);
        }
    }
}
