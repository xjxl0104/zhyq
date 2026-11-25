package com.zhyq.park.marketing.listener;

import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.marketing.entity.MktCustomerLock;
import com.zhyq.park.marketing.mapper.MktCustomerLockMapper;
import com.zhyq.park.marketing.service.MktLockService;
import com.zhyq.park.marketing.service.MktPublicSeaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 监听外部伙伴锁客释放事件：自动将已释放且无归属的客户归集进内部专属公海池。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MktPublicSeaLockListener {

    private final MktPublicSeaService publicSeaService;
    private final MktCustomerLockMapper lockMapper;

    @EventListener
    public void onCustomerLockChanged(DomainEvent.CustomerLockChanged event) {
        if (event == null || !Integer.valueOf(MktLockService.LS_RELEASED).equals(event.status())) {
            return;
        }
        try {
            MktCustomerLock lock = event.lockId() == null ? null : lockMapper.selectById(event.lockId());
            String reason = lock == null ? null : lock.getReleasedReason();
            publicSeaService.onLockReleased(event.customerId(), reason);
        } catch (Exception e) {
            log.warn("[mkt-public-sea] 锁客释放联动归集公海异常 customerId={}: {}", event.customerId(), e.getMessage());
        }
    }
}
