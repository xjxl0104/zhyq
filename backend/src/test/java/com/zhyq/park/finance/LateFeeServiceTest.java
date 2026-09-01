package com.zhyq.park.finance;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.finance.service.LateFeeService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 滞纳金/逾期计算(从 BillController 抽出为服务,供手动端点与每日自愈任务共用)。
 *
 * <p>逻辑从 BillControllerTest 原样迁移:条件更新语义(不把刚结清的账单打回逾期)
 * 是资金正确性的回归锁,搬家不许丢。</p>
 */
@ExtendWith(MockitoExtension.class)
class LateFeeServiceTest {

    @Mock private BillMapper billMapper;

    @BeforeAll
    static void initMpLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Bill.class);
    }

    private LateFeeService service() {
        return new LateFeeService(billMapper);
    }

    private static Bill overdueBill(long id, String amount, String paid, int daysOverdue) {
        Bill b = new Bill();
        b.setId(id);
        b.setDirection(1);
        b.setStatus(3);
        b.setAmount(new BigDecimal(amount));
        b.setPaidAmount(new BigDecimal(paid));
        b.setDueDate(LocalDate.now().minusDays(daysOverdue));
        return b;
    }

    @Test
    @DisplayName("条件更新:SET 走实体(审计字段自动填充),WHERE 带可催缴状态与本金未收齐守卫")
    void recalcUsesConditionalUpdate() {
        when(billMapper.selectList(any())).thenReturn(List.of(overdueBill(5L, "1000", "0", 10)));
        when(billMapper.update(any(), any())).thenReturn(1);

        assertThat(service().recalc()).isEqualTo(1);

        verify(billMapper, never()).updateById(any(Bill.class));
        ArgumentCaptor<Bill> patch = ArgumentCaptor.forClass(Bill.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaUpdateWrapper<Bill>> wrapper =
                ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
        verify(billMapper).update(patch.capture(), wrapper.capture());
        assertThat(patch.getValue().getStatus()).isEqualTo(6);
        assertThat(patch.getValue().getLateFee()).isEqualByComparingTo("5.00"); // 1000×0.0005×10
        assertThat(patch.getValue().getOverdueDays()).isEqualTo(10);
        assertThat(patch.getValue().getPaidAmount()).isNull();
        assertThat(wrapper.getValue().getCustomSqlSegment()).contains("paid_amount < amount");
    }

    @Test
    @DisplayName("快照后被收满的账单条件不满足(updated==0),不计数、不被打回逾期")
    void recalcSkipsBillSettledAfterSnapshot() {
        when(billMapper.selectList(any())).thenReturn(List.of(overdueBill(5L, "1000", "0", 10)));
        when(billMapper.update(any(), any())).thenReturn(0);

        assertThat(service().recalc()).isZero();
    }

    @Test
    @DisplayName("补录的历史账单不追溯:创建日即今天 → 滞纳金 0,但仍标逾期、逾期天数按真实应收日")
    void backfilledBillsAccrueFromCreationDate() {
        Bill backfilled = overdueBill(5L, "1000", "0", 10);
        backfilled.setCreateTime(java.time.LocalDateTime.now()); // 今天刚由自愈任务补录
        when(billMapper.selectList(any())).thenReturn(List.of(backfilled));
        when(billMapper.update(any(), any())).thenReturn(1);

        assertThat(service().recalc()).isEqualTo(1);

        ArgumentCaptor<Bill> patch = ArgumentCaptor.forClass(Bill.class);
        verify(billMapper).update(patch.capture(), any());
        assertThat(patch.getValue().getLateFee()).isEqualByComparingTo("0");
        assertThat(patch.getValue().getStatus()).isEqualTo(6);
        assertThat(patch.getValue().getOverdueDays()).isEqualTo(10);
    }

    @Test
    @DisplayName("补录 4 天后:滞纳金只按补录以来的 4 天算,不含此前 6 天")
    void backfilledBillsAccruePartially() {
        Bill backfilled = overdueBill(5L, "1000", "0", 10);
        backfilled.setCreateTime(java.time.LocalDateTime.now().minusDays(4));
        when(billMapper.selectList(any())).thenReturn(List.of(backfilled));
        when(billMapper.update(any(), any())).thenReturn(1);

        service().recalc();

        ArgumentCaptor<Bill> patch = ArgumentCaptor.forClass(Bill.class);
        verify(billMapper).update(patch.capture(), any());
        assertThat(patch.getValue().getLateFee()).isEqualByComparingTo("2.00"); // 1000×0.0005×4
    }

    @Test
    @DisplayName("本金已收齐的逾期账单直接跳过,不发更新")
    void skipsBillsWithoutOutstandingPrincipal() {
        Bill paid = overdueBill(5L, "1000", "1000", 10);
        when(billMapper.selectList(any())).thenReturn(List.of(paid));

        assertThat(service().recalc()).isZero();
        verify(billMapper, never()).update(any(), any());
    }
}
