package com.zhyq.park.marketing.open;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseErp;
import com.zhyq.park.marketing.mapper.MktWarehouseErpMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.service.MktWarehouseOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/** Connection health comes only from authenticated requests, never from order volume or anonymous ping. */
@Service
@RequiredArgsConstructor
public class ErpConnectionService {
    public static final int HEARTBEAT_TIMEOUT_HOURS = 6;
    private final MktWarehouseMapper warehouseMapper;
    private final MktWarehouseErpMapper erpMapper;

    /** Return false if credentials changed while the request was being authenticated. */
    @Transactional
    public boolean recordHeartbeat(MktWarehouseErp verified, LocalDateTime now) {
        MktWarehouse wh = warehouseMapper.selectForUpdate(verified.getWarehouseId());
        if (wh == null) return false;
        MktWarehouseErp current = erpMapper.selectById(verified.getId());
        if (!sameCredential(verified, current)) return false;
        erpMapper.update(null, new LambdaUpdateWrapper<MktWarehouseErp>()
                .eq(MktWarehouseErp::getId, current.getId()).set(MktWarehouseErp::getLastHeartbeatAt, now));
        current.setLastHeartbeatAt(now);
        verified.setLastHeartbeatAt(now);
        applyStatus(wh, statusOf(current, now));
        return true;
    }

    /** Refresh using the current credential under the same lock used by credential configuration. */
    @Transactional
    public int refresh(MktWarehouseErp credential, LocalDateTime now) {
        if (credential == null) return 0;
        MktWarehouse wh = warehouseMapper.selectForUpdate(credential.getWarehouseId());
        if (wh == null) return 0;
        MktWarehouseErp current = erpMapper.selectById(credential.getId());
        if (current != null) copyConnection(current, credential);
        return applyStatus(wh, statusOf(current, now));
    }

    /** Call after persisting a configuration change. A fresh signed request is then mandatory. */
    @Transactional
    public void invalidate(MktWarehouseErp credential) {
        MktWarehouse wh = warehouseMapper.selectForUpdate(credential.getWarehouseId());
        if (wh == null) throw new BizException("云仓不存在");
        MktWarehouseErp current = erpMapper.selectById(credential.getId());
        if (current == null) throw new BizException("ERP 凭证不存在");
        int nextRevision = revision(current) + 1;
        erpMapper.update(null, new LambdaUpdateWrapper<MktWarehouseErp>()
                .eq(MktWarehouseErp::getId, current.getId())
                .set(MktWarehouseErp::getLastHeartbeatAt, null)
                .set(MktWarehouseErp::getConnectionRevision, nextRevision));
        current.setLastHeartbeatAt(null);
        current.setConnectionRevision(nextRevision);
        copyConnection(current, credential);
        applyStatus(wh, MktWarehouseOnboardingService.ERP_NONE);
    }

    public static int statusOf(MktWarehouseErp credential, LocalDateTime now) {
        if (credential == null || !Integer.valueOf(1).equals(credential.getStatus())
                || (!Integer.valueOf(1).equals(credential.getEnv()) && !Integer.valueOf(2).equals(credential.getEnv()))
                || credential.getLastHeartbeatAt() == null) return MktWarehouseOnboardingService.ERP_NONE;
        if (credential.getLastHeartbeatAt().isBefore(now.minusHours(HEARTBEAT_TIMEOUT_HOURS)))
            return MktWarehouseOnboardingService.ERP_DISCONNECTED;
        return Integer.valueOf(2).equals(credential.getEnv())
                ? MktWarehouseOnboardingService.ERP_LIVE : MktWarehouseOnboardingService.ERP_SANDBOX;
    }

    private int applyStatus(MktWarehouse warehouse, int status) {
        if (Objects.equals(warehouse.getErpStatus(), status)) return 0;
        return warehouseMapper.update(null, new LambdaUpdateWrapper<MktWarehouse>()
                .eq(MktWarehouse::getId, warehouse.getId()).set(MktWarehouse::getErpStatus, status));
    }

    private static boolean sameCredential(MktWarehouseErp before, MktWarehouseErp current) {
        return current != null && Integer.valueOf(1).equals(current.getStatus())
                && Objects.equals(current.getWarehouseId(), before.getWarehouseId())
                && Objects.equals(current.getAppId(), before.getAppId())
                && Objects.equals(current.getSecretEnc(), before.getSecretEnc())
                && Objects.equals(current.getEnv(), before.getEnv())
                && Objects.equals(current.getStatus(), before.getStatus())
                && revision(current) == revision(before);
    }

    private static int revision(MktWarehouseErp credential) {
        return credential.getConnectionRevision() == null ? 0 : credential.getConnectionRevision();
    }

    private static void copyConnection(MktWarehouseErp source, MktWarehouseErp target) {
        target.setEnv(source.getEnv()); target.setStatus(source.getStatus());
        target.setLastHeartbeatAt(source.getLastHeartbeatAt()); target.setConnectionRevision(source.getConnectionRevision());
    }
}
