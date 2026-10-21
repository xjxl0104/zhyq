package com.zhyq.park.marketing.job;

import com.zhyq.park.marketing.service.MktYunshanVolumeOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每天上海时间 10:00（云山数据 09:30 回拉之后）把每日发货票数生成计佣汇总订单。
 * 默认关闭：配置 zhyq.yunshan.order-enabled=true（环境变量 ZHYQ_YUNSHAN_ORDER_ENABLED=true）后才会自动运行；
 * 关闭时仍可由有权限的人在页面手动触发。
 */
@Slf4j
@Component
public class MktYunshanOrderJob {
    private final MktYunshanVolumeOrderService service;
    private final boolean enabled;

    public MktYunshanOrderJob(MktYunshanVolumeOrderService service,
                              @Value("${zhyq.yunshan.order-enabled:false}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @Scheduled(cron = "0 0 10 * * ?", zone = "Asia/Shanghai")
    public void run() {
        if (!enabled) return;
        try {
            service.generate(null, null, null);
        } catch (RuntimeException failure) {
            log.error("[yunshan-order] 定时生成计佣汇总订单失败", failure);
        }
    }
}
