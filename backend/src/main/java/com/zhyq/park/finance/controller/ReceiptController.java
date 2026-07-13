package com.zhyq.park.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.finance.entity.Receipt;
import com.zhyq.park.finance.entity.ReceiptLog;
import com.zhyq.park.finance.mapper.ReceiptLogMapper;
import com.zhyq.park.finance.mapper.ReceiptMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "财务-收据")
@RestController
@RequestMapping("/finance/receipt")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptMapper receiptMapper;
    private final ReceiptLogMapper receiptLogMapper;

    @Operation(summary = "分页查询收据")
    @GetMapping("/page")
    public Result<PageResult<Receipt>> page(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String receiptNo,
                                            @RequestParam(required = false) Long tenantRefId) {
        LambdaQueryWrapper<Receipt> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(receiptNo), Receipt::getReceiptNo, receiptNo)
          .eq(tenantRefId != null, Receipt::getTenantRefId, tenantRefId)
          .orderByDesc(Receipt::getId);
        IPage<Receipt> p = receiptMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "收据详情")
    @GetMapping("/{id}")
    public Result<Receipt> get(@PathVariable Long id) {
        return Result.ok(receiptMapper.selectById(id));
    }

    @Operation(summary = "新增收据")
    @PostMapping
    public Result<Long> add(@RequestBody Receipt receipt) {
        receiptMapper.insert(receipt);
        return Result.ok(receipt.getId());
    }

    @Operation(summary = "修改收据")
    @PutMapping
    public Result<Void> update(@RequestBody Receipt receipt) {
        receiptMapper.updateById(receipt);
        return Result.ok();
    }

    @Operation(summary = "删除收据")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        receiptMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "打印收据(打印次数原子+1,写打印日志)")
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/{id}/print")
    public Result<Void> print(@PathVariable Long id) {
        Receipt receipt = receiptMapper.selectById(id);
        if (receipt == null) {
            throw new BizException("收据不存在: " + id);
        }
        LocalDateTime now = LocalDateTime.now();
        // 原子 +1,避免并发丢更新
        receiptMapper.update(null, new LambdaUpdateWrapper<Receipt>()
                .eq(Receipt::getId, id)
                .setSql("print_count = print_count + 1")
                .set(Receipt::getLastPrintTime, now));
        ReceiptLog log = new ReceiptLog();
        log.setReceiptId(id);
        log.setOperator("system");
        log.setPrintTime(now);
        receiptLogMapper.insert(log);
        return Result.ok();
    }

    @Operation(summary = "收据打印日志列表")
    @GetMapping("/{id}/logs")
    public Result<List<ReceiptLog>> logs(@PathVariable Long id) {
        return Result.ok(receiptLogMapper.selectList(
                new LambdaQueryWrapper<ReceiptLog>()
                        .eq(ReceiptLog::getReceiptId, id)
                        .orderByDesc(ReceiptLog::getId)));
    }
}
