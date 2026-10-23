package com.zhyq.park.marketing.job;

import com.zhyq.park.marketing.service.MktAlertService;
import com.zhyq.park.marketing.service.MktMonthlySettlementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 每月自动生成结算单：默认每月 8 日 10:00（上海时间）把当月之前、已解冻的出库佣金按“合同 × 月份”各生成一个结算批次。
 * 8 日执行是为了让上月最后一天发货的流水也过了默认 7 天冻结期。
 * 运行结束会推送一条完成通知；有失败的合同月份会推送告警。
 * 暂停：zhyq.marketing.auto-settle-enabled=false；调整时间：zhyq.marketing.auto-settle-cron。
 */
@Slf4j
@Component
public class MktMonthlySettleJob {
    private final MktMonthlySettlementService service;
    private final boolean enabled;
    private final MktAlertService alerts;

    @Autowired
    public MktMonthlySettleJob(MktMonthlySettlementService service,
                               @Value("${zhyq.marketing.auto-settle-enabled:true}") boolean enabled, MktAlertService alerts) {
        this.service = service;
        this.enabled = enabled;
        this.alerts = alerts;
    }

    public MktMonthlySettleJob(MktMonthlySettlementService service, boolean enabled) {
        this(service, enabled, new MktAlertService("", ""));
    }

    @Scheduled(cron = "${zhyq.marketing.auto-settle-cron:0 0 10 8 * ?}", zone = "Asia/Shanghai")
    public void run() {
        if (!enabled) return;
        try {
            MktMonthlySettlementService.Result result = service.settleBefore(LocalDate.now(ZoneId.of("Asia/Shanghai")));
            if (result.failed() > 0) {
                alerts.alert("monthly-settle-failed", "佣金自动月结有失败",
                        result.failed() + " 个合同月份结算失败，例如：" + (result.errors().isEmpty() ? "未知原因" : result.errors().get(0))
                                + "。已成功 " + result.batches() + " 个批次，请到“佣金结算”处理失败的部分。");
            } else if (result.batches() > 0) {
                alerts.notify("佣金自动月结完成", "生成 " + result.batches() + " 个结算批次，共 " + result.commissions() + " 条流水，合计 " + result.amount() + " 元。");
            }
        } catch (RuntimeException failure) {
            log.error("[mkt] 自动月结失败", failure);
            alerts.alert("monthly-settle-crash", "佣金自动月结任务异常", String.valueOf(failure.getMessage()));
        }
    }
}
