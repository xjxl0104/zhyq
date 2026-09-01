package com.zhyq.park.finance;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.finance.controller.BillController;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.finance.mapper.InvoiceMapper;
import com.zhyq.park.finance.mapper.PaymentMapper;
import com.zhyq.park.finance.service.FinanceViewEnricher;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 账单接口的资金完整性守卫。
 *
 * <p>三条回归锁:
 * ① calcLateFee 必须条件更新 —— 此前旧读后盲写 status=6,会把快照与写入之间
 *    刚被收满结清的账单打回"逾期"。
 * ② PUT 只放行非资金字段 —— 此前全字段 updateById,持 bill:edit 即可绕开
 *    收款服务直接篡改 paid_amount/status/amount。
 * ③ DELETE 有收款/发票记录或已有实收的账单必须拒删 —— 软删会释放 billingKey
 *    幂等键,让同一账期重复生成账单,已收的钱还从统计里消失。</p>
 */
@ExtendWith(MockitoExtension.class)
class BillControllerTest {

    @Mock private BillMapper billMapper;
    @Mock private FinanceViewEnricher viewEnricher;
    @Mock private PaymentMapper paymentMapper;
    @Mock private InvoiceMapper invoiceMapper;

    @BeforeAll
    static void initMpLambdaCache() {
        // 纯单测没有 Spring/MyBatis 上下文;LambdaUpdateWrapper.set() 解析列名是即时的
        // (eq/in 是惰性的),需要手工装载 Bill 的 TableInfo/lambda 缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Bill.class);
    }

    private BillController controller() {
        return new BillController(billMapper, viewEnricher, paymentMapper, invoiceMapper);
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

    // ---------- calcLateFee ----------

    @Test
    @DisplayName("calcLateFee 走条件更新,不再 updateById 盲写;WHERE 必须带本金未收齐条件")
    void calcLateFeeUsesConditionalUpdate() {
        when(billMapper.selectList(any())).thenReturn(List.of(overdueBill(5L, "1000", "0", 10)));
        when(billMapper.update(any(), any())).thenReturn(1);

        Integer count = controller().calcLateFee().getData();

        assertThat(count).isEqualTo(1);
        verify(billMapper, never()).updateById(any(Bill.class));
        // SET 走实体(审计字段自动填充生效),守卫条件锁在 WHERE 里
        ArgumentCaptor<Bill> patch = ArgumentCaptor.forClass(Bill.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Bill>> wrapper =
                ArgumentCaptor.forClass((Class) com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(billMapper).update(patch.capture(), wrapper.capture());
        assertThat(patch.getValue().getStatus()).isEqualTo(6);
        assertThat(patch.getValue().getLateFee()).isEqualByComparingTo("5.00"); // 1000×0.0005×10
        assertThat(patch.getValue().getOverdueDays()).isEqualTo(10);
        assertThat(patch.getValue().getPaidAmount()).isNull(); // 实收绝不允许被这里改
        assertThat(wrapper.getValue().getCustomSqlSegment()).contains("paid_amount < amount");
    }

    @Test
    @DisplayName("快照后被收满的账单条件不满足(updated==0),不计数、不被打回逾期")
    void calcLateFeeSkipsBillSettledAfterSnapshot() {
        when(billMapper.selectList(any())).thenReturn(List.of(overdueBill(5L, "1000", "0", 10)));
        when(billMapper.update(any(), any())).thenReturn(0);

        Integer count = controller().calcLateFee().getData();

        assertThat(count).isZero();
    }

    // ---------- PUT 字段白名单 ----------

    @Test
    @DisplayName("PUT 只放行非资金字段:paid_amount/status/amount/late_fee 一律不进 SET")
    void updateOnlyTouchesSafeFields() {
        when(billMapper.updateById(any(Bill.class))).thenReturn(1);
        Bill request = new Bill();
        request.setId(5L);
        request.setRemark("补备注");
        request.setTenantRefId(8L);
        request.setFeeType("物业费");
        request.setDueDate(LocalDate.of(2026, 9, 30));
        // 恶意/误传的资金字段
        request.setPaidAmount(new BigDecimal("999999"));
        request.setStatus(5);
        request.setAmount(new BigDecimal("0.01"));
        request.setLateFee(BigDecimal.ZERO);
        request.setDirection(2);
        request.setCode("BLACKHAT");

        controller().update(request);

        ArgumentCaptor<Bill> captor = ArgumentCaptor.forClass(Bill.class);
        verify(billMapper).updateById(captor.capture());
        Bill patch = captor.getValue();
        assertThat(patch.getId()).isEqualTo(5L);
        assertThat(patch.getRemark()).isEqualTo("补备注");
        assertThat(patch.getTenantRefId()).isEqualTo(8L);
        assertThat(patch.getFeeType()).isEqualTo("物业费");
        assertThat(patch.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        // 资金与身份字段全部为 null,MyBatis-Plus 非空更新策略下不会进 SET
        assertThat(patch.getPaidAmount()).isNull();
        assertThat(patch.getStatus()).isNull();
        assertThat(patch.getAmount()).isNull();
        assertThat(patch.getLateFee()).isNull();
        assertThat(patch.getDirection()).isNull();
        assertThat(patch.getCode()).isNull();
    }

    @Test
    @DisplayName("PUT 缺 id 直接拒绝")
    void updateRequiresId() {
        assertThatThrownBy(() -> controller().update(new Bill()))
                .isInstanceOf(BizException.class);
        verify(billMapper, never()).updateById(any(Bill.class));
    }

    // ---------- DELETE 守卫 ----------

    @Test
    @DisplayName("有收款记录的账单拒删")
    void deleteRefusedWhenPaymentsExist() {
        when(paymentMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> controller().delete(5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("收款");
        verify(billMapper, never()).delete(any());
    }

    @Test
    @DisplayName("已开票的账单拒删")
    void deleteRefusedWhenInvoiced() {
        when(paymentMapper.selectCount(any())).thenReturn(0L);
        when(invoiceMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> controller().delete(5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("发票");
        verify(billMapper, never()).delete(any());
    }

    @Test
    @DisplayName("条件删除:并发下刚落了实收(paid_amount>0)时删除不生效并报错")
    void deleteConditionalOnZeroPaid() {
        when(paymentMapper.selectCount(any())).thenReturn(0L);
        when(invoiceMapper.selectCount(any())).thenReturn(0L);
        when(billMapper.delete(any())).thenReturn(0);

        assertThatThrownBy(() -> controller().delete(5L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("干净账单正常删除;实收守卫必须在 DELETE 的 WHERE 里")
    void deleteCleanBill() {
        when(paymentMapper.selectCount(any())).thenReturn(0L);
        when(invoiceMapper.selectCount(any())).thenReturn(0L);
        when(billMapper.delete(any())).thenReturn(1);

        assertThat(controller().delete(5L).getCode()).isZero();
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Bill>> wrapper =
                ArgumentCaptor.forClass((Class) com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(billMapper).delete(wrapper.capture());
        assertThat(wrapper.getValue().getCustomSqlSegment()).contains("paid_amount");
    }

    @Test
    @DisplayName("查完发票到删单之间刚开出发票:删除后复查发现即回滚")
    void deleteRollsBackWhenInvoicedInWindow() {
        when(paymentMapper.selectCount(any())).thenReturn(0L);
        when(invoiceMapper.selectCount(any())).thenReturn(0L).thenReturn(1L);
        when(billMapper.delete(any())).thenReturn(1);

        assertThatThrownBy(() -> controller().delete(5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("开票");
    }
}
