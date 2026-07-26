package com.zhyq.park.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.entity.ContractRef;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.finance.mapper.ContractRefMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "财务-退房报表")
@RestController
@RequestMapping("/finance/checkout-report")
@RequiredArgsConstructor
public class CheckoutReportController {

    private final ContractRefMapper contractMapper;
    private final BillMapper billMapper;

    // 已终止合同
    private static final int CONTRACT_TERMINATED = 9;
    private static final String FEE_RENT = "租金";

    @Operation(summary = "退房报表分页(已终止合同 + 租金账单聚合)")
    @PreAuthorize("hasAuthority('finance:report:query')")
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                        @RequestParam(defaultValue = "10") int pageSize) {
        IPage<ContractRef> p = contractMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<ContractRef>()
                        .eq(ContractRef::getStatus, CONTRACT_TERMINATED)
                        .orderByDesc(ContractRef::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ContractRef c : p.getRecords()) {
            List<Bill> rentBills = billMapper.selectList(new LambdaQueryWrapper<Bill>()
                    .eq(Bill::getContractId, c.getId())
                    .eq(Bill::getFeeType, FEE_RENT));
            BigDecimal rentTotal = BigDecimal.ZERO;
            BigDecimal rentPaid = BigDecimal.ZERO;
            for (Bill b : rentBills) {
                rentTotal = rentTotal.add(nz(b.getAmount()));
                rentPaid = rentPaid.add(nz(b.getPaidAmount()));
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("contractId", c.getId());
            m.put("code", c.getCode());
            m.put("tenantRefId", c.getTenantRefId());
            m.put("terminateDate", c.getTerminateDate());
            m.put("rentTotal", rentTotal);
            m.put("rentPaid", rentPaid);
            m.put("deposit", nz(c.getDeposit()));
            rows.add(m);
        }
        return Result.ok(PageResult.of(p.getTotal(), rows));
    }

    @Operation(summary = "退房报表统计")
    @PreAuthorize("hasAuthority('finance:report:query')")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        List<ContractRef> list = contractMapper.selectList(new LambdaQueryWrapper<ContractRef>()
                .eq(ContractRef::getStatus, CONTRACT_TERMINATED));
        long contractCount = list.size();
        BigDecimal depositTotal = BigDecimal.ZERO;
        BigDecimal rentPaidTotal = BigDecimal.ZERO;
        for (ContractRef c : list) {
            depositTotal = depositTotal.add(nz(c.getDeposit()));
            List<Bill> rentBills = billMapper.selectList(new LambdaQueryWrapper<Bill>()
                    .eq(Bill::getContractId, c.getId())
                    .eq(Bill::getFeeType, FEE_RENT));
            for (Bill b : rentBills) {
                rentPaidTotal = rentPaidTotal.add(nz(b.getPaidAmount()));
            }
        }
        Map<String, Object> m = new HashMap<>();
        m.put("contractCount", contractCount);
        m.put("depositTotal", depositTotal);
        m.put("rentPaidTotal", rentPaidTotal);
        return Result.ok(m);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
