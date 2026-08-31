package com.zhyq.park.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.mapper.ReceivableRegisterMapper;
import com.zhyq.park.tenant.entity.BizTenant;
import com.zhyq.park.tenant.mapper.BizTenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 财务各页面的展示口径单一真相源。
 *
 * <p>账单、逾期、流水、收据、发票、收款通知六个页面此前各显示各的:所有账单页刚补上了
 * 租客名,逾期页还在显示裸的 {@code tenantRefId}(界面上就是一个"2"),流水/发票/收款通知
 * 干脆只有"关联账单ID",收据连账单号都没有 —— 页面之间看不出任何关联,对不上账。</p>
 *
 * <p>租客名的口径固定为:<b>优先取应收登记明细里的租户名</b>({@code tenantNameRaw}),
 * 登记表取不到才回落到租客档案 {@code biz_tenant.name}。登记明细是财务的权威来源,
 * 账单金额也生成自它, 展示口径必须跟它一致, 否则同一个租客在登记表叫一个名、
 * 在账单页叫另一个名。</p>
 */
@Service
@RequiredArgsConstructor
public class FinanceViewEnricher {

    private final BillMapper billMapper;
    private final ReceivableRegisterMapper receivableRegisterMapper;
    private final BizTenantMapper bizTenantMapper;

    /**
     * 一张账单在各下游页面(流水/收据/发票/通知)上需要展示的信息。
     *
     * <p>下游记录只存了 {@code bill_id},光有 id 用户什么也看不出来。这里把
     * "这笔钱是谁的、对应哪张账单、什么费用"一次带出去。</p>
     */
    public record BillView(Long billId, String billCode, String feeType, String tenantName,
                           String agreementNo, BigDecimal amount, LocalDate dueDate, Integer status) {}

    /**
     * 给一批账单填上租客名与协议编号。
     *
     * @param bills 直接就地写入,方法不返回新集合
     */
    public void enrichBills(List<Bill> bills) {
        if (bills == null || bills.isEmpty()) {
            return;
        }
        Map<Long, ReceivableRegister> registers = loadRegisters(
                bills.stream().map(Bill::getReceivableRegisterId).toList());
        Map<Long, BizTenant> tenants = loadTenants(
                bills.stream().map(Bill::getTenantRefId).toList());
        for (Bill bill : bills) {
            ReceivableRegister register = registers.get(bill.getReceivableRegisterId());
            bill.setTenantName(tenantNameOf(register, tenants.get(bill.getTenantRefId())));
            bill.setAgreementNo(register == null ? null : register.getAgreementNoRaw());
        }
    }

    /**
     * 按账单 id 批量查出展示信息。供流水/收据/发票/收款通知四个页面共用。
     *
     * @return billId → 展示信息;传入的 id 查不到账单时该 key 不出现
     */
    public Map<Long, BillView> resolveBillViews(Collection<Long> billIds) {
        List<Long> ids = billIds == null ? List.of() : billIds.stream()
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Bill> bills = billMapper.selectList(
                new LambdaQueryWrapper<Bill>().in(Bill::getId, ids));
        if (bills.isEmpty()) {
            return Collections.emptyMap();
        }
        enrichBills(bills);
        return bills.stream().collect(Collectors.toMap(Bill::getId,
                b -> new BillView(b.getId(), b.getCode(), b.getFeeType(), b.getTenantName(),
                        b.getAgreementNo(), b.getAmount(), b.getDueDate(), b.getStatus())));
    }

    /**
     * 租客名口径:登记明细优先,租客档案兜底。
     *
     * <p>两者都取不到时返回 null,前端显示"-" —— 显示一个裸 id 比显示"-"更糟,
     * 用户既看不懂也没法据此找人。</p>
     */
    private static String tenantNameOf(ReceivableRegister register, BizTenant tenant) {
        if (register != null && StringUtils.hasText(register.getTenantNameRaw())) {
            return register.getTenantNameRaw();
        }
        return tenant == null ? null : tenant.getName();
    }

    private Map<Long, ReceivableRegister> loadRegisters(Collection<Long> rawIds) {
        List<Long> ids = rawIds.stream().filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Collections.emptyMap()
                : receivableRegisterMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ReceivableRegister::getId, Function.identity()));
    }

    private Map<Long, BizTenant> loadTenants(Collection<Long> rawIds) {
        List<Long> ids = rawIds.stream().filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Collections.emptyMap()
                : bizTenantMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(BizTenant::getId, Function.identity()));
    }
}
