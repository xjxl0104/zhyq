package com.zhyq.park.marketing.finance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service @RequiredArgsConstructor
public class MktServiceFeeBillService {
    public static final int DRAFT = 1, PENDING_CONFIRM = 2, CONFIRMED = 3, SETTLED = 4, DISPUTED = 5;
    private final MktServiceFeeBillMapper billMapper;
    private final MktServiceFeeBillLineMapper lineMapper;
    private final MktServiceContractMapper contractMapper;
    private final MktReferralOrderMapper orderMapper;
    private final MktNoticeService noticeService;
    private final MktCommissionService commissionService;
    private final MktPaymentProofService proofs;
    private final MktAuditService audit;

    @Transactional
    public MktServiceFeeBill generate(Long contractId, LocalDate start, LocalDate end) {
        validatePeriod(start, end);
        if (contractId == null) throw new BizException("请选择服务合同");
        // Serialize billing for one contract; a replay may append only orders not yet billed.
        MktServiceContract contract = contractMapper.selectOne(new LambdaQueryWrapper<MktServiceContract>()
                .eq(MktServiceContract::getId, contractId).last("FOR UPDATE"));
        String key = "contract:" + contractId + ":service:" + start;
        MktServiceFeeBill existing = billMapper.selectOne(new LambdaQueryWrapper<MktServiceFeeBill>()
                .eq(MktServiceFeeBill::getContractId, contractId).eq(MktServiceFeeBill::getPeriodStart, start)
                .eq(MktServiceFeeBill::getPeriodEnd, end).orderByDesc(MktServiceFeeBill::getId).last("limit 1 FOR UPDATE"));
        if (contract == null || contract.getWarehouseId() == null || contract.getStatus() == null || !List.of(4, 5, 6, 7).contains(contract.getStatus()))
            throw new BizException("仅生效/履约或正常到期的合同可生成账单");
        if (!Integer.valueOf(1).equals(contract.getSignMode())) throw new BizException("此入口仅用于园区签服务合同,直签请登记平台费到账");
        if (billMapper.selectCount(new LambdaQueryWrapper<MktServiceFeeBill>().eq(MktServiceFeeBill::getContractId, contractId)
                .le(MktServiceFeeBill::getPeriodStart, end).ge(MktServiceFeeBill::getPeriodEnd, start)
                .and(q -> q.ne(MktServiceFeeBill::getPeriodStart, start).or().ne(MktServiceFeeBill::getPeriodEnd, end))) > 0)
            throw new BizException("账期与已有账单重叠,请查看原账单");
        List<MktReferralOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getSourceId, contractId).eq(MktReferralOrder::getCustomerId, contract.getCustomerId())
                .eq(MktReferralOrder::getWarehouseId, contract.getWarehouseId()).eq(MktReferralOrder::getSourceType, 2)
                .eq(MktReferralOrder::getStatus, 2).ge(MktReferralOrder::getEventTime, start.atStartOfDay())
                .lt(MktReferralOrder::getEventTime, end.plusDays(1).atStartOfDay())
                .notInSql(MktReferralOrder::getId, "SELECT referral_order_id FROM crm_service_fee_bill_line WHERE deleted=0 AND referral_order_id IS NOT NULL")
                .orderByAsc(MktReferralOrder::getId).last("FOR UPDATE"));
        if (orders.isEmpty()) {
            if (existing != null) return existing;
            throw new BizException("该合同账期内没有可计费的已确认出库单");
        }
        if (existing != null) key += ":supplement:" + orders.get(0).getId();
        if (orders.stream().anyMatch(o -> o.getServiceFee() == null || o.getServiceFee().signum() <= 0))
            throw new BizException("存在未计费订单,请先核实订单服务费");
        BigDecimal total = orders.stream().map(MktReferralOrder::getServiceFee).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        MktServiceFeeBill bill = new MktServiceFeeBill(); bill.setContractId(contractId); bill.setWarehouseId(contract.getWarehouseId());
        bill.setPeriodStart(start); bill.setPeriodEnd(end); bill.setBillingKey(key); bill.setStatus(PENDING_CONFIRM); bill.setAmount(total); bill.setProjectId(contract.getProjectId());
        try { billMapper.insert(bill); }
        catch (DuplicateKeyException duplicate) { throw new BizException("该账期已由其他操作生成,请刷新列表"); }
        for (MktReferralOrder order : orders) {
            MktServiceFeeBillLine line = new MktServiceFeeBillLine(); line.setBillId(bill.getId()); line.setReferralOrderId(order.getId());
            line.setSourceType(order.getSourceType()); line.setSourceNo(order.getSourceNo()); line.setAmount(order.getServiceFee());
            line.setSnapshotJson("{\"serviceFee\":" + order.getServiceFee() + "}");
            try { lineMapper.insert(line); } catch (DuplicateKeyException e) { throw new BizException("订单已进入其他账单,本次生成已回滚"); }
        }
        audit.log("service.bill.generate", "service_bill", bill.getId(), "账期 " + start + " ~ " + end + " 金额 " + total);
        noticeService.push(bill.getWarehouseId(), "bill.ready", "服务费账单待确认", "账单 #" + bill.getId() + " 金额 " + total, "bill", bill.getId());
        return bill;
    }

    public static void validatePeriod(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start) || end.isAfter(start.plusYears(1))) throw new BizException("账期无效或超过一年");
    }
    public MktServiceFeeBill requireOwned(Long id, Long warehouseId) {
        MktServiceFeeBill bill = billMapper.selectById(id);
        if (bill == null || (warehouseId != null && !warehouseId.equals(bill.getWarehouseId()))) throw new BizException(403, "账单不存在或不属于此云仓");
        return bill;
    }
    @Transactional public void confirm(Long id, Long warehouseId) { requireOwned(id, warehouseId); change(id, warehouseId, PENDING_CONFIRM, CONFIRMED, null); }
    @Transactional public void dispute(Long id, Long warehouseId, String reason) { requireOwned(id, warehouseId); reason = reason(reason); change(id, warehouseId, PENDING_CONFIRM, DISPUTED, reason); }
    @Transactional public void resolve(Long id, String reason) { reason = reason(reason); change(id, null, DISPUTED, PENDING_CONFIRM, reason); }
    private void change(Long id, Long warehouseId, int from, int to, String reason) {
        int n = billMapper.update(null, new LambdaUpdateWrapper<MktServiceFeeBill>().eq(MktServiceFeeBill::getId, id)
                .eq(warehouseId != null, MktServiceFeeBill::getWarehouseId, warehouseId).eq(MktServiceFeeBill::getStatus, from)
                .set(MktServiceFeeBill::getStatus, to).set(reason != null, MktServiceFeeBill::getDisputeReason, reason));
        if (n == 0) {
            MktServiceFeeBill current = requireOwned(id, warehouseId);
            if (Integer.valueOf(to).equals(current.getStatus())) return;
            throw new BizException("账单状态已变化");
        }
        audit.log("service.bill.status", "service_bill", id, from + " → " + to + " " + (reason == null ? "" : reason));
    }
    @Transactional public void receive(Long id, String receiptNo, String receiptProof, BigDecimal amount) {
        MktServiceFeeBill b = requireOwned(id, null);
        if (receiptNo == null || receiptNo.isBlank() || receiptNo.length() > 64) throw new BizException("收款流水号须为1–64字");
        if (amount == null || amount.compareTo(b.getAmount()) != 0) throw new BizException("实际收款金额须与本账单金额一致");
        if (Integer.valueOf(SETTLED).equals(b.getStatus())) {
            if (Objects.equals(receiptNo, b.getReceiptNo()) && Objects.equals(receiptProof, b.getReceiptProof())) return;
            throw new BizException("账单已收款且凭证信息不一致");
        }
        proofs.require(receiptProof, "mkt_bill", id);
        try {
            int n = billMapper.update(null, new LambdaUpdateWrapper<MktServiceFeeBill>().eq(MktServiceFeeBill::getId, id)
                    .eq(MktServiceFeeBill::getStatus, CONFIRMED).set(MktServiceFeeBill::getStatus, SETTLED)
                    .set(MktServiceFeeBill::getReceiptNo, receiptNo).set(MktServiceFeeBill::getReceiptProof, receiptProof)
                    .set(MktServiceFeeBill::getReceivedAt, LocalDateTime.now()).set(MktServiceFeeBill::getReceivedBy, MktAuditService.currentOperator()));
            if (n != 1) throw new BizException("仅已确认且无争议账单可登记收款");
        } catch (DuplicateKeyException e) { throw new BizException("该收款流水号已用于其他账单"); }
        for (MktReferralOrder order : orderMapper.selectList(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getSourceType, MktCommissionService.SOURCE_CONTRACT_BONUS).eq(MktReferralOrder::getSourceId, b.getContractId())
                .eq(MktReferralOrder::getStatus, MktCommissionService.ORDER_CONFIRMED))) commissionService.unfreezeByOrderInTransaction(order.getId());
        int started = contractMapper.update(null, new LambdaUpdateWrapper<MktServiceContract>()
                .eq(MktServiceContract::getId, b.getContractId()).eq(MktServiceContract::getStatus, 4)
                .set(MktServiceContract::getStatus, 5));
        if (started > 0) audit.log("contract.perform", "service_contract", b.getContractId(), "首笔服务费实际到账");
        audit.log("service.bill.receive", "service_bill", id, "线下到账 " + receiptNo + " 金额 " + amount);
    }
    private static String reason(String reason) { if (reason == null || reason.isBlank() || reason.length() > 500) throw new BizException("说明必填且不能超过500字"); return reason.trim(); }
}
