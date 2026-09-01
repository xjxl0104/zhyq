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

    /**
     * 可作为权威租户名来源的登记表状态。
     *
     * <p>草稿(DRAFT)与待核对(PENDING_REVIEW)里的名字还没校对过,拿它覆盖租客档案反而更不准。</p>
     */
    private static final List<String> AUTHORITATIVE_STATUS = List.of("CONFIRMED", "ACTIVE");

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
        List<Long> tenantRefIds = bills.stream().map(Bill::getTenantRefId).toList();
        Map<Long, ReceivableRegister> byRegisterId = loadRegisters(
                bills.stream().map(Bill::getReceivableRegisterId).toList());
        Map<Long, ReceivableRegister> byTenantRefId = loadRegistersByTenant(tenantRefIds);
        Map<Long, BizTenant> tenants = loadTenants(tenantRefIds);
        for (Bill bill : bills) {
            // 优先用账单直接挂着的那张登记表(它同时给出协议编号);
            // 挂不上的(合同计划生成的、历史遗留的账单)退而按租客反查登记表 —— 登记表是
            // 租户名的权威来源,同一个租客不该在登记表叫一个名、在账单页叫另一个名。
            ReceivableRegister linked = byRegisterId.get(bill.getReceivableRegisterId());
            ReceivableRegister authoritative = linked != null
                    ? linked : byTenantRefId.get(bill.getTenantRefId());
            bill.setTenantName(tenantNameOf(authoritative, tenants.get(bill.getTenantRefId())));
            // 协议编号只认直接挂着的那张:按租客猜出来的登记表未必对应这张账单的那份协议
            bill.setAgreementNo(linked == null ? null : linked.getAgreementNoRaw());
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

    /**
     * 按租客 id 反查登记表,给"没挂登记表的账单"提供权威租户名。
     *
     * <p>同一个租客可能有多份登记明细(续签、多个铺位)。这里只用它拿名字,取最新一份即可 ——
     * 名字在多份之间通常一致;真要拿协议编号必须用账单直接挂着的那份,不能靠这个猜。</p>
     *
     * <p>只取已确认/已生效的:草稿和待核对的登记表里名字可能还没校对过,
     * 拿它覆盖租客档案反而更不准。</p>
     */
    private Map<Long, ReceivableRegister> loadRegistersByTenant(Collection<Long> rawIds) {
        List<Long> ids = rawIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ReceivableRegister> rows = receivableRegisterMapper.selectList(
                new LambdaQueryWrapper<ReceivableRegister>()
                        .in(ReceivableRegister::getTenantRefId, ids)
                        .in(ReceivableRegister::getStatus, AUTHORITATIVE_STATUS)
                        .orderByAsc(ReceivableRegister::getId));
        // 同一租客多行时后写覆盖前写 → 留下 id 最大的那份(最新)
        Map<Long, ReceivableRegister> out = new java.util.HashMap<>();
        for (ReceivableRegister r : rows) {
            out.put(r.getTenantRefId(), r);
        }
        return out;
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
