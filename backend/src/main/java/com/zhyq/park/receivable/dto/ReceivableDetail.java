package com.zhyq.park.receivable.dto;

import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.importing.entity.ImportBatch;
import com.zhyq.park.importing.entity.ImportRow;
import com.zhyq.park.receivable.entity.DepositLedger;
import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.entity.ReceivableRule;

import java.util.List;

public record ReceivableDetail(
        ReceivableRegister register,
        List<ReceivableRule> rules,
        List<DepositLedger> deposits,
        List<Bill> bills,
        ImportBatch sourceBatch,
        ImportRow sourceRow
) {}
