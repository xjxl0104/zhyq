package com.zhyq.park.finance;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.finance.controller.BillController;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.entity.Invoice;
import com.zhyq.park.finance.entity.Payment;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.finance.mapper.InvoiceMapper;
import com.zhyq.park.finance.mapper.PaymentMapper;
import com.zhyq.park.finance.service.FinanceViewEnricher;
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
import java.util.Map;

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
    @Mock private LateFeeService lateFeeService;

    @BeforeAll
    static void initMpLambdaCache() {
        // 纯单测没有 Spring/MyBatis 上下文;LambdaUpdateWrapper.set() 解析列名是即时的
        // (eq/in 是惰性的),需要手工装载 Bill 的 TableInfo/lambda 缓存
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Bill.class);
        TableInfoHelper.initTableInfo(assistant, Payment.class);
        TableInfoHelper.initTableInfo(assistant, Invoice.class);
    }

    private BillController controller() {
        return new BillController(billMapper, viewEnricher, paymentMapper, invoiceMapper, lateFeeService);
    }

    // ---------- calcLateFee ----------
    // 计算逻辑(条件更新语义)已抽到 LateFeeService,由 LateFeeServiceTest 锁住;
    // 控制器只剩委托,手动端点与每日自愈任务走同一份实现

    @Test
    @DisplayName("calcLateFee 委托 LateFeeService,不再自持计算逻辑")
    void calcLateFeeDelegates() {
        when(lateFeeService.recalc()).thenReturn(5);

        assertThat(controller().calcLateFee().getData()).isEqualTo(5);
        verify(lateFeeService).recalc();
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

    // ---------- 费用类型筛选 ----------

    @Test
    @DisplayName("费用类型「保证金」为大类:按后缀命中租金保证金/物业保证金;精确类型仍走等值")
    void feeTypeDepositCategoryMatchesBySuffix() {
        when(billMapper.selectPage(any(), any()))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());

        controller().page(1, 10, null, null, null, null, null, "保证金", null, null, null);
        controller().page(1, 10, null, null, null, null, null, "租金", null, null, null);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Bill>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(
                        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(billMapper, org.mockito.Mockito.times(2)).selectPage(any(), captor.capture());
        String depositSql = captor.getAllValues().get(0).getSqlSegment();
        String rentSql = captor.getAllValues().get(1).getSqlSegment();
        assertThat(depositSql).contains("LIKE");
        assertThat(rentSql).doesNotContain("LIKE");
        assertThat(rentSql).contains("fee_type =");
    }

    // ---------- 重置(批量作废登记表账单) ----------

    @Test
    @DisplayName("重置只作废登记表来源账单,有收款/发票关联的排除在删除范围外")
    void resetExcludesPaidAndInvoicedBills() {
        Payment payment = new Payment();
        payment.setBillId(11L);
        Invoice invoice = new Invoice();
        invoice.setBillId(12L);
        when(paymentMapper.selectList(any())).thenReturn(List.of(payment));
        when(invoiceMapper.selectList(any())).thenReturn(List.of(invoice));
        when(billMapper.delete(any())).thenReturn(340);
        when(billMapper.selectCount(any())).thenReturn(2L);

        Map<String, Object> data = controller().reset().getData();

        assertThat(data.get("deleted")).isEqualTo(340);
        assertThat(data.get("kept")).isEqualTo(2L);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Bill>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(
                        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(billMapper).delete(captor.capture());
        // 有收款/发票关联的账单 id 必须进 NOT IN 排除名单;来源与零实收进 WHERE
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("NOT IN");
        assertThat(sql).contains("source");
        assertThat(sql).contains("paid_amount");
    }
}
