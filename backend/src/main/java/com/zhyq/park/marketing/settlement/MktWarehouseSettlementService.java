package com.zhyq.park.marketing.settlement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.finance.MktServiceFeeBillService;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.*;
import com.zhyq.park.marketing.engine.PoolCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

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
    private final MktServiceFeeBillLineMapper billLineMapper;
    private final MktServiceContractMapper contractMapper;
    private final MktCustomerGradeMapper gradeMapper;
    private final MktPaymentProofService proofs;
    private final MktAuditService audit;
    private final ObjectMapper json;

    @Transactional
    public MktWarehouseSettlement generate(Long warehouseId, LocalDate start, LocalDate end) {
        MktServiceFeeBillService.validatePeriod(start, end);
        MktWarehouse w = warehouseMapper.selectById(warehouseId);
        if (w == null || !Integer.valueOf(5).equals(w.getJoinStatus())) throw new BizException("云仓须完成审核并上线后才能结算");
        MktWarehouseSettlement old = settlementMapper.selectOne(new LambdaQueryWrapper<MktWarehouseSettlement>()
                .eq(MktWarehouseSettlement::getWarehouseId, warehouseId).eq(MktWarehouseSettlement::getPeriodStart, start)
                .eq(MktWarehouseSettlement::getPeriodEnd, end).last("limit 1"));
        if (old != null) return old;
        if (settlementMapper.selectCount(new LambdaQueryWrapper<MktWarehouseSettlement>().eq(MktWarehouseSettlement::getWarehouseId, warehouseId)
                .le(MktWarehouseSettlement::getPeriodStart, end).ge(MktWarehouseSettlement::getPeriodEnd, start)) > 0)
            throw new BizException("结算期间与已有批次重叠");
        var snapshot = snapshot(warehouseId, start, end);
        if (!"manual".equals(w.getOrderMode()) && snapshot == null) throw new BizException("ERP订单尚无对账结果,请先完成对账");
        BigDecimal perOrder = rate(w.getFeeModel(), "perOrder"), perItem = rate(w.getFeeModel(), "perItem");
        if (perOrder.add(perItem).signum() <= 0) throw new BizException("请先配置云仓应付单票/按件费率");
        List<MktServiceFeeBill> bills = billMapper.selectList(new LambdaQueryWrapper<MktServiceFeeBill>()
                .eq(MktServiceFeeBill::getWarehouseId, warehouseId).eq(MktServiceFeeBill::getStatus, MktServiceFeeBillService.SETTLED)
                .ge(MktServiceFeeBill::getPeriodStart, start).le(MktServiceFeeBill::getPeriodEnd, end)
                .notInSql(MktServiceFeeBill::getId, "SELECT bill_id FROM crm_warehouse_settlement_line WHERE deleted=0 AND bill_id IS NOT NULL"));
        if (bills.isEmpty()) throw new BizException("该期间没有已收款且尚未结算的服务费账单");
        List<MktWarehouseSettlementLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (MktServiceFeeBill bill : bills) {
            int qty = 0, packages = 0;
            BigDecimal commissions = BigDecimal.ZERO;
            var details = billLineMapper.selectList(new LambdaQueryWrapper<MktServiceFeeBillLine>().eq(MktServiceFeeBillLine::getBillId, bill.getId()));
            if (details.isEmpty()) throw new BizException("服务费账单缺少出库明细");
            for (var detail : details) {
                var o = orderMapper.selectById(detail.getReferralOrderId());
                if (o == null || !Objects.equals(warehouseId, o.getWarehouseId()) || !Integer.valueOf(2).equals(o.getStatus())
                        || o.getQty() == null || o.getPackages() == null || o.getQty() < 0 || o.getPackages() <= 0)
                    throw new BizException("订单已撤销、归属不符或缺少数量,请先核对账单");
                qty = Math.addExact(qty, o.getQty()); packages = Math.addExact(packages, o.getPackages());
                if (o.getPoolAmount() == null || o.getPoolAmount().signum() < 0) throw new BizException("订单佣金快照缺失,请先核对账单");
                commissions = commissions.add(o.getPoolAmount());
            }
            BigDecimal cost = perOrder.multiply(BigDecimal.valueOf(packages)).add(perItem.multiply(BigDecimal.valueOf(qty))).setScale(2, RoundingMode.HALF_UP);
            if (bill.getAmount() == null || bill.getAmount().subtract(cost).subtract(commissions).signum() < 0)
                throw new BizException("当前云仓费率使本期毛利为负,请核对合同收入、应付成本和佣金快照");
            MktWarehouseSettlementLine line = new MktWarehouseSettlementLine(); line.setBillId(bill.getId()); line.setAmount(cost);
            try { line.setSnapshotJson(json.writeValueAsString(Map.of("perOrder", perOrder, "perItem", perItem, "qty", qty, "packages", packages, "serviceFee", bill.getAmount(), "commission", commissions))); }
            catch (Exception e) { throw new BizException("结算快照生成失败"); }
            lines.add(line); total = total.add(cost);
        }
        if (total.signum() <= 0) throw new BizException("本期应付结算金额为0,无需生成结算单");
        boolean frozen = snapshot != null && Integer.valueOf(1).equals(snapshot.getFrozen());
        MktWarehouseSettlement s = new MktWarehouseSettlement(); s.setWarehouseId(warehouseId);
        s.setBatchNo("WH-SB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        s.setPeriodStart(start); s.setPeriodEnd(end); s.setStatus(frozen ? FROZEN : PENDING);
        s.setFrozenReason(frozen ? snapshot.getReason() : null); s.setAmount(total); s.setProjectId(w.getProjectId());
        try {
            settlementMapper.insert(s);
            for (var line : lines) { line.setSettlementId(s.getId()); lineMapper.insert(line); }
        } catch (DuplicateKeyException e) { throw new BizException("账单已进入其他结算批次,请刷新列表"); }
        audit.log("warehouse.settlement.generate", "settlement", s.getId(), "云仓成本快照 " + total);
        noticeService.push(warehouseId, "settlement.ready", "结算单待核对", "批次 " + s.getBatchNo() + " 应付 " + total, "settlement", s.getId());
        return s;
    }

    public MktWarehouseSettlement requireOwned(Long id, Long warehouseId) {
        var s = settlementMapper.selectById(id);
        if (s == null || (warehouseId != null && !Objects.equals(s.getWarehouseId(), warehouseId))) throw new BizException(403, "结算单不存在或不属于此云仓");
        return s;
    }
    @Transactional public void confirm(Long id, Long warehouseId) {
        var s = requireOwned(id, warehouseId); assertNotFrozen(s);
        if (change(id, warehouseId, PENDING, CONFIRMED, null)) noticeService.push(warehouseId, "settlement.confirmed", "结算单已确认", "结算单 #" + id + " 已确认", "settlement", id);
    }
    @Transactional public void dispute(Long id, Long warehouseId, String reason) {
        requireOwned(id, warehouseId); validateReason(reason);
        if (change(id, warehouseId, PENDING, DISPUTED, reason)) noticeService.push(warehouseId, "settlement.disputed", "结算争议已提交", reason, "settlement", id);
    }
    @Transactional public void resolve(Long id, String reason) {
        validateReason(reason); var s = requireOwned(id, null); assertNotFrozen(s);
        if (!Set.of(DISPUTED, FROZEN).contains(s.getStatus())) throw new BizException("当前结算单无需处理争议或冻结");
        change(id, s.getWarehouseId(), s.getStatus(), PENDING, reason);
        audit.log("warehouse.settlement.resolve", "settlement", id, reason);
    }
    private boolean change(Long id, Long warehouseId, int from, int to, String reason) {
        int n = settlementMapper.update(null, new LambdaUpdateWrapper<MktWarehouseSettlement>().eq(MktWarehouseSettlement::getId, id)
                .eq(MktWarehouseSettlement::getWarehouseId, warehouseId).eq(MktWarehouseSettlement::getStatus, from)
                .set(MktWarehouseSettlement::getStatus, to).set(reason != null, MktWarehouseSettlement::getFrozenReason, reason));
        if (n == 0) { var s = requireOwned(id, warehouseId); if (Integer.valueOf(to).equals(s.getStatus())) return false; throw new BizException("结算单状态已变化"); }
        return true;
    }
    @Transactional public void pay(Long id, String payNo, String payProof, BigDecimal amount, String operator) {
        var s = requireOwned(id, null); assertNotFrozen(s);
        if (payNo == null || payNo.isBlank() || payNo.length() > 64) throw new BizException("付款流水号须为1–64字");
        if (amount == null || amount.compareTo(s.getAmount()) != 0) throw new BizException("实付金额须与本结算单一致");
        if (Integer.valueOf(PAID).equals(s.getStatus())) {
            if (Objects.equals(payNo, s.getPayNo()) && Objects.equals(payProof, s.getPayProof())) return;
            throw new BizException("结算单已付款且凭证不一致");
        }
        proofs.require(payProof, "mkt_settlement", id);
        try {
            int n = settlementMapper.update(null, new LambdaUpdateWrapper<MktWarehouseSettlement>().eq(MktWarehouseSettlement::getId, id)
                    .eq(MktWarehouseSettlement::getStatus, CONFIRMED).set(MktWarehouseSettlement::getStatus, PAID)
                    .set(MktWarehouseSettlement::getPayNo, payNo).set(MktWarehouseSettlement::getPayProof, payProof)
                    .set(MktWarehouseSettlement::getPaidAt, LocalDateTime.now()).set(MktWarehouseSettlement::getPaidBy, operator));
            if (n != 1) throw new BizException("仅云仓已确认且无争议的结算单可登记付款");
        } catch (DuplicateKeyException e) { throw new BizException("该付款流水号已用于其他结算单"); }
        audit.log("warehouse.settlement.pay", "settlement", id, "线下付款 " + payNo + " 金额 " + amount);
        noticeService.push(s.getWarehouseId(), "settlement.paid", "结算单已付款", "批次 " + s.getBatchNo() + " 已登记线下付款 " + amount, "settlement", id);
    }

    /** Finance records a real platform-fee receipt; direct-sign customer turnover is never treated as park income. */
    @Transactional public MktDirectSignPayment recordPayment(Long contractId, Long warehouseId, String paymentNo, BigDecimal amount, String proof) {
        if (contractId == null || warehouseId == null || paymentNo == null || paymentNo.isBlank() || paymentNo.length() > 64)
            throw new BizException("直签到账参数不完整");
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) throw new BizException("到账金额须为正数且最多两位小数");
        var c = contractMapper.selectById(contractId);
        if (c == null || !Objects.equals(c.getWarehouseId(), warehouseId) || !Integer.valueOf(2).equals(c.getSignMode())
                || c.getStatus() == null || !Set.of(4, 5, 6).contains(c.getStatus()) || c.getPartnerId() == null)
            throw new BizException("仅生效且有成交伙伴的本云仓直签合同可登记平台费");
        MktDirectSignPayment old = paymentMapper.selectOne(new LambdaQueryWrapper<MktDirectSignPayment>().eq(MktDirectSignPayment::getPaymentNo, paymentNo).last("limit 1"));
        if (old != null) {
            if (!Objects.equals(old.getContractId(), contractId) || !Objects.equals(old.getWarehouseId(), warehouseId)
                    || old.getAmount().compareTo(amount) != 0 || !Objects.equals(old.getPayProof(), proof)) throw new BizException("该流水号已用于其他到账记录");
            return old;
        }
        proofs.require(proof, "mkt_direct_payment", contractId);
        var grade = gradeMapper.selectOne(new LambdaQueryWrapper<MktCustomerGrade>().eq(MktCustomerGrade::getCode, c.getGrade()).last("limit 1"));
        if (grade == null || grade.getErpTotalRate() == null || grade.getErpTotalRate().signum() < 0 || grade.getErpTotalRate().compareTo(new BigDecimal("100")) > 0)
            throw new BizException("合同评级佣金比例未配置");
        MktDirectSignPayment p = new MktDirectSignPayment(); p.setContractId(contractId); p.setWarehouseId(warehouseId); p.setPaymentNo(paymentNo);
        p.setAmount(amount); p.setStatus(1); p.setPaidAt(LocalDateTime.now()); p.setProjectId(c.getProjectId()); p.setPayProof(proof);
        try { paymentMapper.insert(p); } catch (DuplicateKeyException e) { throw new BizException("到账流水号已存在,请刷新核对"); }
        var order = commissionService.createAndSplitInTransaction(new MktCommissionService.CommissionEvent(
                MktCommissionService.SOURCE_PLATFORM_FEE, paymentNo, contractId, c.getCustomerId(), c.getPartnerId(), c.getGrade(),
                grade.getErpTotalRate(), amount, PoolCalculator.ratePool(amount, grade.getErpTotalRate()), LocalDateTime.now(), null, c.getProjectId(), warehouseId));
        commissionService.unfreezeByOrderInTransaction(order.getId());
        for (var bonus : orderMapper.selectList(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getSourceType, MktCommissionService.SOURCE_CONTRACT_BONUS)
                .eq(MktReferralOrder::getSourceId, contractId).eq(MktReferralOrder::getStatus, MktCommissionService.ORDER_CONFIRMED)))
            commissionService.unfreezeByOrderInTransaction(bonus.getId());
        int started = contractMapper.update(null, new LambdaUpdateWrapper<MktServiceContract>()
                .eq(MktServiceContract::getId, contractId).eq(MktServiceContract::getStatus, 4)
                .set(MktServiceContract::getStatus, 5));
        if (started > 0) audit.log("contract.perform", "service_contract", contractId, "首笔直签平台费实际到账");
        audit.log("warehouse.platform.receive", "service_contract", contractId, "平台费到账 " + paymentNo + " 金额 " + amount);
        return p;
    }
    public MktDirectSignPayment recordPayment(Long contractId, Long warehouseId, String paymentNo, BigDecimal amount) { throw new BizException("请上传平台费到账凭证"); }

    private void assertNotFrozen(MktWarehouseSettlement s) {
        var snap = snapshot(s.getWarehouseId(), s.getPeriodStart(), s.getPeriodEnd());
        if (snap != null && Integer.valueOf(1).equals(snap.getFrozen())) throw new BizException("对账仍处于冻结状态,请先解决对账差异");
    }
    private MktErpReconcileSnapshot snapshot(Long warehouseId, LocalDate start, LocalDate end) {
        return reconcileMapper.selectOne(new LambdaQueryWrapper<MktErpReconcileSnapshot>().eq(MktErpReconcileSnapshot::getWarehouseId, warehouseId)
                .eq(MktErpReconcileSnapshot::getPeriodStart, start).eq(MktErpReconcileSnapshot::getPeriodEnd, end).last("limit 1"));
    }
    private BigDecimal rate(String model, String field) {
        try { var n = json.readTree(model); var v = n.get(field); if (v == null) return BigDecimal.ZERO;
            if (!v.isNumber() || v.decimalValue().signum() < 0) throw new IllegalArgumentException(); return v.decimalValue(); }
        catch (Exception e) { throw new BizException("云仓费率格式不合法,请配置非负单票/按件金额"); }
    }
    private static void validateReason(String reason) { if (reason == null || reason.isBlank() || reason.length() > 500) throw new BizException("处理说明必填且不能超过500字"); }
}
