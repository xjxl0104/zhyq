package com.zhyq.park.marketing.job;

import com.zhyq.park.marketing.service.MktPositionReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 岗位晋升/降级复核(PARK-MKT-001 §5.5):每月 1 日 02:00。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MktPositionReviewJob {

    private final MktPositionReviewService reviewService;

    @Scheduled(cron = "0 0 2 1 * ?")
    public void run() {
        try {
            int n = reviewService.reviewAll();
            log.info("[mkt] PositionReviewJob 调整 {} 人", n);
        } catch (Exception e) {
            log.error("[mkt] PositionReviewJob 失败", e);
        }
    }
}
