package com.zhyq.park.marketing.job;

import com.zhyq.park.marketing.service.MktAlertService;
import com.zhyq.park.marketing.service.MktYunshanVolumeOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每天上海时间 10:00（云山数据 09:30 回拉之后）把每日发货票数生成计佣汇总订单。
 * 默认开启；只自动处理最近 35 天，更早的历史要先在页面看回补预览、确认每单金额和分成链路后手动回补。
 * 园区签和云仓直签合同都按发货量计佣；其他签约方式不计佣。
 * 需要暂停时配置 zhyq.yunshan.order-enabled=false（环境变量 ZHYQ_YUNSHAN_ORDER_ENABLED=false）。
 * 有日期生成失败（如合同还没配置每单佣金）会推送告警。
 */
@Slf4j
@Component
public class MktYunshanOrderJob {
    private final MktYunshanVolumeOrderService service;
    private final boolean enabled;
    private final MktAlertService alerts;

    @Autowired
    public MktYunshanOrderJob(MktYunshanVolumeOrderService service,
                              @Value("${zhyq.yunshan.order-enabled:true}") boolean enabled, MktAlertService alerts) {
        this.service = service;
        this.enabled = enabled;
        this.alerts = alerts;
    }

    public MktYunshanOrderJob(MktYunshanVolumeOrderService service, boolean enabled) {
        this(service, enabled, new MktAlertService("", ""));
    }

    @Scheduled(cron = "0 0 10 * * ?", zone = "Asia/Shanghai")
    public void run() {
        if (!enabled) return;
        try {
            MktYunshanVolumeOrderService.Result result = service.generateScheduled();
            if (result != null && result.failed() > 0) {
                alerts.alert("yunshan-order-failed", "云山发货量佣金订单生成失败",
                        "今日有 " + result.failed() + " 个发货日没有生成佣金订单，例如：" + (result.errors().isEmpty() ? "未知原因" : result.errors().get(0))
                                + "。常见原因：合同还没配置每单总佣金；配置后下次运行会自动补上。");
            }
        } catch (RuntimeException failure) {
            log.error("[yunshan-order] 定时生成计佣汇总订单失败", failure);
            alerts.alert("yunshan-order-crash", "云山发货量佣金订单任务异常", String.valueOf(failure.getMessage()));
        }
    }
}
