package com.zhyq.park.integration;

import java.util.List;

/**
 * 设备类连接器(批次① 地基,第三方对接适配器层)。
 *
 * <p>统一"设备平台"接入契约:门锁/门禁/停车/充电/摄像头/传感器等第三方设备平台,
 * 各自实现本接口;调用方只依赖接口,不感知具体平台。按 {@code iot_vendor.platform} 选择实现
 * (见 {@link ConnectorRegistry})。Mock 与真实实现同接口可切换 —— 本轮先落地 Mock,
 * 真实对接凭 {@code iot_vendor} 的 apiUrl/appKey/appSecret 后续补齐,不动调用方。</p>
 */
public interface DeviceConnector {

    /** 本连接器对应的平台标识,与 {@code iot_vendor.platform} 取值一致(如 "mock"/"hikvision"/"tuya")。 */
    String platform();

    /** 拉取该平台下的设备实时状态(真实实现调平台 API;Mock 返回构造数据)。 */
    List<DeviceStatus> pullDeviceStatus();

    /** 向设备下发指令(如开锁、抬杆)。返回是否受理成功。 */
    boolean sendCommand(DeviceCommand command);

    /** 设备实时状态(适配器归一化后的对外结构)。 */
    record DeviceStatus(String externalId, String name, String category, boolean online, String raw) {}

    /** 设备指令。 */
    record DeviceCommand(String externalId, String action, String params) {}
}
