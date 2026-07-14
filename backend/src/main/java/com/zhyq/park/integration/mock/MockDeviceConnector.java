package com.zhyq.park.integration.mock;

import com.zhyq.park.integration.DeviceConnector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 设备连接器 Mock 实现(批次① 地基,默认兜底)。
 *
 * <p>platform="mock":当 {@code iot_vendor} 未配置真实平台时的默认连接器。
 * 返回构造的设备状态、指令一律受理成功 —— 用于联调与演示,<b>不写库、不替换 Flyway 静态种子</b>
 * (替换种子属行为变更,留待真实对接时进行)。真实平台实现新增一个 {@code @Component}
 * 并让 {@link #platform()} 返回对应标识即可被 Registry 自动纳管。</p>
 */
@Slf4j
@Component
public class MockDeviceConnector implements DeviceConnector {

    @Override
    public String platform() {
        return "mock";
    }

    @Override
    public List<DeviceStatus> pullDeviceStatus() {
        return List.of(
                new DeviceStatus("MOCK-LOCK-01", "模拟门锁01", "门锁", true, "{\"battery\":88}"),
                new DeviceStatus("MOCK-GATE-01", "模拟道闸01", "停车", true, "{\"barrier\":\"down\"}")
        );
    }

    @Override
    public boolean sendCommand(DeviceCommand command) {
        log.info("[mock-device] 受理指令 {} -> {} ({})", command.externalId(), command.action(), command.params());
        return true;
    }
}
