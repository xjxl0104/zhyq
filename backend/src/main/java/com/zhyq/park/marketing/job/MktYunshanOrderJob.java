package com.zhyq.park.marketing.job;

import com.zhyq.park.marketing.service.MktYunshanVolumeOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每天上海时间 10:00（云山数据 09:30 回拉之后）把每日发货票数生成计佣汇总订单。
 * 默认开启，首次运行会把已拉取的全部历史票数一次性补成计佣汇总订单（幂等，已生成的日期不会重复计佣）。
 * 需要暂停时配置 zhyq.yunshan.order-enabled=false（环境变量 ZHYQ_YUNSHAN_ORDER_ENABLED=false）。
 * 仅园区签合同会计佣；云仓直签只记运营数据，佣金按实际收到的平台费另行登记。
 */
@Slf4j
@Component
public class MktYunshanOrderJob {
    private final MktYunshanVolumeOrderService service;
    private final boolean enabled;

    public MktYunshanOrderJob(MktYunshanVolumeOrderService service,
                              @Value("${zhyq.yunshan.order-enabled:true}") boolean enabled) {
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
