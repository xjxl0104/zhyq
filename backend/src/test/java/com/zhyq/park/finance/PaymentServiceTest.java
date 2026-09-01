package com.zhyq.park.finance;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.entity.Payment;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.finance.mapper.FlowMapper;
import com.zhyq.park.finance.mapper.PaymentMapper;
import com.zhyq.park.finance.mapper.ReceiptMapper;
import com.zhyq.park.finance.service.PaymentService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 收款事务的幂等与滞纳金口径。
 *
 * <p>两条硬约束的回归锁:
 * ① 同 payNo 重放必须原样返回首次结果 —— 哪怕首次收款已经把账单收满结清。
 *    此前幂等键查找排在状态校验之后,超时重试撞上"当前账单状态不可收款",
 *    幂等键在唯一需要它的场景下失效。
 * ② 剩余可收 = 本金 + 滞纳金 - 实收。此前上限只看本金,滞纳金永久收不进来。</p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentMapper paymentMapper;
    @Mock private BillMapper billMapper;
    @Mock private FlowMapper flowMapper;
    @Mock private ReceiptMapper receiptMapper;

    @BeforeAll
    static void initMpLambdaCache() {
        // 渲染 wrapper SQL 片段做内容断言需要 Bill 的 lambda 缓存(纯单测无 MP 上下文)
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Bill.class);
    }

    private PaymentService service() {
        return new PaymentService(paymentMapper, billMapper, flowMapper, receiptMapper);
    }

    private static Bill bill(String amount, String paid, String lateFee, int status) {
        Bill b = new Bill();
        b.setId(1L);
        b.setTenantRefId(20L);
        b.setDirection(1);
        b.setStatus(status);
        b.setAmount(new BigDecimal(amount));
        b.setPaidAmount(new BigDecimal(paid));
        b.setLateFee(lateFee == null ? null : new BigDecimal(lateFee));
        return b;
    }

    @Test
    @DisplayName("同 payNo 重放:账单已结清也要返回原支付单,而不是报'状态不可收款'")
    void replaySamePayNoReturnsExistingEvenAfterSettle() {
        Payment existed = new Payment();
        existed.setPayNo("PN-1");
        when(paymentMapper.selectOne(any())).thenReturn(existed);

        Payment result = service().收款(1L, new BigDecimal("100"), "现金", "PN-1");

        assertThat(result).isSameAs(existed);
        // 幂等命中后不允许再碰账单与入账:不插支付单、不加实收、不写流水收据
        verify(paymentMapper, never()).insert(any(Payment.class));
        verify(billMapper, never()).update(any(), any());
        verify(flowMapper, never()).insert(any(com.zhyq.park.finance.entity.Flow.class));
        verify(receiptMapper, never()).insert(any(com.zhyq.park.finance.entity.Receipt.class));
    }

    @Test
    @DisplayName("剩余可收含滞纳金:本金已付清、只剩滞纳金时,这笔钱能收进来")
    void collectsLateFeeAfterPrincipalPaid() {
        when(paymentMapper.selectOne(any())).thenReturn(null);
        when(billMapper.selectById(1L)).thenReturn(bill("100.00", "100.00", "5.00", 4));
        when(paymentMapper.insert(any(Payment.class))).thenReturn(1);
        when(billMapper.update(any(), any())).thenReturn(1);

        Payment payment = service().收款(1L, new BigDecimal("5.00"), "现金", "PN-2");

        assertThat(payment.getAmount()).isEqualByComparingTo("5.00");
        verify(flowMapper).insert(any(com.zhyq.park.finance.entity.Flow.class));
        verify(receiptMapper).insert(any(com.zhyq.park.finance.entity.Receipt.class));

        // 锁死 SQL 侧口径:结清条件与防超收条件都必须带 late_fee,
        // 谁悄悄把 "+ late_fee" 删掉,这里先红
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaUpdateWrapper<Bill>> wrapper =
                ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
        verify(billMapper).update(isNull(), wrapper.capture());
        assertThat(wrapper.getValue().getSqlSet()).contains("amount + late_fee");
        assertThat(wrapper.getValue().getCustomSqlSegment()).contains("<= amount + late_fee");
    }

    @Test
    @DisplayName("payNo 是全局幂等键但不隐含账单:拿别的账单用过的 payNo 收当前账单要报错")
    void rejectsPayNoBoundToAnotherBill() {
        Payment other = new Payment();
        other.setPayNo("PN-X");
        other.setBillId(2L);
        when(paymentMapper.selectOne(any())).thenReturn(other);

        assertThatThrownBy(() -> service().收款(1L, new BigDecimal("10"), "现金", "PN-X"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("其它账单");
        verify(paymentMapper, never()).insert(any(Payment.class));
    }

    @Test
    @DisplayName("金额超过两位小数拒收:DECIMAL(14,2) 列会静默四舍五入,入账与请求对不上")
    void rejectsMoreThanTwoDecimals() {
        when(paymentMapper.selectOne(any())).thenReturn(null);
        when(billMapper.selectById(1L)).thenReturn(bill("100.00", "0", null, 3));

        assertThatThrownBy(() -> service().收款(1L, new BigDecimal("10.005"), "现金", "PN-7"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("两位小数");
        verify(paymentMapper, never()).insert(any(Payment.class));
    }

    @Test
    @DisplayName("超出 本金+滞纳金-实收 的收款被拒绝")
    void rejectsAmountBeyondPrincipalPlusLateFee() {
        when(paymentMapper.selectOne(any())).thenReturn(null);
        when(billMapper.selectById(1L)).thenReturn(bill("100.00", "0", "5.00", 3));

        assertThatThrownBy(() -> service().收款(1L, new BigDecimal("105.01"), "现金", "PN-3"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("超过剩余应收");
        verify(paymentMapper, never()).insert(any(Payment.class));
    }

    @Test
    @DisplayName("并发撞唯一键:回查返回已入账的支付单,保持幂等")
    void duplicateKeyRaceFallsBackToExisting() {
        Payment landed = new Payment();
        landed.setPayNo("PN-4");
        when(paymentMapper.selectOne(any())).thenReturn(null).thenReturn(landed);
        when(billMapper.selectById(1L)).thenReturn(bill("100.00", "0", null, 3));
        when(paymentMapper.insert(any(Payment.class)))
                .thenThrow(new DuplicateKeyException("uk_pay_no"));

        Payment result = service().收款(1L, new BigDecimal("50"), "现金", "PN-4");

        assertThat(result).isSameAs(landed);
        verify(billMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("金额必须大于 0;不可收款状态照旧拒绝(新收款,非重放)")
    void keepsBasicValidations() {
        when(paymentMapper.selectOne(any())).thenReturn(null);
        when(billMapper.selectById(1L)).thenReturn(bill("100.00", "0", null, 5));

        assertThatThrownBy(() -> service().收款(1L, new BigDecimal("10"), "现金", "PN-5"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不可收款");
        assertThatThrownBy(() -> service().收款(1L, BigDecimal.ZERO, "现金", "PN-6"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("大于0");
    }
}
