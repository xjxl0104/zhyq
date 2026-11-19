package com.zhyq.park.marketing.service;

import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.job.MktAutoWithdrawJob;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mp.MpSettlementControllerAccess;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MktAutoWithdrawJobTest {
    private static MktPromoter p(long id) { var p = new MktPromoter(); p.setId(id); p.setName("伙伴" + id); return p; }

    @Test void 每位伙伴把全部可提现余额提交一次_跳过余额不足和已有处理中的() {
        var promoters = mock(MktPromoterMapper.class);
        var withdrawals = mock(MktWithdrawalService.class);
        when(promoters.selectList(any())).thenReturn(List.of(p(1), p(2), p(3), p(4)));
        when(withdrawals.autoWithdrawAmount(1L)).thenReturn(new BigDecimal("12.34"));
        when(withdrawals.autoWithdrawAmount(2L)).thenReturn(new BigDecimal("0.00"));
        when(withdrawals.autoWithdrawAmount(3L)).thenReturn(new BigDecimal("5.00"));
        when(withdrawals.hasOpenWithdrawal(3L)).thenReturn(true);
        when(withdrawals.autoWithdrawAmount(4L)).thenReturn(new BigDecimal("1.00"));
        when(withdrawals.apply(eq(4L), any(), eq(true))).thenThrow(new com.zhyq.park.common.exception.BizException("收款资料尚未通过人工审核"));
        var result = new MktAutoWithdrawJob(promoters, withdrawals, new MktAlertService("", ""), true).runOnce();
        verify(withdrawals).apply(1L, new BigDecimal("12.34"), true);
        verify(withdrawals, never()).apply(eq(2L), any(), anyBoolean());
        verify(withdrawals, never()).apply(eq(3L), any(), anyBoolean());
        assertThat(result.submitted()).isEqualTo(1);
        assertThat(result.amount()).isEqualByComparingTo("12.34");
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors().get(0)).contains("伙伴4");
    }

    @Test void 每天上午10点检查是否到自动提现日() throws Exception {
        Scheduled s = MktAutoWithdrawJob.class.getMethod("run").getAnnotation(Scheduled.class);
        assertThat(s.cron()).contains("0 0 10 * * ?");
        assertThat(s.zone()).isEqualTo("Asia/Shanghai");
    }

    @Test void 结算提现页把月份行按合同归并() {
        var rows = List.<Map<String, Object>>of(
                Map.of("ym", "2026-11", "contract_id", 5L, "contract_no", "CS-1", "customer_name", "甲", "total", new BigDecimal("4.845"),
                        "frozen", new BigDecimal("1.425"), "settleable", new BigDecimal("3.420"), "settled", BigDecimal.ZERO, "tickets", 51),
                Map.of("ym", "2026-11", "contract_id", 6L, "contract_no", "CS-2", "customer_name", "乙", "total", BigDecimal.ONE,
                        "frozen", BigDecimal.ZERO, "settleable", BigDecimal.ZERO, "settled", BigDecimal.ONE, "tickets", 2),
                Map.of("ym", "2026-10", "contract_id", 5L, "contract_no", "CS-1", "customer_name", "甲", "total", new BigDecimal("4.085"),
                        "frozen", BigDecimal.ZERO, "settleable", BigDecimal.ZERO, "settled", new BigDecimal("4.085"), "tickets", 43));
        var contracts = MpSettlementControllerAccess.contracts(rows);
        assertThat(contracts).hasSize(2);
        assertThat(contracts.get(0)).containsEntry("contractId", 5L).containsEntry("tickets", 94L).containsEntry("monthCount", 2)
                .containsEntry("latestMonth", "2026-11");
        assertThat((BigDecimal) contracts.get(0).get("total")).isEqualByComparingTo("8.930");
        assertThat((BigDecimal) contracts.get(0).get("settled")).isEqualByComparingTo("4.085");
    }

    @Test void 只有白名单业务能由系统发起审批() {
        assertThat(com.zhyq.park.workflow.service.WorkflowService.SYSTEM_STARTED).containsExactly("withdrawal");
    }
}
