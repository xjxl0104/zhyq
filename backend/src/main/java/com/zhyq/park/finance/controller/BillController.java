package com.zhyq.park.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

    private final BillMapper billMapper;

    @Operation(summary = "分页查询账单")
    @GetMapping("/page")
    public Result<PageResult<Bill>> page(@RequestParam(defaultValue = "1") int pageNo,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) Long contractId,
                                         @RequestParam(required = false) Long tenantRefId,
                                         @RequestParam(required = false) Integer direction,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String feeType,
                                         @RequestParam(required = false) Boolean onlyDue) {
        LambdaQueryWrapper<Bill> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(code), Bill::getCode, code)
          .eq(contractId != null, Bill::getContractId, contractId)
          .eq(tenantRefId != null, Bill::getTenantRefId, tenantRefId)
          .eq(direction != null, Bill::getDirection, direction)
          .eq(status != null, Bill::getStatus, status)
          .eq(StringUtils.hasText(feeType), Bill::getFeeType, feeType)
          // 隐藏未到期:仅显示应收日<=今天的账单
          .le(Boolean.TRUE.equals(onlyDue), Bill::getDueDate, LocalDate.now())
          .orderByDesc(Bill::getId);
        IPage<Bill> p = billMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "账单统计(顶部卡片)")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        List<Bill> all = billMapper.selectList(new LambdaQueryWrapper<>());
        BigDecimal receivable = BigDecimal.ZERO;  // 应收:direction=1 的 amount 合计
        BigDecimal received = BigDecimal.ZERO;     // 实收:paid_amount 合计
        BigDecimal lateFee = BigDecimal.ZERO;      // 滞纳金合计
        long overdueCount = 0;                     // 逾期账单数(status=6)
        for (Bill b : all) {
            if (b.getDirection() != null && b.getDirection() == 1) {
                receivable = receivable.add(nz(b.getAmount()));
            }
            received = received.add(nz(b.getPaidAmount()));
            lateFee = lateFee.add(nz(b.getLateFee()));
            if (b.getStatus() != null && b.getStatus() == 6) {
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
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "计算滞纳金(遍历逾期未结清的应收账单)")
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
    @GetMapping("/{id}")
    public Result<Bill> get(@PathVariable Long id) {
        return Result.ok(billMapper.selectById(id));
    }

    @Operation(summary = "新增账单")
    @PostMapping
    public Result<Long> add(@RequestBody Bill bill) {
        billMapper.insert(bill);
        return Result.ok(bill.getId());
    }

    @Operation(summary = "修改账单")
    @PutMapping
    public Result<Void> update(@RequestBody Bill bill) {
        billMapper.updateById(bill);
        return Result.ok();
    }

    @Operation(summary = "删除账单")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        billMapper.deleteById(id);
        return Result.ok();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
