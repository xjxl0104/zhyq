package com.zhyq.park.marketing.finance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktReferralOrder;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.entity.MktServiceFeeBill;
import com.zhyq.park.marketing.entity.MktServiceFeeBillLine;
import com.zhyq.park.marketing.mapper.MktReferralOrderMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.mapper.MktServiceFeeBillLineMapper;
import com.zhyq.park.marketing.mapper.MktServiceFeeBillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MktServiceFeeBillService {
    public static final int DRAFT = 1, PENDING_CONFIRM = 2, CONFIRMED = 3, SETTLED = 4, DISPUTED = 5;
    private final MktServiceFeeBillMapper billMapper;
    private final MktServiceFeeBillLineMapper lineMapper;
    private final MktServiceContractMapper contractMapper;
    private final MktReferralOrderMapper orderMapper;

    @Transactional
    public MktServiceFeeBill generate(Long contractId, LocalDate start, LocalDate end) {
        if (contractId == null || start == null || end == null || end.isBefore(start)) throw new BizException("服务费账期无效");
        String key = "contract:" + contractId + ":service:" + start;
        MktServiceFeeBill existing = billMapper.selectOne(new LambdaQueryWrapper<MktServiceFeeBill>().eq(MktServiceFeeBill::getBillingKey, key).last("limit 1"));
        if (existing != null) return existing;
        MktServiceContract contract = contractMapper.selectById(contractId);
        if (contract == null) throw new BizException("服务合同不存在");
        List<MktReferralOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getWarehouseId, contract.getWarehouseId()).eq(MktReferralOrder::getSourceType, 2)
                .eq(MktReferralOrder::getStatus, 2).ge(MktReferralOrder::getEventTime, start.atStartOfDay())
                .lt(MktReferralOrder::getEventTime, end.plusDays(1).atStartOfDay()));
        BigDecimal total = orders.stream().map(MktReferralOrder::getServiceFee).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        MktServiceFeeBill bill = new MktServiceFeeBill(); bill.setContractId(contractId); bill.setWarehouseId(contract.getWarehouseId());
        bill.setPeriodStart(start); bill.setPeriodEnd(end); bill.setBillingKey(key); bill.setStatus(PENDING_CONFIRM); bill.setAmount(total); bill.setProjectId(contract.getProjectId());
        try { billMapper.insert(bill); } catch (DuplicateKeyException duplicate) { return billMapper.selectOne(new LambdaQueryWrapper<MktServiceFeeBill>().eq(MktServiceFeeBill::getBillingKey, key).last("limit 1")); }
        for (MktReferralOrder order : orders) {
            MktServiceFeeBillLine line = new MktServiceFeeBillLine(); line.setBillId(bill.getId()); line.setReferralOrderId(order.getId()); line.setSourceType(order.getSourceType()); line.setSourceNo(order.getSourceNo()); line.setAmount(order.getServiceFee()); line.setSnapshotJson("{\"serviceFee\":" + order.getServiceFee() + "}"); lineMapper.insert(line);
        }
        return bill;
    }

    @Transactional public void confirm(Long id) { change(id, PENDING_CONFIRM, CONFIRMED, null); }
    @Transactional public void dispute(Long id, String reason) { if (reason == null || reason.isBlank()) throw new BizException("争议原因必填"); change(id, PENDING_CONFIRM, DISPUTED, reason); }
    private void change(Long id, int from, int to, String reason) {
        int n = billMapper.update(null, new LambdaUpdateWrapper<MktServiceFeeBill>().eq(MktServiceFeeBill::getId, id).eq(MktServiceFeeBill::getStatus, from).set(MktServiceFeeBill::getStatus, to).set(reason != null, MktServiceFeeBill::getDisputeReason, reason));
        if (n == 0) { MktServiceFeeBill current = billMapper.selectById(id); if (current != null && current.getStatus() == to) return; throw new BizException("账单状态已变化"); }
    }
}
