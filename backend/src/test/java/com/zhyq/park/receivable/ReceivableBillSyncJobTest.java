package com.zhyq.park.receivable;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.finance.service.LateFeeService;
import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.mapper.ReceivableRegisterMapper;
import com.zhyq.park.receivable.service.ReceivableAutoBillService;
import com.zhyq.park.receivable.service.ReceivableBillSyncJob;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登记表 → 下游的自愈定时任务:启动后先跑一遍(部署即回填存量登记的账单),
 * 之后每天自动把"已确认/已生效但账单缺失或过期"的登记补齐,再重算逾期与滞纳金。
 * 这是"下游自动从登记表获取"的兜底保证 —— 即使确认时的自动生成失败,最迟一天内自愈。
 */
@ExtendWith(MockitoExtension.class)
class ReceivableBillSyncJobTest {

    @Mock private ReceivableRegisterMapper registerMapper;
    @Mock private ReceivableAutoBillService autoBillService;
    @Mock private LateFeeService lateFeeService;

    @BeforeAll
    static void initMpLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReceivableRegister.class);
    }

    private ReceivableBillSyncJob job() {
        return new ReceivableBillSyncJob(registerMapper, autoBillService, lateFeeService);
    }

    private static ReceivableRegister register(long id) {
        ReceivableRegister r = new ReceivableRegister();
        r.setId(id);
        return r;
    }

    @Test
    @DisplayName("只取已确认/已生效登记;先补账单、后算滞纳金(新生成的历史账期账单要立刻参与逾期计算)")
    void syncsConfirmedRegistersThenLateFees() {
        when(registerMapper.selectList(any())).thenReturn(List.of(register(7L), register(8L)));
        when(autoBillService.generateFor(List.of(7L, 8L)))
                .thenReturn(new ReceivableAutoBillService.AutoBillSummary(2, 8, 0, 0, 0));
        when(lateFeeService.recalc()).thenReturn(3);

        job().doSync();

        InOrder order = inOrder(autoBillService, lateFeeService);
        order.verify(autoBillService).generateFor(List.of(7L, 8L));
        order.verify(lateFeeService).recalc();

        // 查询条件锁住"只看已确认/已生效"
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ReceivableRegister>> wrapper =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(registerMapper).selectList(wrapper.capture());
        assertThat(wrapper.getValue().getCustomSqlSegment()).contains("status IN");
    }

    @Test
    @DisplayName("没有登记也照样重算滞纳金(逾期状态每天要刷新)")
    void recalculatesLateFeesEvenWithoutRegisters() {
        when(registerMapper.selectList(any())).thenReturn(List.of());
        when(autoBillService.generateFor(List.of()))
                .thenReturn(new ReceivableAutoBillService.AutoBillSummary(0, 0, 0, 0, 0));
        when(lateFeeService.recalc()).thenReturn(0);

        job().doSync();

        verify(lateFeeService).recalc();
    }

    @Test
    @DisplayName("调度入口 sync() 吞掉异常不上抛,任务失败不能炸掉调度线程")
    void syncSwallowsFailures() {
        when(registerMapper.selectList(any())).thenThrow(new RuntimeException("db down"));

        job().sync(); // 不抛出即通过
    }
}
