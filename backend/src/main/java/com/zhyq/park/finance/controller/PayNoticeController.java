package com.zhyq.park.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.entity.PayNotice;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.finance.mapper.PayNoticeMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Tag(name = "财务-收款通知")
@RestController
@RequestMapping("/finance/notice")
@RequiredArgsConstructor
public class PayNoticeController {

    private final PayNoticeMapper noticeMapper;
    private final BillMapper billMapper;

    private static final int ST_PENDING = 1; // 待发送
    private static final int ST_SENT = 2;    // 已发送

    @Operation(summary = "分页查询收款通知")
    @GetMapping("/page")
    public Result<PageResult<PayNotice>> page(@RequestParam(defaultValue = "1") int pageNo,
                                              @RequestParam(defaultValue = "10") int pageSize,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(required = false) Long billId) {
        LambdaQueryWrapper<PayNotice> qw = new LambdaQueryWrapper<>();
        qw.eq(status != null, PayNotice::getStatus, status)
          .eq(billId != null, PayNotice::getBillId, billId)
          .orderByDesc(PayNotice::getId);
        IPage<PayNotice> p = noticeMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "删除收款通知")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        noticeMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "一键生成收款通知(对逾期/待收付应收账单且无待发送通知者生成)")
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/generate")
    public Result<Integer> generate() {
        LocalDate today = LocalDate.now();
        // 应收方向、可催缴状态(待收付3/部分结清4/逾期6)、应收日已到
        List<Bill> bills = billMapper.selectList(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getDirection, 1)
                .in(Bill::getStatus, 3, 4, 6)
                .le(Bill::getDueDate, today));
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int count = 0;
        int seq = 0;
        for (Bill b : bills) {
            // 已存在该账单的待发送通知则跳过
            Long exists = noticeMapper.selectCount(new LambdaQueryWrapper<PayNotice>()
                    .eq(PayNotice::getBillId, b.getId())
                    .eq(PayNotice::getStatus, ST_PENDING));
            if (exists != null && exists > 0) {
                continue;
            }
            BigDecimal owe = nz(b.getAmount()).subtract(nz(b.getPaidAmount()));
            PayNotice n = new PayNotice();
            n.setNoticeNo("TZ" + ts + String.format("%03d", ++seq));
            n.setBillId(b.getId());
            n.setTenantRefId(b.getTenantRefId());
            n.setAmount(owe);
            n.setStatus(ST_PENDING);
            noticeMapper.insert(n);
            count++;
        }
        return Result.ok(count);
    }

    @Operation(summary = "发送收款通知(条件更新 1→2)")
    @PostMapping("/{id}/send")
    public Result<Void> send(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String sendChannel = body != null && body.get("sendChannel") != null
                ? body.get("sendChannel").toString() : null;
        LambdaUpdateWrapper<PayNotice> uw = new LambdaUpdateWrapper<PayNotice>()
                .eq(PayNotice::getId, id)
                .eq(PayNotice::getStatus, ST_PENDING)
                .set(PayNotice::getStatus, ST_SENT)
                .set(PayNotice::getSendTime, LocalDateTime.now());
        if (sendChannel != null && !sendChannel.isEmpty()) {
            uw.set(PayNotice::getSendChannel, sendChannel);
        }
        noticeMapper.update(null, uw);
        return Result.ok();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
