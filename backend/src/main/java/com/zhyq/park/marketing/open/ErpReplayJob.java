package com.zhyq.park.marketing.open;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ERP 事件重放定时任务:扫 RECEIVED 状态事件重新投递。
 * 与 {@link ErpRetryJob}(把 RETRY 翻成 RECEIVED)配套,本 job 是消费端。
 * 频率 3 分钟、单次上限 {@link ErpReplayWorker#REPLAY_BATCH} 条,避免共用服务器长事务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpReplayJob {
    private final ErpReplayWorker worker;

    @Scheduled(initialDelay = 120_000, fixedDelay = 180_000)
    public void run() {
        try {
            int n = worker.replayDue();
            if (n > 0) log.info("[erp] replay processed {} event(s)", n);
        } catch (Exception e) {
            log.error("[erp] replay job failed", e);
        }
    }
}
