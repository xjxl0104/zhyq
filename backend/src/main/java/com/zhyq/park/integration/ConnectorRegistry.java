package com.zhyq.park.integration;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 连接器注册中心(批次① 地基,第三方对接适配器层入口)。
 *
 * <p>Spring 启动时注入全部 {@link DeviceConnector}/{@link ServiceConnector} 实现,按 platform 建索引。
 * 调用方按 {@code iot_vendor.platform} 取对应连接器;取不到时回退到 "mock" 兜底实现,
 * 保证"未配置真实平台也能跑通"。真实平台只需新增 {@code @Component} 即被自动纳管,Registry 不改。</p>
 */
@Component
public class ConnectorRegistry {

    private static final String FALLBACK = "mock";

    private final Map<String, DeviceConnector> deviceConnectors;
    private final Map<String, ServiceConnector> serviceConnectors;

    public ConnectorRegistry(List<DeviceConnector> devices, List<ServiceConnector> services) {
        this.deviceConnectors = devices.stream()
                .collect(Collectors.toMap(DeviceConnector::platform, Function.identity(), (a, b) -> a));
        this.serviceConnectors = services.stream()
                .collect(Collectors.toMap(ServiceConnector::platform, Function.identity(), (a, b) -> a));
    }

    /** 取设备连接器;platform 未注册则回退 mock;mock 也无则抛出。 */
    public DeviceConnector device(String platform) {
        DeviceConnector c = deviceConnectors.get(normalize(platform));
        if (c == null) c = deviceConnectors.get(FALLBACK);
        if (c == null) throw new IllegalStateException("无可用设备连接器,platform=" + platform);
        return c;
    }

    /** 取服务连接器;platform 未注册则回退 mock;mock 也无则抛出。 */
    public ServiceConnector service(String platform) {
        ServiceConnector c = serviceConnectors.get(normalize(platform));
        if (c == null) c = serviceConnectors.get(FALLBACK);
        if (c == null) throw new IllegalStateException("无可用服务连接器,platform=" + platform);
        return c;
    }

    private String normalize(String platform) {
        return (platform == null || platform.isBlank()) ? FALLBACK : platform.trim();
    }
}
