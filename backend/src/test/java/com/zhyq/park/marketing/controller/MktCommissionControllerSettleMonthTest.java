package com.zhyq.park.marketing.controller;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.mapper.MktPromoterCommissionMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.mapper.MktSettleBatchMapper;
import com.zhyq.park.marketing.service.MktCommissionService;
import org.junit.jupiter.api.Test;

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
    void 缺省月份为上个月且仍受3到10号限制() {
        contract(LocalDate.now().minusYears(2));
        when(commissions.selectList(any())).thenReturn(List.of());
        int day = LocalDate.now().getDayOfMonth();
        if (day >= 3 && day <= 10) {
            var result = controller.settleContract(Map.of("contractId", 5L)).getData();
            assertThat(result.get("month")).isEqualTo(YearMonth.now().minusMonths(1).toString());
        } else {
            assertThatThrownBy(() -> controller.settleContract(Map.of("contractId", 5L)))
                    .isInstanceOf(BizException.class).hasMessageContaining("3-10");
        }
    }
}
