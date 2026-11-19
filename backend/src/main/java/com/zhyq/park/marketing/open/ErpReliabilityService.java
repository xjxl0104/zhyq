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
    private final ErpConnectionService connectionService;

    public int heartbeat(LocalDateTime now) {
        int changed = 0;
        for (MktWarehouseErp erp : erpMapper.selectList(new LambdaQueryWrapper<>())) {
            changed += connectionService.refresh(erp, now);
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
            LambdaUpdateWrapper<MktErpEventInbox> transition = new LambdaUpdateWrapper<MktErpEventInbox>()
                    .eq(MktErpEventInbox::getId, row.getId())
                    .eq(MktErpEventInbox::getStatus, "RETRY")
                    .le(MktErpEventInbox::getNextRetryAt, now);
            if (attempts >= MAX_ATTEMPTS) {
                moved += deadLetter(row, transition, attempts, "MAX_RETRY", "超过最大重试次数");
            } else {
                moved += inboxMapper.update(null, transition.set(MktErpEventInbox::getNextRetryAt, null).set(MktErpEventInbox::getStatus, "RECEIVED"));
            }
        }
        return moved;
    }

    @Transactional
    public int deadLetter(MktErpEventInbox row, LambdaUpdateWrapper<MktErpEventInbox> transition,
                          int attempts, String code, String error) {
        String detail = error == null ? code : error.substring(0, Math.min(error.length(), 500));
        int changed = inboxMapper.update(null, transition.set(MktErpEventInbox::getStatus, "DEAD")
                .set(MktErpEventInbox::getAttempts, attempts).set(MktErpEventInbox::getLastError, detail)
                .set(MktErpEventInbox::getNextRetryAt, null));
        if (changed == 0) return 0;
        MktErpDeadLetter letter = new MktErpDeadLetter();
        letter.setInboxId(row.getId());
        letter.setEventId(row.getEventId());
        letter.setWarehouseId(row.getWarehouseId());
        letter.setOrderNo(row.getOrderNo());
        letter.setErrorCode(code);
        letter.setErrorMessage(detail);
        letter.setAttempts(attempts);
        letter.setProjectId(row.getProjectId());
        try { deadMapper.insert(letter); }
        catch (DuplicateKeyException existing) {
            deadMapper.update(null, new LambdaUpdateWrapper<MktErpDeadLetter>()
                    .eq(MktErpDeadLetter::getInboxId, row.getId()).set(MktErpDeadLetter::getErrorCode, code)
                    .set(MktErpDeadLetter::getErrorMessage, detail).set(MktErpDeadLetter::getAttempts, attempts));
        }
        return changed;
    }

    @Transactional
    public void replay(Long inboxId, String operator) {
        MktErpEventInbox row = inboxMapper.selectById(inboxId);
        if (row == null) throw new BizException("ERP 事件不存在");
        if ("PROCESSED".equals(row.getStatus())) return;
        if ("PROCESSING".equals(row.getStatus())) throw new BizException("事件正在处理，请稍后刷新");
        int changed = inboxMapper.update(null, new LambdaUpdateWrapper<MktErpEventInbox>()
                .eq(MktErpEventInbox::getId, inboxId).eq(MktErpEventInbox::getStatus, row.getStatus())
                .set(MktErpEventInbox::getStatus, "RECEIVED").set(MktErpEventInbox::getAttempts, 0)
                .set(MktErpEventInbox::getNextRetryAt, null).set(MktErpEventInbox::getProcessedAt, null)
                .set(MktErpEventInbox::getLastError, "人工重放:" + operator));
        if (changed == 0) throw new BizException("事件状态已变化，请刷新后重试");
    }

    /** Legacy amount estimates are not proof of an external reconciliation. */
    @Deprecated
    public MktErpReconcileSnapshot snapshot(Long warehouseId, LocalDate start, LocalDate end,
                                            BigDecimal expected, BigDecimal actual, Long projectId) {
        throw new BizException("旧金额估算对账已停用，请录入外部 ERP 出库单数及凭据并完成周期对账确认");
    }
}
