package com.zhyq.park.marketing.yunshan;

import com.zhyq.park.marketing.job.MktYunshanOrderJob;
import com.zhyq.park.marketing.service.MktYunshanVolumeOrderService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MktYunshanOrderJobTest {
    private final MktYunshanVolumeOrderService service = mock(MktYunshanVolumeOrderService.class);

    @Test
    void 开启时每天生成计佣汇总订单() {
        new MktYunshanOrderJob(service, true).run();
        verify(service).generate(null, null, null);
    }

    @Test
    void 关闭时不生成() {
        new MktYunshanOrderJob(service, false).run();
        verify(service, never()).generate(null, null, null);
    }

    @Test
    void 生成失败不向调度器抛异常() {
        when(service.generate(null, null, null)).thenThrow(new IllegalStateException("db down"));
        assertThatCode(() -> new MktYunshanOrderJob(service, true).run()).doesNotThrowAnyException();
    }
}
