package com.zhyq.park.marketing.open;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktErpDeadLetter;
import com.zhyq.park.marketing.entity.MktErpEventInbox;
import com.zhyq.park.marketing.entity.MktErpReconcileSnapshot;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseErp;
import com.zhyq.park.marketing.mapper.MktErpDeadLetterMapper;
import com.zhyq.park.marketing.mapper.MktErpEventInboxMapper;
import com.zhyq.park.marketing.mapper.MktErpReconcileSnapshotMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseErpMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.service.MktWarehouseOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ErpReliabilityService {
    private static final int MAX_ATTEMPTS = 5;
    private final MktWarehouseErpMapper erpMapper;
    private final MktWarehouseMapper warehouseMapper;
    private final MktErpEventInboxMapper inboxMapper;
    private final MktErpDeadLetterMapper deadMapper;
    private final MktErpReconcileSnapshotMapper reconcileMapper;

    @Transactional
    public int heartbeat(LocalDateTime now) {
        int changed = 0;
        for (MktWarehouseErp erp : erpMapper.selectList(new LambdaQueryWrapper<>())) {
            LocalDateTime last = erp.getLastSyncAt();
            boolean stale = last == null || last.isBefore(now.minusHours(6));
            MktWarehouse wh = warehouseMapper.selectById(erp.getWarehouseId());
            if (wh == null) continue;
            if (stale && !Integer.valueOf(MktWarehouseOnboardingService.ERP_DISCONNECTED).equals(wh.getErpStatus())) {
                changed += warehouseMapper.update(null, new LambdaUpdateWrapper<MktWarehouse>().eq(MktWarehouse::getId, wh.getId())
                        .set(MktWarehouse::getErpStatus, MktWarehouseOnboardingService.ERP_DISCONNECTED));
            } else if (!stale && Integer.valueOf(MktWarehouseOnboardingService.ERP_DISCONNECTED).equals(wh.getErpStatus())) {
                int status = Integer.valueOf(2).equals(erp.getEnv()) ? MktWarehouseOnboardingService.ERP_LIVE : MktWarehouseOnboardingService.ERP_SANDBOX;
                changed += warehouseMapper.update(null, new LambdaUpdateWrapper<MktWarehouse>().eq(MktWarehouse::getId, wh.getId())
                        .set(MktWarehouse::getErpStatus, status));
            }
        }
        return changed;
    }

    @Transactional
    public int retryDue(LocalDateTime now) {
        List<MktErpEventInbox> rows = inboxMapper.selectList(new LambdaQueryWrapper<MktErpEventInbox>()
                .eq(MktErpEventInbox::getStatus, "RETRY").le(MktErpEventInbox::getNextRetryAt, now).last("limit 200"));
        int moved = 0;
        for (MktErpEventInbox row : rows) {
            int attempts = row.getAttempts() == null ? 0 : row.getAttempts();
            if (attempts >= MAX_ATTEMPTS) {
                row.setStatus("DEAD"); row.setLastError("超过最大重试次数"); inboxMapper.updateById(row);
                MktErpDeadLetter dl = new MktErpDeadLetter(); dl.setInboxId(row.getId()); dl.setWarehouseId(row.getWarehouseId());
                dl.setEventId(row.getEventId()); dl.setOrderNo(row.getOrderNo()); dl.setErrorCode("MAX_RETRY"); dl.setErrorMessage("超过最大重试次数"); dl.setAttempts(attempts); dl.setProjectId(row.getProjectId());
                try { deadMapper.insert(dl); } catch (DuplicateKeyException ignored) { }
                moved++;
            } else {
                row.setStatus("RECEIVED"); row.setNextRetryAt(null); inboxMapper.updateById(row); moved++;
            }
        }
        return moved;
    }

    @Transactional
    public void replay(Long inboxId, String operator) {
        MktErpEventInbox row = inboxMapper.selectById(inboxId);
        if (row == null) throw new BizException("ERP 事件不存在");
        if ("PROCESSED".equals(row.getStatus())) return;
        row.setStatus("RECEIVED"); row.setAttempts(0); row.setNextRetryAt(null); row.setLastError("人工重放:" + operator);
        inboxMapper.updateById(row);
    }

    @Transactional
    public MktErpReconcileSnapshot snapshot(Long warehouseId, LocalDate start, LocalDate end,
                                            BigDecimal expected, BigDecimal actual, Long projectId) {
        if (start == null || end == null || end.isBefore(start)) throw new BizException("对账周期无效");
        BigDecimal e = expected == null ? BigDecimal.ZERO : expected;
        BigDecimal a = actual == null ? BigDecimal.ZERO : actual;
        BigDecimal diff = e.subtract(a).abs();
        BigDecimal base = e.abs().max(BigDecimal.ONE);
        BigDecimal ratio = diff.divide(base, 8, RoundingMode.HALF_UP);
        MktErpReconcileSnapshot existing = reconcileMapper.selectOne(new LambdaQueryWrapper<MktErpReconcileSnapshot>()
                .eq(MktErpReconcileSnapshot::getWarehouseId, warehouseId).eq(MktErpReconcileSnapshot::getPeriodStart, start).eq(MktErpReconcileSnapshot::getPeriodEnd, end).last("limit 1"));
        if (existing != null) return existing;
        MktErpReconcileSnapshot s = new MktErpReconcileSnapshot(); s.setWarehouseId(warehouseId); s.setPeriodStart(start); s.setPeriodEnd(end);
        s.setExpectedAmount(e); s.setActualAmount(a); s.setDiffAmount(diff); s.setDiffRatio(ratio); s.setFrozen(ratio.compareTo(new BigDecimal("0.02")) >= 0 ? 1 : 0);
        s.setReason(s.getFrozen() == 1 ? "对账差异达到 2%" : null); s.setProjectId(projectId); reconcileMapper.insert(s); return s;
    }
}
