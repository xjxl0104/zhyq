package com.zhyq.park.receivable;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.receivable.dto.ReceivableGenerateResult;
import com.zhyq.park.receivable.service.ReceivableAutoBillService;
import com.zhyq.park.receivable.service.ReceivablePlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 登记表 → 账单的自动生成编排。
 *
 * <p>用户的核心诉求:应收明细登记表是账单/收银台/逾期的唯一源头,下游必须自动派生,
 * 不能靠人挨条点"生成账单"。这里锁住编排的两个关键行为:逐条独立执行(单条失败
 * 不拖垮整批)、汇总数字可信(前端要拿它做确认提示)。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReceivableAutoBillServiceTest {

    @Mock private ReceivablePlanService planService;

    private ReceivableAutoBillService service() {
        return new ReceivableAutoBillService(planService);
    }

    @Test
    @DisplayName("单条生成失败不中断其余登记,失败计数,成功的照常累加")
    void continuesPastSingleFailure() {
        when(planService.generate(1L)).thenReturn(new ReceivableGenerateResult(4, 4, 0, 0));
        when(planService.generate(2L)).thenThrow(new BizException("应收登记表的租户、空间或合同期限尚未完整绑定"));
        when(planService.generate(3L)).thenReturn(new ReceivableGenerateResult(4, 0, 3, 1));

        ReceivableAutoBillService.AutoBillSummary s = service().generateFor(List.of(1L, 2L, 3L));

        assertThat(s.registers()).isEqualTo(3);
        assertThat(s.inserted()).isEqualTo(4);
        assertThat(s.updated()).isEqualTo(3);
        assertThat(s.skipped()).isEqualTo(1);
        assertThat(s.failed()).isEqualTo(1);
        // 第 2 条失败后第 3 条仍然执行了
        verify(planService).generate(3L);
    }

    @Test
    @DisplayName("空/null 入参不查库不炸;重复与 null id 去重过滤")
    void handlesEmptyAndDuplicates() {
        assertThat(service().generateFor(null).registers()).isZero();
        assertThat(service().generateFor(List.of()).registers()).isZero();
        verifyNoInteractions(planService);

        when(planService.generate(7L)).thenReturn(new ReceivableGenerateResult(2, 2, 0, 0));
        ReceivableAutoBillService.AutoBillSummary s =
                service().generateFor(Arrays.asList(7L, null, 7L));
        assertThat(s.registers()).isEqualTo(1);
        verify(planService).generate(7L);
    }
}
