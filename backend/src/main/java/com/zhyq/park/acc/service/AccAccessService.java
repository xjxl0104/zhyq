package com.zhyq.park.acc.service;

import com.zhyq.park.acc.entity.AccAccessRecord;
import com.zhyq.park.acc.mapper.AccAccessRecordMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.integration.ConnectorRegistry;
import com.zhyq.park.integration.DeviceConnector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 门禁通行记录服务(#21)。只增不改的流水,无状态机。
 *
 * <p>门禁硬件走 #2 适配器层 {@link ConnectorRegistry} 的 Mock {@link DeviceConnector}:
 * 放行(result=1)且方向为进时,尝试向设备下发"开门"指令 —— 本期 Mock no-op,失败不影响落记录。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccAccessService {

    public static final int DIR_IN = 1;   // 进
    public static final int DIR_OUT = 2;  // 出
    public static final int RESULT_PASS = 1;   // 放行
    public static final int RESULT_DENY = 2;   // 拒绝

    private final AccAccessRecordMapper accessMapper;
    private final ConnectorRegistry connectorRegistry;

    /**
     * 登记一条通行流水(控制器入口,接实体)。
     *
     * @return 新记录 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long record(AccAccessRecord rec) {
        if (rec == null) {
            throw new BizException("通行记录不能为空");
        }
        return record(rec.getGateCode(), rec.getSpaceId(), rec.getPersonType(),
                rec.getPersonRef(), rec.getDirection(), rec.getResult());
    }

    /**
     * 登记一条通行流水;放行且进场时尝试调 Mock 设备开门(no-op,不阻断落记录)。
     *
     * @return 新记录 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long record(String gateCode, Long spaceId, String personType, String personRef,
                       Integer direction, Integer result) {
        if (gateCode == null || gateCode.isBlank()) {
            throw new BizException("门/闸机编号必填");
        }
        if (direction == null || (direction != DIR_IN && direction != DIR_OUT)) {
            throw new BizException("方向非法(1进 2出)");
        }
        if (result == null || (result != RESULT_PASS && result != RESULT_DENY)) {
            throw new BizException("放行结果非法(1放行 2拒绝)");
        }

        AccAccessRecord rec = new AccAccessRecord();
        rec.setGateCode(gateCode);
        rec.setSpaceId(spaceId);
        rec.setPersonType((personType == null || personType.isBlank()) ? "unknown" : personType);
        rec.setPersonRef(personRef);
        rec.setDirection(direction);
        rec.setResult(result);
        rec.setAccessTime(LocalDateTime.now());
        accessMapper.insert(rec);

        if (result == RESULT_PASS && direction == DIR_IN) {
            tryOpenGate(gateCode);
        }
        return rec.getId();
    }

    /** 通过 #2 适配器取 Mock 设备连接器下发开门指令;Mock 为 no-op,任何异常吞掉不阻断记录落库。 */
    private void tryOpenGate(String gateCode) {
        try {
            DeviceConnector connector = connectorRegistry.device("mock");
            connector.sendCommand(new DeviceConnector.DeviceCommand(gateCode, "open", null));
        } catch (Exception e) {
            log.warn("门禁开门指令下发失败(Mock,忽略),gateCode={}", gateCode, e);
        }
    }
}
