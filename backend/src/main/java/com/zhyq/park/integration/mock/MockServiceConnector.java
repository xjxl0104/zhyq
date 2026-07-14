package com.zhyq.park.integration.mock;

import com.zhyq.park.integration.ServiceConnector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 服务连接器 Mock 实现(批次① 地基,默认兜底)。
 *
 * <p>platform="mock":一律返回成功,用于短信/推送/支付等能力在无真实通道时的联调占位。
 * 真实通道实现新增 {@code @Component} 覆盖对应 platform 即可,不动调用方。</p>
 */
@Slf4j
@Component
public class MockServiceConnector implements ServiceConnector {

    @Override
    public String platform() {
        return "mock";
    }

    @Override
    public String capability() {
        return "generic";
    }

    @Override
    public InvokeResult invoke(String action, String payloadJson) {
        log.info("[mock-service] 调用 {} payload={}", action, payloadJson);
        return InvokeResult.ok("{\"mock\":true}");
    }
}
