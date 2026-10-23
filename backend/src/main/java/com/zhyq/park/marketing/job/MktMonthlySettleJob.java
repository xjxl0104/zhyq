package com.zhyq.park.marketing.job;

import com.zhyq.park.marketing.service.MktMonthlySettlementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 每月自动生成结算单：默认每月 8 日 10:00（上海时间）把当月之前、已解冻的出库佣金按“合同 × 月份”各生成一个结算批次。
 * 8 日执行是为了让上月最后一天发货的流水也过了默认 7 天冻结期。
 * 暂停：zhyq.marketing.auto-settle-enabled=false；调整时间：zhyq.marketing.auto-settle-cron。
 */
@Slf4j
@Component
public class MktMonthlySettleJob {
    private final MktMonthlySettlementService service;
    private final boolean enabled;

    public MktMonthlySettleJob(MktMonthlySettlementService service,
                               @Value("${zhyq.marketing.auto-settle-enabled:true}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${zhyq.marketing.auto-settle-cron:0 0 10 8 * ?}", zone = "Asia/Shanghai")
    public void run() {
        if (!enabled) return;
        try {
            service.settleBefore(LocalDate.now(ZoneId.of("Asia/Shanghai")));
        } catch (RuntimeException failure) {
            log.error("[mkt] 自动月结失败", failure);
        }
    }
}
