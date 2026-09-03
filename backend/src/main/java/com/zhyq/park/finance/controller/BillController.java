package com.zhyq.park.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.audit.OperationLog;
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
import com.zhyq.park.finance.service.LateFeeService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final LateFeeService lateFeeService;

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
        // 「保证金」是费用大类:登记表生成的是「租金保证金/物业保证金」,按后缀命中
        // 全部保证金类账单(老数据里费用类型恰好叫「保证金」的也被 %保证金 覆盖)
        boolean depositCategory = "保证金".equals(feeType);
        qw.eq(billId != null, Bill::getId, billId)
          .like(StringUtils.hasText(code), Bill::getCode, code)
          .eq(contractId != null, Bill::getContractId, contractId)
          .eq(tenantRefId != null, Bill::getTenantRefId, tenantRefId)
          .eq(direction != null, Bill::getDirection, direction)
          .eq(status != null, Bill::getStatus, status)
          .likeLeft(depositCategory, Bill::getFeeType, "保证金")
          .eq(!depositCategory && StringUtils.hasText(feeType), Bill::getFeeType, feeType)
          .eq(StringUtils.hasText(source), Bill::getSource, source)
          // 隐藏未到期:仅显示应收日<=今天的账单
          .le(Boolean.TRUE.equals(onlyDue), Bill::getDueDate, LocalDate.now())
          // 台账习惯:按账期从合同起始月往后排,而不是最后生成的(合同末期)排最前
          .orderByAsc(Bill::getPeriodStart)
          .orderByAsc(Bill::getId);
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

    /** 账单页按钮可见性:前端据此决定显示哪些操作入口(与 @PreAuthorize 同一权限点) */
    public record BillCapabilities(boolean lateFeeAdjust) {}

    @Operation(summary = "账单页操作权限(前端按钮可见性)")
    @GetMapping("/capabilities")
    public Result<BillCapabilities> capabilities() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return Result.ok(new BillCapabilities(
                hasAuthority(authentication, "finance:bill:lateFee:adjust")));
    }

    private static boolean hasAuthority(org.springframework.security.core.Authentication authentication,
                                        String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(item -> authority.equals(item.getAuthority()));
    }

    @Operation(summary = "计算滞纳金(逾期未结清应收账单,与每日自愈任务同一实现)")
    @PreAuthorize("hasAuthority('finance:bill:calcLateFee')")
    @PostMapping("/calcLateFee")
    public Result<Integer> calcLateFee() {
        // 计算逻辑在 LateFeeService:ReceivableBillSyncJob 每天自动跑同一份,
        // 这个端点保留给"不想等自愈周期"的手动触发
        return Result.ok(lateFeeService.recalc());
    }

    /**
     * 滞纳金人工调整请求。
     *
     * @param lateFee 调整后的滞纳金,>=0;restoreAuto=true 时忽略
     * @param remark  调整原因(如「与租户协商减免」),留痕给对账/审计看
     * @param restoreAuto true=撤销人工调整、恢复自动计算(并立即重算)
     */
    public record LateFeeAdjustRequest(BigDecimal lateFee, String remark, Boolean restoreAuto) {}

    @Operation(summary = "调整滞纳金(人工改额或减免;调整后锁定不再被自动重算覆盖,可恢复自动)")
    @PreAuthorize("hasAuthority('finance:bill:lateFee:adjust')")
    @OperationLog(module = "账单", action = "调整滞纳金")
    @PutMapping("/{id}/late-fee")
    public Result<Void> adjustLateFee(@PathVariable Long id,
                                      @RequestBody LateFeeAdjustRequest request) {
        Bill bill = billMapper.selectById(id);
        if (bill == null) throw new BizException("账单不存在");
        if (!BillMetrics.isReceivable(bill)) throw new BizException("只有应收账单才有滞纳金");
        if (Boolean.TRUE.equals(request.restoreAuto())) {
            // 解锁 + 立即重算:让管理员当场看到自动口径算出来的数,不用等自愈任务
            Bill patch = new Bill();
            patch.setLateFeeManual(0);
            patch.setLateFeeRemark("");
            billMapper.update(patch, new LambdaUpdateWrapper<Bill>().eq(Bill::getId, id));
            lateFeeService.recalc();
            return Result.ok();
        }
        BigDecimal lateFee = request.lateFee();
        if (lateFee == null || lateFee.signum() < 0) throw new BizException("滞纳金不能为负数");
        lateFee = lateFee.setScale(2, java.math.RoundingMode.HALF_UP);
        // 结清判定与 PaymentService 同口径(付满 本金+滞纳金 才算结清):减免滞纳金后
        // 可能刚好收满,状态要跟着走,否则会出现「欠款 ¥0 却仍显示逾期」。只做
        // 「未结清→已结清」这一个方向,不把逾期账单降级成别的状态
        int updated = billMapper.update(null, new LambdaUpdateWrapper<Bill>()
                .eq(Bill::getId, id)
                .in(Bill::getStatus, PAYABLE_STATUS)
                .set(Bill::getLateFee, lateFee)
                .set(Bill::getLateFeeManual, 1)
                .set(Bill::getLateFeeRemark, request.remark())
                .set(Bill::getUpdateTime, java.time.LocalDateTime.now())
                .setSql("status = IF(paid_amount >= amount + " + lateFee.toPlainString()
                        + ", " + BillMetrics.STATUS_SETTLED + ", status)"));
        if (updated != 1) {
            throw new BizException("该账单当前状态不可调整滞纳金(仅待收付/部分结清/逾期账单可调)");
        }
        return Result.ok();
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

    /**
     * 重置登记表推送的账单:一键作废「由应收明细登记表生成、且未收款未开票」的全部账单。
     *
     * <p>推送口径出错时(如免租月落错)的重来通道:软删后 billing_active_key 释放,
     * 回登记表重新点「生成账单」即可按新口径重出。已收款/已关联发票的账单一律保留,
     * 与单条删除同一守卫;实收守卫同样写进 DELETE 的 WHERE,并发下刚落款的删不掉。</p>
     */
    @Operation(summary = "重置登记表账单(未收款未开票的全部作废,可重新推送)")
    @PreAuthorize("hasAuthority('finance:bill:delete')")
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/reset")
    public Result<Map<String, Object>> reset() {
        Set<Long> protectedIds = new HashSet<>();
        paymentMapper.selectList(new LambdaQueryWrapper<Payment>()
                        .select(Payment::getBillId).isNotNull(Payment::getBillId))
                .forEach(p -> protectedIds.add(p.getBillId()));
        invoiceMapper.selectList(new LambdaQueryWrapper<Invoice>()
                        .select(Invoice::getBillId).isNotNull(Invoice::getBillId))
                .forEach(i -> protectedIds.add(i.getBillId()));
        LambdaQueryWrapper<Bill> deletable = new LambdaQueryWrapper<Bill>()
                .eq(Bill::getSource, "应收登记表")
                .eq(Bill::getPaidAmount, BigDecimal.ZERO);
        if (!protectedIds.isEmpty()) {
            deletable.notIn(Bill::getId, protectedIds);
        }
        int deleted = billMapper.delete(deletable);
        Long kept = billMapper.selectCount(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getSource, "应收登记表"));
        Map<String, Object> result = new HashMap<>();
        result.put("deleted", deleted);
        result.put("kept", kept == null ? 0L : kept);
        return Result.ok(result);
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
