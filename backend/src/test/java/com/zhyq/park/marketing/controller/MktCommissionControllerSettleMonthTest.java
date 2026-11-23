package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktPromoterCommission;
import com.zhyq.park.marketing.entity.MktReferralOrder;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.mapper.MktPromoterCommissionMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktReferralOrderMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.mapper.MktSettleBatchMapper;
import com.zhyq.park.marketing.service.MktCommissionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 按合同月结：可选择合同开始月到上个月之间的任意月份，更早月份可随时补结。 */
class MktCommissionControllerSettleMonthTest {
    private final MktPromoterCommissionMapper commissions = mock(MktPromoterCommissionMapper.class);
    private final MktServiceContractMapper contracts = mock(MktServiceContractMapper.class);
    private final MktCommissionService commissionService = mock(MktCommissionService.class);
    private final MktCommissionController controller = new MktCommissionController(commissions,
            mock(MktSettleBatchMapper.class), mock(MktPromoterMapper.class), contracts, commissionService);

    @BeforeAll
    static void metadata() {
        var a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(a, MktPromoterCommission.class);
        TableInfoHelper.initTableInfo(a, MktReferralOrder.class);
    }

    private MktServiceContract contract(LocalDate start) {
        MktServiceContract contract = new MktServiceContract();
        contract.setId(5L);
        contract.setContractNo("CS-TEST");
        contract.setStartDate(start);
        when(contracts.selectById(5L)).thenReturn(contract);
        return contract;
    }

    @Test
    void 当月和未来月份不能结算() {
        contract(LocalDate.now().minusYears(2));
        String current = YearMonth.now().toString();
        String future = YearMonth.now().plusMonths(1).toString();
        assertThatThrownBy(() -> controller.settleContract(Map.of("contractId", 5L, "month", current)))
                .isInstanceOf(BizException.class).hasMessageContaining("当月之前");
        assertThatThrownBy(() -> controller.settleContract(Map.of("contractId", 5L, "month", future)))
                .isInstanceOf(BizException.class).hasMessageContaining("当月之前");
        verifyNoInteractions(commissionService);
    }

    @Test
    void 结算月份不能早于合同开始月份() {
        contract(LocalDate.now().minusMonths(3).withDayOfMonth(15));
        String tooEarly = YearMonth.now().minusMonths(5).toString();
        assertThatThrownBy(() -> controller.settleContract(Map.of("contractId", 5L, "month", tooEarly)))
                .isInstanceOf(BizException.class).hasMessageContaining("不能早于合同开始月份");
        verifyNoInteractions(commissionService);
    }

    @Test
    void 月份格式错误被拒绝() {
        contract(LocalDate.now().minusYears(1));
        assertThatThrownBy(() -> controller.settleContract(Map.of("contractId", 5L, "month", "2026/06")))
                .isInstanceOf(BizException.class).hasMessageContaining("yyyy-MM");
    }

    @Test
    void 更早月份可以补结且不受3到10号限制() {
        contract(LocalDate.now().minusYears(2));
        when(commissions.selectList(any())).thenReturn(List.of());
        YearMonth month = YearMonth.now().minusMonths(2);
        var result = controller.settleContract(Map.of("contractId", 5L, "month", month.toString())).getData();
        assertThat(result.get("month")).isEqualTo(month.toString());
        assertThat(result.get("periodStart")).isEqualTo(month.atDay(1).toString());
        assertThat(result.get("periodEnd")).isEqualTo(month.atEndOfMonth().toString());
        assertThat(result.get("settleCount")).isEqualTo(0);
        assertThat(result.get("batchNo")).isNull();
    }

    @Test
    void 缺省月份为上个月且任何日期都可结算() {
        contract(LocalDate.now().minusYears(2));
        when(commissions.selectList(any())).thenReturn(List.of());
        var result = controller.settleContract(Map.of("contractId", 5L)).getData();
        assertThat(result.get("month")).isEqualTo(YearMonth.now().minusMonths(1).toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void 分页支持非出库佣金筛选并补充来源类型与基数金额() {
        var orderMapper = mock(MktReferralOrderMapper.class);
        ReflectionTestUtils.setField(controller, "orderMapper", orderMapper);
        var row = new MktPromoterCommission();
        row.setId(88L); row.setReferralOrderId(501L); row.setPromoterId(7L); row.setAmount(new BigDecimal("2000.00")); row.setStatus(2); row.setSign(1);
        when(commissions.selectPage(any(), any())).thenAnswer(inv -> {
            Page<MktPromoterCommission> p = inv.getArgument(0);
            p.setRecords(List.of(row));
            p.setTotal(1);
            return p;
        });
        var order = new MktReferralOrder();
        order.setId(501L); order.setSourceType(MktCommissionService.SOURCE_CONTRACT_BONUS);
        order.setSourceNo("BONUS-9"); order.setBaseAmount(new BigDecimal("2000.00"));
        when(orderMapper.selectBatchIds(List.of(501L))).thenReturn(List.of(order));

        var res = controller.page(1, 20, null, 2, null, null, null, null, null, null, true).getData();
        assertThat(res.getRecords()).hasSize(1);
        assertThat(res.getRecords().get(0))
                .containsEntry("orderSourceType", MktCommissionService.SOURCE_CONTRACT_BONUS)
                .containsEntry("orderBaseAmount", new BigDecimal("2000.00"));

        ArgumentCaptor<LambdaQueryWrapper<MktPromoterCommission>> qw = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        org.mockito.Mockito.verify(commissions).selectPage(any(), qw.capture());
        assertThat(qw.getValue().getSqlSegment()).contains("source_type IN (1, 3, 4)");
    }
}
