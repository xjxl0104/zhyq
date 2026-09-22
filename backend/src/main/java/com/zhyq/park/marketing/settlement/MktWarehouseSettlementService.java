package com.zhyq.park.marketing.settlement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.finance.MktServiceFeeBillService;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.MktCommissionService;
import com.zhyq.park.marketing.service.MktNoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class MktWarehouseSettlementService {
    public static final int PENDING = 2, CONFIRMED = 3, PAID = 4, DISPUTED = 5, FROZEN = 6;
    private final MktWarehouseSettlementMapper settlementMapper;
    private final MktWarehouseSettlementLineMapper lineMapper;
    private final MktServiceFeeBillMapper billMapper;
    private final MktDirectSignPaymentMapper paymentMapper;
    private final MktReferralOrderMapper orderMapper;
    private final MktWarehouseMapper warehouseMapper;
    private final MktErpReconcileSnapshotMapper reconcileMapper;
    private final MktCommissionService commissionService;
    private final MktNoticeService noticeService;
    private final MktPromoterCommissionMapper commissionMapper;

    @Transactional
    public MktWarehouseSettlement generate(Long warehouseId, LocalDate start, LocalDate end) {
        MktWarehouse w = warehouseMapper.selectById(warehouseId); if (w == null) throw new BizException("云仓不存在");
        MktWarehouseSettlement old = settlementMapper.selectOne(new LambdaQueryWrapper<MktWarehouseSettlement>().eq(MktWarehouseSettlement::getWarehouseId, warehouseId).eq(MktWarehouseSettlement::getPeriodStart, start).eq(MktWarehouseSettlement::getPeriodEnd, end).last("limit 1"));
        if (old != null) return old;
        List<MktServiceFeeBill> bills = billMapper.selectList(new LambdaQueryWrapper<MktServiceFeeBill>().eq(MktServiceFeeBill::getWarehouseId, warehouseId).in(MktServiceFeeBill::getStatus, MktServiceFeeBillService.CONFIRMED, MktServiceFeeBillService.SETTLED).ge(MktServiceFeeBill::getPeriodStart, start).le(MktServiceFeeBill::getPeriodEnd, end));
        BigDecimal total = bills.stream().map(MktServiceFeeBill::getAmount).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        MktErpReconcileSnapshot snapshot = reconcileMapper.selectOne(new LambdaQueryWrapper<MktErpReconcileSnapshot>().eq(MktErpReconcileSnapshot::getWarehouseId, warehouseId).eq(MktErpReconcileSnapshot::getPeriodStart, start).eq(MktErpReconcileSnapshot::getPeriodEnd, end).last("limit 1"));
        MktWarehouseSettlement s = new MktWarehouseSettlement(); s.setWarehouseId(warehouseId); s.setBatchNo("WH-SB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)); s.setPeriodStart(start); s.setPeriodEnd(end); s.setStatus(snapshot != null && Integer.valueOf(1).equals(snapshot.getFrozen()) ? FROZEN : PENDING); s.setFrozenReason(snapshot != null && Integer.valueOf(1).equals(snapshot.getFrozen()) ? snapshot.getReason() : null); s.setAmount(total); s.setProjectId(w.getProjectId());
        try { settlementMapper.insert(s); } catch (DuplicateKeyException e) { return settlementMapper.selectOne(new LambdaQueryWrapper<MktWarehouseSettlement>().eq(MktWarehouseSettlement::getWarehouseId, warehouseId).eq(MktWarehouseSettlement::getPeriodStart, start).eq(MktWarehouseSettlement::getPeriodEnd, end).last("limit 1")); }
        for (MktServiceFeeBill b : bills) { MktWarehouseSettlementLine l = new MktWarehouseSettlementLine(); l.setSettlementId(s.getId()); l.setBillId(b.getId()); l.setAmount(b.getAmount()); lineMapper.insert(l); }
        return s;
    }
    @Transactional public void confirm(Long id, Long warehouseId) {
        if (change(id, warehouseId, PENDING, CONFIRMED, null)) {
            noticeService.push(warehouseId, "settlement.confirmed", "结算单已确认",
                    "结算单 #" + id + " 已确认", "settlement", id);
        }
    }
    @Transactional public void dispute(Long id, Long warehouseId, String reason) {
        if (reason == null || reason.isBlank()) throw new BizException("争议原因必填");
        if (change(id, warehouseId, PENDING, DISPUTED, reason)) {
            noticeService.push(warehouseId, "settlement.disputed", "结算单争议已提交",
                    "结算单 #" + id + " 争议:" + reason, "settlement", id);
        }
    }
    /** @return 是否真的发生了状态迁移(false = 幂等的重复调用) */
    private boolean change(Long id, Long warehouseId, int from, int to, String reason) {
        int n = settlementMapper.update(null, new LambdaUpdateWrapper<MktWarehouseSettlement>().eq(MktWarehouseSettlement::getId, id).eq(MktWarehouseSettlement::getWarehouseId, warehouseId).eq(MktWarehouseSettlement::getStatus, from).set(MktWarehouseSettlement::getStatus, to).set(reason != null, MktWarehouseSettlement::getFrozenReason, reason));
        if (n == 0) { MktWarehouseSettlement x = settlementMapper.selectById(id); if (x != null && x.getWarehouseId().equals(warehouseId) && x.getStatus() == to) return false; throw new BizException("结算单状态已变化"); }
        return true;
    }

    @Transactional
    public MktDirectSignPayment recordPayment(Long contractId, Long warehouseId, String paymentNo, BigDecimal amount) {
        if (contractId == null || warehouseId == null || paymentNo == null || paymentNo.isBlank()) throw new BizException("直签到账参数不完整");
        MktDirectSignPayment old = paymentMapper.selectOne(new LambdaQueryWrapper<MktDirectSignPayment>().eq(MktDirectSignPayment::getPaymentNo, paymentNo).last("limit 1"));
        if (old != null) return old;   // 幂等:重复到账直接返回,绝不重复解冻
        if (amount == null || amount.signum() <= 0) throw new BizException("到账金额必须大于 0");
        List<MktReferralOrder> platformOrders = orderMapper.selectList(new LambdaQueryWrapper<MktReferralOrder>().eq(MktReferralOrder::getSourceType, MktCommissionService.SOURCE_PLATFORM_FEE).eq(MktReferralOrder::getSourceId, contractId));
        // 严口径:到账必须足额覆盖该合同平台费订单项下"当前仍冻结"的佣金合计,否则拒绝、不解冻
        BigDecimal due = platformOrders.stream()
                .map(o -> commissionMapper.selectList(new LambdaQueryWrapper<MktPromoterCommission>()
                        .eq(MktPromoterCommission::getReferralOrderId, o.getId())
                        .eq(MktPromoterCommission::getStatus, MktCommissionService.C_FROZEN)
                        .eq(MktPromoterCommission::getSign, 1)))
                .flatMap(List::stream)
                .map(MktPromoterCommission::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(due) < 0) {
            throw new BizException("直签平台费到账不足:应付 " + due + " 实收 " + amount + ",已拒绝解冻");
        }
        MktDirectSignPayment p = new MktDirectSignPayment(); p.setContractId(contractId); p.setWarehouseId(warehouseId); p.setPaymentNo(paymentNo); p.setAmount(amount); p.setStatus(1); p.setPaidAt(LocalDateTime.now());
        MktWarehouse w = warehouseMapper.selectById(warehouseId); p.setProjectId(w == null ? null : w.getProjectId()); paymentMapper.insert(p);
        for (MktReferralOrder o : platformOrders) commissionService.unfreezeByOrder(o.getId());
        return p;
    }
}
