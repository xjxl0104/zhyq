package com.zhyq.park.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.finance.entity.Invoice;
import com.zhyq.park.finance.mapper.InvoiceMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "财务-发票")
@RestController
@RequestMapping("/finance/invoice")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceMapper invoiceMapper;

    @Operation(summary = "分页查询发票")
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
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "发票详情")
    @GetMapping("/{id}")
    public Result<Invoice> get(@PathVariable Long id) {
        return Result.ok(invoiceMapper.selectById(id));
    }

    @Operation(summary = "新增发票")
    @PostMapping
    public Result<Long> add(@RequestBody Invoice invoice) {
        invoiceMapper.insert(invoice);
        return Result.ok(invoice.getId());
    }

    @Operation(summary = "修改发票")
    @PutMapping
    public Result<Void> update(@RequestBody Invoice invoice) {
        invoiceMapper.updateById(invoice);
        return Result.ok();
    }

    @Operation(summary = "删除发票")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        invoiceMapper.deleteById(id);
        return Result.ok();
    }
}
