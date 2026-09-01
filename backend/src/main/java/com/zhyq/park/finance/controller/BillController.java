package com.zhyq.park.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.entity.Invoice;
import com.zhyq.park.finance.entity.Payment;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.finance.mapper.InvoiceMapper;
import com.zhyq.park.finance.mapper.PaymentMapper;
import com.zhyq.park.finance.service.BillMetrics;
import com.zhyq.park.finance.service.FinanceViewEnricher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "财务-账单")
@RestController
@RequestMapping("/finance/bill")
@RequiredArgsConstructor
public class BillController {

    /** 可收款的账单状态:待收付(3)/部分结清(4)/逾期(6),与 PaymentService 的收款校验同口径 */
    private static final List<Integer> PAYABLE_STATUS = List.of(3, 4, 6);

    private final BillMapper billMapper;
    private final FinanceViewEnricher viewEnricher;
    private final PaymentMapper paymentMapper;
    private final InvoiceMapper invoiceMapper;

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
     * 收银台的租客下拉:档案里的<b>每个租客都能选</b>,欠款的排前面并带欠款汇总。
     *
     * <p>此前只列"还欠着钱的租客" —— 当月有应收/有欠款的租客一旦口径没对上
     * (账单挂了档案外的 tenantRefId 等)就整个从下拉里消失,收银员无从下手。
     * 名册与欠款的拼装口径统一在 {@link FinanceViewEnricher#cashierTenantOptions}。</p>
     */
    @Operation(summary = "收银台:全部租客(欠款的在前,带欠款笔数与合计)")
    @PreAuthorize("hasAuthority('finance:bill:query')")
    @GetMapping("/payable-tenants")
    public Result<List<FinanceViewEnricher.TenantOption>> payableTenants(
            @RequestParam(required = false) Long projectId) {
        // 欠款账单不按 projectId 过滤:登记表生成的账单目前不带 project_id,
        // 按项目过滤会把它们的欠款漏成 0。projectId 只用来收窄租客名册
        List<Bill> bills = billMapper.selectList(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getDirection, 1)
                .in(Bill::getStatus, PAYABLE_STATUS)
                .isNotNull(Bill::getTenantRefId));
        return Result.ok(viewEnricher.cashierTenantOptions(projectId, bills));
    }

    @Operation(summary = "计算滞纳金(遍历逾期未结清的应收账单)")
    @PreAuthorize("hasAuthority('finance:bill:calcLateFee')")
    @PostMapping("/calcLateFee")
    public Result<Integer> calcLateFee() {
        LocalDate today = LocalDate.now();
        // 仅应收方向(direction=1)、可催缴状态(待收付3/部分结清4/已逾期6)、应收日<今天。
        // 逐行独立条件更新、不整批包事务:每行都是幂等重算,整批事务只会把行锁攥到循环
        // 结束(卡住并发收款),两次并发触发还可能因扫描顺序不同互相死锁;按 id 排序让
        // 加锁顺序确定
        LambdaQueryWrapper<Bill> qw = new LambdaQueryWrapper<>();
        qw.eq(Bill::getDirection, 1)
          .in(Bill::getStatus, 3, 4, 6)
          .lt(Bill::getDueDate, today)
          .orderByAsc(Bill::getId);
        List<Bill> list = billMapper.selectList(qw);
        // 万分之五/天,基数为剩余本金欠款(amount - paid),每次全量重算,重复调用幂等
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
            // 条件更新:上面的 selectList 是旧读,这中间账单可能刚被收满(status→5)。
            // updateById 盲写会把已结清账单打回"逾期";WHERE 里必须再验一次
            // "仍在可催缴状态且本金未收齐",条件不满足(updated==0)就跳过。
            // SET 走实体而不是 wrapper.set():实体形式才会触发 MyMetaObjectHandler
            // 自动填充 update_time/update_by,审计字段不掉队
            Bill patch = new Bill();
            patch.setOverdueDays((int) days);
            patch.setLateFee(lateFee);
            patch.setStatus(6);
            int updated = billMapper.update(patch, new LambdaUpdateWrapper<Bill>()
                    .eq(Bill::getId, b.getId())
                    .in(Bill::getStatus, 3, 4, 6)
                    .apply("paid_amount < amount"));
            if (updated == 1) {
                count++;
            }
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

    /**
     * 修改账单:只放行非资金字段。
     *
     * <p>此前全字段 updateById,持 {@code finance:bill:edit} 即可绕开收款服务直接改写
     * paid_amount/status/amount —— 与 9b0ecc2 在采购/工作流里修掉的是同一类洞。
     * 资金字段各有唯一入口:实收/结清走 {@code PaymentService.收款},滞纳金/逾期走
     * {@code calcLateFee},金额与来源由生成方(登记表/合同)负责。</p>
     */
    @Operation(summary = "修改账单(仅限租客/费用类型/账期/应收日/税率/备注等非资金字段)")
    @PreAuthorize("hasAuthority('finance:bill:edit')")
    @PutMapping
    public Result<Void> update(@RequestBody Bill bill) {
        if (bill.getId() == null) {
            throw new BizException("缺少账单 id");
        }
        Bill patch = new Bill();
        patch.setId(bill.getId());
        patch.setTenantRefId(bill.getTenantRefId());
        patch.setFeeType(bill.getFeeType());
        patch.setPeriodStart(bill.getPeriodStart());
        patch.setPeriodEnd(bill.getPeriodEnd());
        patch.setDueDate(bill.getDueDate());
        patch.setTaxRate(bill.getTaxRate());
        patch.setRemark(bill.getRemark());
        int updated = billMapper.updateById(patch);
        if (updated == 0) {
            throw new BizException("账单不存在: " + bill.getId());
        }
        return Result.ok();
    }

    /**
     * 删除账单:有实收、有收款记录或已开票的一律拒删。
     *
     * <p>软删会把 {@code billing_active_key} 置 NULL,幂等键随即可复用 —— 下次
     * 生成账单会给同一账期重新插一张新账单,而已收的钱从统计里消失(账实脱钩)。
     * 实收条件放在 DELETE 的 WHERE 里,并发下刚落一笔收款也删不掉。</p>
     */
    @Operation(summary = "删除账单(有实收/收款记录/发票的不可删)")
    @PreAuthorize("hasAuthority('finance:bill:delete')")
    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long payments = paymentMapper.selectCount(
                new LambdaQueryWrapper<Payment>().eq(Payment::getBillId, id));
        if (payments != null && payments > 0) {
            throw new BizException("该账单已有收款记录,不能删除");
        }
        Long invoices = invoiceMapper.selectCount(
                new LambdaQueryWrapper<Invoice>().eq(Invoice::getBillId, id));
        if (invoices != null && invoices > 0) {
            throw new BizException("该账单已关联发票,不能删除");
        }
        // 实收守卫放进 DELETE 的 WHERE:并发下刚落一笔收款也删不掉
        int deleted = billMapper.delete(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getId, id)
                .eq(Bill::getPaidAmount, BigDecimal.ZERO));
        if (deleted == 0) {
            throw new BizException("账单不存在或已有实收,不能删除");
        }
        // 删单后再验一次发票:上面查完、删之前这窗口里刚开出的发票会让整个删除回滚,
        // 不留孤儿发票(fin_invoice.bill_id 无外键兜底)
        Long invoicedAfter = invoiceMapper.selectCount(
                new LambdaQueryWrapper<Invoice>().eq(Invoice::getBillId, id));
        if (invoicedAfter != null && invoicedAfter > 0) {
            throw new BizException("该账单刚被开票,删除已回滚");
        }
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
