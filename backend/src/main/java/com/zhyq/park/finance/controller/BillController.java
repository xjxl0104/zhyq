package com.zhyq.park.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.finance.service.BillMetrics;
import com.zhyq.park.finance.service.FinanceViewEnricher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "财务-账单")
@RestController
@RequestMapping("/finance/bill")
@RequiredArgsConstructor
public class BillController {

    /** 可收款的账单状态:待收付(3)/部分结清(4)/逾期(6),与 PaymentService 的收款校验同口径 */
    private static final List<Integer> PAYABLE_STATUS = List.of(3, 4, 6);

    private final BillMapper billMapper;
    private final FinanceViewEnricher viewEnricher;

    @Operation(summary = "分页查询账单")
    @PreAuthorize("hasAuthority('finance:bill:query')")
    @GetMapping("/page")
    public Result<PageResult<Bill>> page(@RequestParam(defaultValue = "1") int pageNo,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) Long contractId,
                                         @RequestParam(required = false) Long tenantRefId,
                                         @RequestParam(required = false) Integer direction,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String feeType,
                                         // 来源:'应收登记表' 的账单才带协议编号与登记明细口径的租客名,
                                         // 与历史/演示账单(合同计划等)区分开
                                         @RequestParam(required = false) String source,
                                         // 从流水/收据/发票/收款通知点「关联账单」跳过来时按 id 定位那一张
                                         @RequestParam(required = false) Long billId,
                                         @RequestParam(required = false) Boolean onlyDue) {
        LambdaQueryWrapper<Bill> qw = new LambdaQueryWrapper<>();
        qw.eq(billId != null, Bill::getId, billId)
          .like(StringUtils.hasText(code), Bill::getCode, code)
          .eq(contractId != null, Bill::getContractId, contractId)
          .eq(tenantRefId != null, Bill::getTenantRefId, tenantRefId)
          .eq(direction != null, Bill::getDirection, direction)
          .eq(status != null, Bill::getStatus, status)
          .eq(StringUtils.hasText(feeType), Bill::getFeeType, feeType)
          .eq(StringUtils.hasText(source), Bill::getSource, source)
          // 隐藏未到期:仅显示应收日<=今天的账单
          .le(Boolean.TRUE.equals(onlyDue), Bill::getDueDate, LocalDate.now())
          .orderByDesc(Bill::getId);
        IPage<Bill> p = billMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        enrich(p.getRecords());
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "账单统计(顶部卡片)")
    @PreAuthorize("hasAuthority('finance:bill:query')")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        List<Bill> all = billMapper.selectList(new LambdaQueryWrapper<>());
        BigDecimal receivable = BigDecimal.ZERO;  // 应收:direction=1 的 amount 合计
        BigDecimal received = BigDecimal.ZERO;     // 实收:paid_amount 合计
        BigDecimal lateFee = BigDecimal.ZERO;      // 滞纳金合计
        long overdueCount = 0;                     // 逾期账单数(status=6)
        for (Bill b : all) {
            // 口径统一走 BillMetrics:实收此前漏了收款方向的过滤,
            // 把应付账单已付出去的钱也算进了"实收",与财务报表同款问题
            receivable = receivable.add(BillMetrics.receivableOf(b));
            received = received.add(BillMetrics.receivedOf(b));
            lateFee = lateFee.add(nz(b.getLateFee()));
            if (b.getStatus() != null && b.getStatus() == BillMetrics.STATUS_OVERDUE) {
                overdueCount++;
            }
        }
        Map<String, Object> m = new HashMap<>();
        m.put("receivable", receivable);
        m.put("received", received);
        m.put("needReceive", receivable.subtract(received));
        m.put("lateFee", lateFee);
        m.put("overdueCount", overdueCount);
        return Result.ok(m);
    }

    @Operation(summary = "逾期账单分页(应收方向:status=6 或 应收日<今天且未结清)")
    @PreAuthorize("hasAuthority('finance:bill:query')")
    @GetMapping("/overdue")
    public Result<PageResult<Bill>> overdue(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize) {
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<Bill> qw = new LambdaQueryWrapper<>();
        // direction=1 AND ( status=6  OR  (due_date<today AND status in (3,4)) )
        qw.eq(Bill::getDirection, 1)
          .and(w -> w.eq(Bill::getStatus, 6)
                     .or(o -> o.lt(Bill::getDueDate, today).in(Bill::getStatus, 3, 4)))
          .orderByDesc(Bill::getDueDate);
        IPage<Bill> p = billMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        enrich(p.getRecords());
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /**
     * 收银台的租客下拉。
     *
     * <p>只列还欠着钱的租客,并带上欠款笔数与合计。收银台原先拉的是全部租客档案
     * ({@code /tenant/info/list}),里面绝大多数没有任何未结账单 —— 收银员得挨个点开试,
     * 试到有账单的那个为止。而且档案里的名字与登记明细可能不是一个口径。</p>
     */
    @Operation(summary = "收银台:有未结账单的租客(含欠款汇总)")
    @PreAuthorize("hasAuthority('finance:bill:query')")
    @GetMapping("/payable-tenants")
    public Result<List<Map<String, Object>>> payableTenants(@RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<Bill> qw = new LambdaQueryWrapper<Bill>()
                .eq(Bill::getDirection, 1)
                .in(Bill::getStatus, PAYABLE_STATUS)
                .eq(projectId != null, Bill::getProjectId, projectId)
                .isNotNull(Bill::getTenantRefId);
        List<Bill> bills = billMapper.selectList(qw);
        // 只留真正还有欠款的(状态是待收付但已被收满的边角数据不该出现在收银台)
        List<Bill> outstanding = bills.stream()
                .filter(b -> BillMetrics.outstandingOf(b).signum() > 0)
                .toList();
        viewEnricher.enrichBills(outstanding);

        Map<Long, List<Bill>> byTenant = outstanding.stream()
                .collect(Collectors.groupingBy(Bill::getTenantRefId, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> out = new ArrayList<>();
        byTenant.forEach((tenantRefId, rows) -> {
            BigDecimal owe = rows.stream()
                    .map(BillMetrics::outstandingOf)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tenantRefId", tenantRefId);
            item.put("tenantName", rows.stream().map(Bill::getTenantName)
                    .filter(StringUtils::hasText).findFirst().orElse("租客 #" + tenantRefId));
            item.put("billCount", rows.size());
            item.put("owe", owe);
            out.add(item);
        });
        // 欠得多的排前面,收银员通常先处理大额
        out.sort((a, b) -> ((BigDecimal) b.get("owe")).compareTo((BigDecimal) a.get("owe")));
        return Result.ok(out);
    }

    @Operation(summary = "计算滞纳金(遍历逾期未结清的应收账单)")
    @PreAuthorize("hasAuthority('finance:bill:calcLateFee')")
    @PostMapping("/calcLateFee")
    public Result<Integer> calcLateFee() {
        LocalDate today = LocalDate.now();
        // 仅应收方向(direction=1)、可催缴状态(待收付3/部分结清4/已逾期6)、应收日<今天
        LambdaQueryWrapper<Bill> qw = new LambdaQueryWrapper<>();
        qw.eq(Bill::getDirection, 1)
          .in(Bill::getStatus, 3, 4, 6)
          .lt(Bill::getDueDate, today);
        List<Bill> list = billMapper.selectList(qw);
        // 万分之五/天,基数为剩余欠款(应收-实收),每次全量重算,重复调用幂等
        BigDecimal rate = new BigDecimal("0.0005");
        int count = 0;
        for (Bill b : list) {
            if (b.getDueDate() == null) {
                continue;
            }
            long days = today.toEpochDay() - b.getDueDate().toEpochDay();
            if (days <= 0) {
                continue;
            }
            BigDecimal outstanding = nz(b.getAmount()).subtract(nz(b.getPaidAmount()));
            if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal lateFee = outstanding
                    .multiply(rate)
                    .multiply(BigDecimal.valueOf(days))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            Bill update = new Bill();
            update.setId(b.getId());
            update.setOverdueDays((int) days);
            update.setLateFee(lateFee);
            update.setStatus(6);
            billMapper.updateById(update);
            count++;
        }
        return Result.ok(count);
    }

    @Operation(summary = "账单详情")
    @PreAuthorize("hasAuthority('finance:bill:query')")
    @GetMapping("/{id}")
    public Result<Bill> get(@PathVariable Long id) {
        Bill bill = billMapper.selectById(id);
        enrich(bill == null ? List.of() : List.of(bill));
        return Result.ok(bill);
    }

    @Operation(summary = "新增账单")
    @PreAuthorize("hasAuthority('finance:bill:add')")
    @PostMapping
    public Result<Long> add(@RequestBody Bill bill) {
        billMapper.insert(bill);
        return Result.ok(bill.getId());
    }

    @Operation(summary = "修改账单")
    @PreAuthorize("hasAuthority('finance:bill:edit')")
    @PutMapping
    public Result<Void> update(@RequestBody Bill bill) {
        billMapper.updateById(bill);
        return Result.ok();
    }

    @Operation(summary = "删除账单")
    @PreAuthorize("hasAuthority('finance:bill:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        billMapper.deleteById(id);
        return Result.ok();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 填租客名与协议编号。实现搬到了 {@link FinanceViewEnricher} —— 流水/收据/发票/收款通知
     * 也要按同一口径展示,口径留在这个控制器里private着,别的页面就只能各写各的
     * (它们此前就是这么显示裸 bill_id 的)。
     */
    private void enrich(List<Bill> bills) {
        viewEnricher.enrichBills(bills);
    }
}
