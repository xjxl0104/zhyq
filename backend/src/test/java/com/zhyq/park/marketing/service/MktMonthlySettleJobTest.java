package com.zhyq.park.marketing.service;

import com.zhyq.park.marketing.job.MktMonthlySettleJob;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 每月 8 日自动月结：完成通知、失败告警、开关。 */
class MktMonthlySettleJobTest {
    private final MktMonthlySettlementService settlement = mock(MktMonthlySettlementService.class);
    private final MktAlertService alerts = mock(MktAlertService.class);

    private static MktMonthlySettlementService.Result result(int batches, int failed, String... errors) {
        return new MktMonthlySettlementService.Result(batches, batches * 3, new BigDecimal("12.345"), failed, List.of(errors));
    }

    @Test void 完成后推送汇总通知() {
        when(settlement.settleBefore(any(LocalDate.class))).thenReturn(result(2, 0));
        new MktMonthlySettleJob(settlement, true, alerts).run();
        verify(alerts).notify(eq("佣金自动月结完成"), contains("2 个结算批次"));
    }

    @Test void 没有可结算的佣金时不打扰() {
        when(settlement.settleBefore(any(LocalDate.class))).thenReturn(result(0, 0));
        new MktMonthlySettleJob(settlement, true, alerts).run();
        verifyNoInteractions(alerts);
    }

    @Test void 有失败时告警并带上原因() {
        when(settlement.settleBefore(any(LocalDate.class))).thenReturn(result(1, 1, "合同 5 · 2026-09：流水 1 不是可结算状态"));
        new MktMonthlySettleJob(settlement, true, alerts).run();
        verify(alerts).alert(eq("monthly-settle-failed"), anyString(), contains("不是可结算状态"));
    }

    @Test void 抛异常不向调度器抛出并告警_关闭时不运行() {
        when(settlement.settleBefore(any(LocalDate.class))).thenThrow(new IllegalStateException("db down"));
        assertThatCode(() -> new MktMonthlySettleJob(settlement, true, alerts).run()).doesNotThrowAnyException();
        verify(alerts).alert(eq("monthly-settle-crash"), anyString(), contains("db down"));
        clearInvocations(settlement);
        new MktMonthlySettleJob(settlement, false, alerts).run();
        verifyNoInteractions(settlement);
    }

    @Test void 每月8日上午10点在上海时区执行() throws Exception {
        Scheduled schedule = MktMonthlySettleJob.class.getMethod("run").getAnnotation(Scheduled.class);
        assertThat(schedule.zone()).isEqualTo("Asia/Shanghai");
        assertThat(schedule.cron()).contains("0 0 10 8 * ?");
    }
}
