package com.zhyq.park.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "财务-报表")
@RestController
@RequestMapping("/finance/report")
@RequiredArgsConstructor
public class ReportController {

    private final BillMapper billMapper;

    @Operation(summary = "财务汇总报表(收缴率/收入结构/账龄分布)")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        List<Bill> all = billMapper.selectList(new LambdaQueryWrapper<>());
        LocalDate today = LocalDate.now();

        BigDecimal receivable = BigDecimal.ZERO; // 应收(direction=1)
        BigDecimal received = BigDecimal.ZERO;   // 实收

        // 收入结构:按 feeType 分组的应收金额
        Map<String, BigDecimal> feeTypeMap = new LinkedHashMap<>();

        // 账龄分布:未逾期 / 30天内 / 30-90 / 90天以上(基于未结清欠款金额)
        BigDecimal agingNotOverdue = BigDecimal.ZERO;
        BigDecimal aging30 = BigDecimal.ZERO;
        BigDecimal aging30to90 = BigDecimal.ZERO;
        BigDecimal aging90 = BigDecimal.ZERO;

        for (Bill b : all) {
            boolean isReceivable = b.getDirection() != null && b.getDirection() == 1;
            BigDecimal amount = nz(b.getAmount());
            BigDecimal paid = nz(b.getPaidAmount());

            if (isReceivable) {
                receivable = receivable.add(amount);
                String feeType = b.getFeeType() == null ? "其它" : b.getFeeType();
                feeTypeMap.merge(feeType, amount, BigDecimal::add);
            }
            received = received.add(paid);

            // 账龄:仅统计未结清欠款(应收 - 实收 > 0)
            BigDecimal owe = amount.subtract(paid);
            if (isReceivable && owe.compareTo(BigDecimal.ZERO) > 0) {
                long overdueDays = b.getDueDate() == null ? 0
                        : today.toEpochDay() - b.getDueDate().toEpochDay();
                if (overdueDays <= 0) {
                    agingNotOverdue = agingNotOverdue.add(owe);
                } else if (overdueDays <= 30) {
                    aging30 = aging30.add(owe);
                } else if (overdueDays <= 90) {
                    aging30to90 = aging30to90.add(owe);
                } else {
                    aging90 = aging90.add(owe);
                }
            }
        }

        // 收缴率 = 实收 / 应收,给 scale + RoundingMode 防除0
        BigDecimal collectRate;
        if (receivable.compareTo(BigDecimal.ZERO) == 0) {
            collectRate = BigDecimal.ZERO;
        } else {
            collectRate = received.divide(receivable, 4, RoundingMode.HALF_UP);
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : feeTypeMap.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("feeType", e.getKey());
            item.put("amount", e.getValue());
            list.add(item);
        }

        Map<String, Object> aging = new LinkedHashMap<>();
        aging.put("notOverdue", agingNotOverdue);
        aging.put("within30", aging30);
        aging.put("days30to90", aging30to90);
        aging.put("over90", aging90);

        Map<String, Object> m = new HashMap<>();
        m.put("receivable", receivable);
        m.put("received", received);
        m.put("collectRate", collectRate);
        m.put("list", list);
        m.put("aging", aging);
        return Result.ok(m);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
