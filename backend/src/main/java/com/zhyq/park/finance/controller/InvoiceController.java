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
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.finance.mapper.InvoiceMapper;
import com.zhyq.park.finance.service.FinanceViewEnricher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "财务-发票")
@RestController
@RequestMapping("/finance/invoice")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceMapper invoiceMapper;
    private final BillMapper billMapper;
    private final FinanceViewEnricher viewEnricher;

    @Operation(summary = "分页查询发票")
    @PreAuthorize("hasAuthority('finance:invoice:query')")
    @GetMapping("/page")
    public Result<PageResult<Invoice>> page(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String code,
                                            @RequestParam(required = false) String title,
                                            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Invoice> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(code), Invoice::getCode, code)
          .like(StringUtils.hasText(title), Invoice::getTitle, title)
          .eq(status != null, Invoice::getStatus, status)
          .orderByDesc(Invoice::getId);
        IPage<Invoice> p = invoiceMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        enrichInvoice(p.getRecords());
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "发票详情")
    @PreAuthorize("hasAuthority('finance:invoice:query')")
    @GetMapping("/{id}")
    public Result<Invoice> get(@PathVariable Long id) {
        Invoice invoice = invoiceMapper.selectById(id);
        if (invoice == null) {
            throw new BizException("发票不存在或已删除");
        }
        enrichInvoice(java.util.List.of(invoice));
        return Result.ok(invoice);
    }

    @Operation(summary = "新增发票")
    @PreAuthorize("hasAuthority('finance:invoice:add')")
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<Long> add(@RequestBody Invoice invoice) {
        bindBill(invoice);
        if (hasActiveInvoice(invoice.getBillId(), null)) {
            throw new BizException("该账单已有未作废或未红冲的发票，请在已有发票中处理");
        }
        invoiceMapper.insert(invoice);
        refreshBillInvoiceStatus(invoice.getBillId());
        return Result.ok(invoice.getId());
    }

    @Operation(summary = "修改发票")
    @PreAuthorize("hasAuthority('finance:invoice:edit')")
    @PutMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> update(@RequestBody Invoice invoice) {
        if (invoice.getId() == null) {
            throw new BizException("发票 ID 不能为空");
        }
        Invoice existing = invoiceMapper.selectById(invoice.getId());
        if (existing == null) {
            throw new BizException("发票不存在或已删除");
        }
        Long originalBillId = existing.getBillId();
        // 状态流转按钮只提交 id + status，此时保留原账单；编辑表单则允许改关联账单。
        if (invoice.getBillId() == null) {
            invoice.setBillId(originalBillId);
        }
        bindBill(invoice);
        if (!java.util.Objects.equals(originalBillId, invoice.getBillId())
                && hasActiveInvoice(invoice.getBillId(), invoice.getId())) {
            throw new BizException("目标账单已有未作废或未红冲的发票");
        }
        invoiceMapper.updateById(invoice);
        refreshBillInvoiceStatus(originalBillId);
        refreshBillInvoiceStatus(invoice.getBillId());
        return Result.ok();
    }

    @Operation(summary = "删除发票")
    @PreAuthorize("hasAuthority('finance:invoice:delete')")
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable Long id) {
        Invoice existing = invoiceMapper.selectById(id);
        if (existing == null) {
            throw new BizException("发票不存在或已删除");
        }
        invoiceMapper.deleteById(id);
        refreshBillInvoiceStatus(existing.getBillId());
        return Result.ok();
    }

    /**
     * 发票的租户、费用类型都以关联账单为准。前端不再让用户录入容易写错的裸 ID，
     * 后端仍要做这一层兜底，防止接口调用绕过页面后留下无法对账的发票。
     */
    private void bindBill(Invoice invoice) {
        if (invoice.getBillId() == null) {
            throw new BizException("请选择关联账单，发票必须能对应租户和费用类型");
        }
        Bill bill = billMapper.selectById(invoice.getBillId());
        if (bill == null) {
            throw new BizException("关联账单不存在或已删除");
        }
        if (bill.getDirection() == null || bill.getDirection() != 1) {
            throw new BizException("只能关联应收方向的账单开具发票");
        }
        invoice.setTenantRefId(bill.getTenantRefId());
    }

    private boolean hasActiveInvoice(Long billId, Long excludedInvoiceId) {
        if (billId == null) {
            return false;
        }
        return invoiceMapper.selectCount(new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getBillId, billId)
                .ne(excludedInvoiceId != null, Invoice::getId, excludedInvoiceId)
                .notIn(Invoice::getStatus, 4, 5)) > 0;
    }

    /** 账单只要还有一张有效发票就标记已开；红冲、作废或删除后即可重新开票。 */
    private void refreshBillInvoiceStatus(Long billId) {
        if (billId == null) {
            return;
        }
        billMapper.update(null, new LambdaUpdateWrapper<Bill>()
                .set(Bill::getInvoiceStatus, hasActiveInvoice(billId, null) ? 1 : 0)
                .eq(Bill::getId, billId));
    }

    /**
     * 填上关联账单号、租客名与费用类型。
     *
     * <p>本页此前只显示 {@code billId} —— 一个裸数字,既看不出是哪个租客的钱,
     * 也没法和账单页对上账。口径与所有账单页共用 {@link FinanceViewEnricher}。</p>
     */
    private void enrichInvoice(java.util.List<Invoice> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        java.util.Map<Long, FinanceViewEnricher.BillView> views = viewEnricher.resolveBillViews(
                rows.stream().map(Invoice::getBillId).toList());
        for (Invoice row : rows) {
            FinanceViewEnricher.BillView v = views.get(row.getBillId());
            if (v == null) {
                continue;
            }
            row.setBillCode(v.billCode());
            row.setTenantName(v.tenantName());
            row.setFeeType(v.feeType());
        }
    }
}
