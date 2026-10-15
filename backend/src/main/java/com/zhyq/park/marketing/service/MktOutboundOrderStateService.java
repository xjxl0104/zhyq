package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.marketing.entity.MktErpOrderState;
import com.zhyq.park.marketing.mapper.MktErpOrderStateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

/** Callers must hold the warehouse row lock for the entire order transaction. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class MktOutboundOrderStateService {
    private final MktErpOrderStateMapper stateMapper;

    public boolean isTerminal(Long warehouseId, String orderNo) {
        return stateMapper.selectOne(new LambdaQueryWrapper<MktErpOrderState>()
                .eq(MktErpOrderState::getWarehouseId, warehouseId)
                .eq(MktErpOrderState::getOrderNo, orderNo).last("limit 1 FOR UPDATE")) != null;
    }

    public void terminate(Long warehouseId, String orderNo, String event, String appId,
                          String eventId, LocalDateTime occurredAt, Long projectId) {
        if (isTerminal(warehouseId, orderNo)) return;
        MktErpOrderState state = new MktErpOrderState();
        state.setWarehouseId(warehouseId); state.setOrderNo(orderNo); state.setTerminalEvent(event);
        state.setAppId(appId); state.setEventId(eventId); state.setOccurredAt(occurredAt); state.setProjectId(projectId);
        stateMapper.insert(state);
    }
}
