package com.zhyq.park.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.entity.Payment;
import com.zhyq.park.finance.mapper.PaymentMapper;
import com.zhyq.park.finance.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
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

    /**
     * 零元核销:免租期/抵扣期账单应收就是 0,没钱可收但要能结清。
     * 不走收款(不产生 0 元支付单/流水/收据),只推账单状态并留痕。
     */
    @Operation(summary = "零元核销(免租期等 0 应收账单直接结清,不产生资金记录)")
    @PreAuthorize("hasAuthority('finance:bill:writeOff')")
    @PostMapping("/write-off")
    public Result<Bill> writeOff(@RequestBody Map<String, Object> body) {
        Long billId = body.get("billId") == null ? null : Long.valueOf(body.get("billId").toString());
        String remark = body.get("remark") == null ? null : body.get("remark").toString();
        return Result.ok(paymentService.零元核销(billId, remark, username()));
    }

    /**
     * 撤销收款(红冲)。收错了要能退:原单标记已撤销 + 生成负额红冲单 + 负额流水
     * + 作废收据 + 账单实收回退。不做物理删除,留痕可审计。
     */
    @Operation(summary = "撤销收款(红冲留痕:原单作废+负额红冲单+账单实收回退)")
    @PreAuthorize("hasAuthority('finance:payment:void')")
    @PostMapping("/{id}/void")
    public Result<Payment> voidPayment(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null || body.get("reason") == null ? null : body.get("reason").toString();
        return Result.ok(paymentService.撤销收款(id, reason, username()));
    }

    @Operation(summary = "查某账单的收款记录(含红冲单,按 id 倒序)")
    @PreAuthorize("hasAuthority('finance:payment:query')")
    @GetMapping("/list")
    public Result<List<Payment>> list(@RequestParam Long billId) {
        return Result.ok(paymentMapper.selectList(
                new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getBillId, billId)
                        .orderByDesc(Payment::getId)));
    }

    private static String username() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : auth.getName();
    }
}
