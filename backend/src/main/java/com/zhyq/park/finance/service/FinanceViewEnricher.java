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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
     * 收银台租客下拉的一个选项。
     *
     * <p>档案里的<b>每个租客都可选</b>:当月有应收、有欠款的租客,以前只要口径没对上
     * (账单状态不在可收款集合、tenantRefId 挂空等)就整个从下拉里消失,收银员无从下手。
     * 现在名册来自租客档案,欠款只是附加汇总 —— 没欠款的租客同样能选进去看账。</p>
     */
    public record TenantOption(Long tenantRefId, String tenantName, int billCount, BigDecimal owe) {}

    /**
     * 收银台租客选项:全部档案租客 + 欠款汇总,欠款多的排前面,没欠款的按名字排在后面。
     *
     * <p>名字口径与账单页同源:该租客的欠款账单带出的登记明细名优先,档案名兜底 ——
     * 租客 id 从此总是跟着一个人能读懂的名字,而不是界面上一个裸数字。</p>
     *
     * <p>账单里出现了档案中不存在的 tenantRefId(历史脏数据)时,该 id 也会以
     * 「租客 #id」兜底名出现在选项里 —— 账单必须有收款入口,不能因为档案缺失而失联。</p>
     *
     * @param projectId 只收窄<b>租客名册</b>(档案里的 project_id)。欠款账单不按项目过滤:
     *                  登记表生成的账单目前不带 project_id,按项目过滤会把它们的欠款漏成 0
     * @param bills     候选账单(调用方按可收款状态查出;这里再按"应收方向且有欠款"过滤)
     */
    public List<TenantOption> cashierTenantOptions(Long projectId, List<Bill> bills) {
        List<Bill> outstanding = bills == null ? List.of() : bills.stream()
                .filter(b -> b != null && b.getTenantRefId() != null)
                .filter(b -> BillMetrics.outstandingOf(b).signum() > 0)
                .toList();
        enrichBills(outstanding);
        Map<Long, List<Bill>> byTenant = outstanding.stream()
                .collect(Collectors.groupingBy(Bill::getTenantRefId, LinkedHashMap::new, Collectors.toList()));

        List<BizTenant> roster = bizTenantMapper.selectList(new LambdaQueryWrapper<BizTenant>()
                .eq(projectId != null, BizTenant::getProjectId, projectId)
                .orderByAsc(BizTenant::getId));

        List<TenantOption> options = new ArrayList<>();
        for (BizTenant tenant : roster) {
            options.add(toOption(tenant.getId(), tenant.getName(), byTenant.get(tenant.getId())));
        }
        // 档案里没有的 tenantRefId(欠款账单挂着的孤儿 id)也要有入口
        Set<Long> rosterIds = roster.stream().map(BizTenant::getId).collect(Collectors.toSet());
        byTenant.forEach((tenantRefId, rows) -> {
            if (!rosterIds.contains(tenantRefId)) {
                options.add(toOption(tenantRefId, null, rows));
            }
        });

        options.sort(Comparator.comparing(TenantOption::owe, Comparator.reverseOrder())
                .thenComparing(o -> o.tenantName() == null ? "" : o.tenantName()));
        return options;
    }

    /**
     * 批量取租客档案名(biz_tenant.name),空白名不算名字。
     *
     * <p>给退房报表这类手里只有 tenantRefId、又没有账单可借登记明细名的页面兜底用。</p>
     */
    public Map<Long, String> tenantNamesOf(Collection<Long> tenantRefIds) {
        Map<Long, BizTenant> tenants = loadTenants(tenantRefIds == null ? List.of() : tenantRefIds);
        Map<Long, String> names = new LinkedHashMap<>();
        tenants.forEach((id, tenant) -> {
            if (StringUtils.hasText(tenant.getName())) {
                names.put(id, tenant.getName());
            }
        });
        return names;
    }

    private TenantOption toOption(Long tenantRefId, String profileName, List<Bill> rows) {
        BigDecimal owe = rows == null ? BigDecimal.ZERO : rows.stream()
                .map(BillMetrics::outstandingOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String name = rows == null ? null : rows.stream()
                .map(Bill::getTenantName)
                .filter(StringUtils::hasText)
                .findFirst().orElse(null);
        if (name == null) {
            name = StringUtils.hasText(profileName) ? profileName : "租客 #" + tenantRefId;
        }
        return new TenantOption(tenantRefId, name, rows == null ? 0 : rows.size(), owe);
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
