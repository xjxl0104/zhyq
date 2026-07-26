package com.zhyq.park.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.finance.entity.Payment;
import com.zhyq.park.finance.mapper.PaymentMapper;
import com.zhyq.park.finance.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "财务-收款")
@RestController
@RequestMapping("/finance/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;

    @Operation(summary = "收款(事务:支付单+账单结清+流水;幂等键 payNo 可选)")
    @PreAuthorize("hasAuthority('finance:payment:pay')")
    @PostMapping
    public Result<Payment> pay(@RequestBody Map<String, Object> body) {
        Long billId = body.get("billId") == null ? null : Long.valueOf(body.get("billId").toString());
        BigDecimal amount = body.get("amount") == null ? null : new BigDecimal(body.get("amount").toString());
        String payMethod = body.get("payMethod") == null ? null : body.get("payMethod").toString();
        String payNo = body.get("payNo") == null ? null : body.get("payNo").toString();
        return Result.ok(paymentService.收款(billId, amount, payMethod, payNo));
    }

    @Operation(summary = "查某账单的收款记录")
    @PreAuthorize("hasAuthority('finance:payment:query')")
    @GetMapping("/list")
    public Result<List<Payment>> list(@RequestParam Long billId) {
        return Result.ok(paymentMapper.selectList(
                new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getBillId, billId)
                        .orderByDesc(Payment::getId)));
    }
}
