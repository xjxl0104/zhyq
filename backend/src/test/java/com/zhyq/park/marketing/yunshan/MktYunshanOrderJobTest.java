package com.zhyq.park.marketing.yunshan;

import com.zhyq.park.marketing.job.MktYunshanOrderJob;
import com.zhyq.park.marketing.service.MktAlertService;
import com.zhyq.park.marketing.service.MktYunshanVolumeOrderService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MktYunshanOrderJobTest {
    private final MktYunshanVolumeOrderService service = mock(MktYunshanVolumeOrderService.class);
    private final MktAlertService alerts = mock(MktAlertService.class);

    private static MktYunshanVolumeOrderService.Result result(int failed, String... errors) {
        return new MktYunshanVolumeOrderService.Result(1, 0, 0, 0, failed, List.of(errors));
    }

    @Test
    void 开启时每天自动生成最近35天的计佣汇总订单() {
        when(service.generateScheduled()).thenReturn(result(0));
        new MktYunshanOrderJob(service, true, alerts).run();
        verify(service).generateScheduled();
        verifyNoInteractions(alerts);
    }

    @Test
    void 关闭时不生成() {
        new MktYunshanOrderJob(service, false, alerts).run();
        verify(service, never()).generateScheduled();
    }

    @Test
    void 有日期生成失败时推送告警并带上原因() {
        when(service.generateScheduled()).thenReturn(result(2, "关联 7 · 2026-10-17：客户尚未设置 P4 每单佣金"));
        new MktYunshanOrderJob(service, true, alerts).run();
        verify(alerts).alert(eq("yunshan-order-failed"), anyString(), contains("尚未设置 P4 每单佣金"));
    }

    @Test
    void 生成抛异常不向调度器抛出并推送告警() {
        when(service.generateScheduled()).thenThrow(new IllegalStateException("db down"));
        assertThatCode(() -> new MktYunshanOrderJob(service, true, alerts).run()).doesNotThrowAnyException();
        verify(alerts).alert(eq("yunshan-order-crash"), anyString(), contains("db down"));
    }

    @Test
    void 不带告警服务的构造方式仍可使用() {
        when(service.generateScheduled()).thenThrow(new IllegalStateException("db down"));
        assertThatCode(() -> new MktYunshanOrderJob(service, true).run()).doesNotThrowAnyException();
    }
}
