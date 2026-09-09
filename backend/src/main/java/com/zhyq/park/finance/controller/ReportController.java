package com.zhyq.park.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.finance.service.BillMetrics;
import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.mapper.ReceivableRegisterMapper;
import com.zhyq.park.tenant.entity.BizTenant;
import com.zhyq.park.tenant.mapper.BizTenantMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "财务-报表")
@RestController
@RequestMapping("/finance/report")
@RequiredArgsConstructor
public class ReportController {

    private final BillMapper billMapper;
    private final ReceivableRegisterMapper registerMapper;
    private final BizTenantMapper tenantMapper;

    @Operation(summary = "财务汇总报表(经营提示/收缴率/应收结构/账龄分布);可按账期年月过滤")
    @PreAuthorize("hasAuthority('finance:report:query')")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        List<Bill> all = billMapper.selectList(new LambdaQueryWrapper<>());

        // 租户名:应收登记表口径优先(与账单页同源),合同直推账单回落 biz_tenant
        Map<Long, String> registerTenant = registerMapper.selectList(new LambdaQueryWrapper<>())
                .stream().filter(r -> r.getTenantNameRaw() != null && !r.getTenantNameRaw().isBlank())
                .collect(Collectors.toMap(ReceivableRegister::getId,
                        ReceivableRegister::getTenantNameRaw, (a, b) -> a));
        Map<Long, String> bizTenant = tenantMapper.selectList(new LambdaQueryWrapper<>())
                .stream().filter(t -> t.getName() != null && !t.getName().isBlank())
                .collect(Collectors.toMap(BizTenant::getId, BizTenant::getName, (a, b) -> a));

        LocalDate today = LocalDate.now();

        BigDecimal receivable = BigDecimal.ZERO;   // 应收(按月,含滞纳金)
        BigDecimal received = BigDecimal.ZERO;     // 实收(按月)

        // 应收结构(按月):租户 → 费用类型 → 应收金额
        Map<String, Map<String, BigDecimal>> structure = new LinkedHashMap<>();

        // 账龄(按月,未结清欠款)
        BigDecimal agingNotOverdue = BigDecimal.ZERO;
        BigDecimal aging30 = BigDecimal.ZERO;
        BigDecimal aging30to90 = BigDecimal.ZERO;
        BigDecimal aging90 = BigDecimal.ZERO;

        // 经营提示:全局累计欠款(应收-实收>0),不受年月过滤——保证金是一次性、账期落在
        // 签约月,按月看多数月份为 0,但欠款一直存在,故这三个数按全部账单累计
        BigDecimal operatingOutstanding = BigDecimal.ZERO; // 经营(租金+物业)未收
        BigDecimal depositOutstanding = BigDecimal.ZERO;   // 保证金未收
        Map<String, BigDecimal> dunning = new LinkedHashMap<>();

        for (Bill b : all) {
            boolean isReceivable = BillMetrics.isReceivable(b);
            String feeType = b.getFeeType() == null ? "其它" : b.getFeeType();
            String tenant = tenantName(b, registerTenant, bizTenant);

            // ① 全局累计欠款(不分月):经营提示三个数都基于「应收 - 实收 > 0」
            if (isReceivable) {
                BigDecimal owe = BillMetrics.outstandingOf(b);
                if (owe.compareTo(BigDecimal.ZERO) > 0) {
                    if (isDeposit(feeType)) {
                        depositOutstanding = depositOutstanding.add(owe);
                    } else if (isOperating(feeType)) {
                        operatingOutstanding = operatingOutstanding.add(owe);
                    }
                    dunning.merge(tenant, owe, BigDecimal::add);
                }
            }

            // ② 以下按年月过滤:收缴情况/应收结构/账龄只统计选定账期
            if (year != null) {
                LocalDate ps = b.getPeriodStart();
                if (ps == null || ps.getYear() != year) continue;
                if (month != null && ps.getMonthValue() != month) continue;
            }

            // 口径统一走 BillMetrics,与账单页顶部卡片同源
            received = received.add(BillMetrics.receivedOf(b));
            if (!isReceivable) continue;
            BigDecimal amount = BillMetrics.receivableOf(b);
            receivable = receivable.add(amount);
            structure.computeIfAbsent(tenant, k -> new LinkedHashMap<>())
                    .merge(feeType, amount, BigDecimal::add);

            BigDecimal owePeriod = BillMetrics.outstandingOf(b);
            if (owePeriod.compareTo(BigDecimal.ZERO) > 0) {
                long overdueDays = b.getDueDate() == null ? 0
                        : today.toEpochDay() - b.getDueDate().toEpochDay();
                if (overdueDays <= 0) {
                    agingNotOverdue = agingNotOverdue.add(owePeriod);
                } else if (overdueDays <= 30) {
                    aging30 = aging30.add(owePeriod);
                } else if (overdueDays <= 90) {
                    aging30to90 = aging30to90.add(owePeriod);
                } else {
                    aging90 = aging90.add(owePeriod);
                }
            }
        }

        BigDecimal collectRate = receivable.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : received.divide(receivable, 4, RoundingMode.HALF_UP);

        // 应收结构展开为 {租户,费用类型,应收金额},按租户应收合计降序、费用类型固定次序
        List<Map<String, Object>> list = new ArrayList<>();
        structure.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Map<String, BigDecimal>>>comparingDouble(
                        e -> tenantTotal(e.getValue()).doubleValue()).reversed())
                .forEach(tenantEntry -> tenantEntry.getValue().entrySet().stream()
                        // 免租期账单应收为 0,不进结构表,免得整屏 ¥0 噪声淹没真实应收
                        .filter(fe -> fe.getValue().compareTo(BigDecimal.ZERO) > 0)
                        .sorted(Comparator.comparingInt(fe -> feeOrder(fe.getKey())))
                        .forEach(fe -> {
                            Map<String, Object> item = new HashMap<>();
                            item.put("tenant", tenantEntry.getKey());
                            item.put("feeType", fe.getKey());
                            item.put("amount", fe.getValue());
                            list.add(item);
                        }));

        // 需催收用户:按欠款降序
        List<Map<String, Object>> dunningList = dunning.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("tenant", e.getKey());
                    item.put("amount", e.getValue());
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> aging = new LinkedHashMap<>();
        aging.put("notOverdue", agingNotOverdue);
        aging.put("within30", aging30);
        aging.put("days30to90", aging30to90);
        aging.put("over90", aging90);

        Map<String, Object> m = new HashMap<>();
        m.put("receivable", receivable);
        m.put("received", received);
        m.put("collectRate", collectRate);
        m.put("operating", operatingOutstanding);
        m.put("deposit", depositOutstanding);
        m.put("list", list);
        m.put("dunning", dunningList);
        m.put("aging", aging);
        return Result.ok(m);
    }

    private static String tenantName(Bill b, Map<Long, String> registerTenant,
                                     Map<Long, String> bizTenant) {
        if (b.getReceivableRegisterId() != null) {
            String n = registerTenant.get(b.getReceivableRegisterId());
            if (n != null && !n.isBlank()) return n;
        }
        if (b.getTenantRefId() != null) {
            String n = bizTenant.get(b.getTenantRefId());
            if (n != null && !n.isBlank()) return n;
        }
        return "其它";
    }

    /** 保证金类:含「保证金」字样(租金保证金/物业保证金) */
    private static boolean isDeposit(String feeType) {
        return feeType != null && feeType.contains("保证金");
    }

    /** 经营性:非保证金且属租金或物业(租金/物业费/物业管理费) */
    private static boolean isOperating(String feeType) {
        return feeType != null && !isDeposit(feeType)
                && (feeType.contains("租金") || feeType.contains("物业"));
    }

    private static int feeOrder(String feeType) {
        if (feeType == null) return 99;
        return switch (feeType) {
            case "租金" -> 0;
            case "物业费" -> 1;
            case "租金保证金" -> 2;
            case "物业保证金" -> 3;
            default -> 50;
        };
    }

    private static BigDecimal tenantTotal(Map<String, BigDecimal> feeMap) {
        return feeMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
