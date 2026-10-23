package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktPromoterCommission;
import com.zhyq.park.marketing.mapper.MktPromoterCommissionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 按月自动结算：每个（合同，月份）一个批次，只结算当月之前、已解冻的流水。 */
class MktMonthlySettlementServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MktPromoterCommissionMapper commissions = mock(MktPromoterCommissionMapper.class);
    private final MktCommissionService commissionService = mock(MktCommissionService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private MktMonthlySettlementService service;

    @BeforeAll static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MktPromoterCommission.class);
    }
    @BeforeEach void setup() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new MktMonthlySettlementService(jdbc, commissions, commissionService, transactions);
    }
    private static MktPromoterCommission commission(long id, String amount) {
        var c = new MktPromoterCommission(); c.setId(id); c.setAmount(new BigDecimal(amount)); c.setSign(1); c.setStatus(2); return c;
    }
    private void pairs(Map<String, Object>... rows) {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(rows));
    }

    @Test @SuppressWarnings("unchecked")
    void 每个合同每个月份各生成一个结算批次() {
        pairs(Map.of("contract_id", 5L, "ym", "2026-09"), Map.of("contract_id", 5L, "ym", "2026-10"));
        when(commissions.selectList(any())).thenReturn(List.of(commission(1, "0.190"), commission(2, "1.235")), List.of(commission(3, "0.380")));
        when(commissionService.settle(anyList(), eq("system"))).thenReturn("SB-1", "SB-2");
        var result = service.settleBefore(LocalDate.of(2026, 11, 8));
        assertThat(result.batches()).isEqualTo(2);
        assertThat(result.commissions()).isEqualTo(3);
        assertThat(result.amount()).isEqualByComparingTo("1.805");
        assertThat(result.failed()).isZero();
        verify(commissionService).settle(List.of(1L, 2L), "system");
        verify(commissionService).settle(List.of(3L), "system");
    }

    @Test @SuppressWarnings("unchecked")
    void 当月之前的截止日期传给查询且没有可结算流水时不生成批次() {
        pairs(Map.of("contract_id", 5L, "ym", "2026-09"));
        when(commissions.selectList(any())).thenReturn(List.of());
        var result = service.settleBefore(LocalDate.of(2026, 11, 8));
        assertThat(result.batches()).isZero();
        verify(commissionService, never()).settle(anyList(), anyString());
        verify(jdbc).queryForList(anyString(), eq("2026-11-01 00:00:00"));
    }

    @Test @SuppressWarnings("unchecked")
    void 一个合同失败不影响其他合同并记录原因() {
        pairs(Map.of("contract_id", 5L, "ym", "2026-09"), Map.of("contract_id", 6L, "ym", "2026-09"));
        when(commissions.selectList(any())).thenReturn(List.of(commission(1, "0.100")), List.of(commission(2, "0.200")));
        when(commissionService.settle(List.of(1L), "system")).thenThrow(new BizException("流水 1 不是可结算状态"));
        when(commissionService.settle(List.of(2L), "system")).thenReturn("SB-2");
        var result = service.settleBefore(LocalDate.of(2026, 10, 8));
        assertThat(result.batches()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors()).singleElement().asString().contains("合同 5").contains("2026-09");
    }
}
