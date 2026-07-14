package com.zhyq.park.integration.sign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 电子签 Mock 实现(批次② 默认兜底)。
 * 发起即返回"受理成功 + 模拟签署单号",查询恒返回 COMPLETED,仅用于联调占位。
 */
@Slf4j
@Component
public class MockSignatureProvider implements SignatureProvider {

    @Override
    public String platform() {
        return "mock";
    }

    @Override
    public SignResult initiate(SignRequest request) {
        String flowId = "MOCK-SIGN-" + request.bizType() + "-" + request.bizId();
        log.info("[mock-sign] 发起签署 {} -> {}", request.bizType(), flowId);
        return new SignResult(true, flowId, "模拟签署已受理");
    }

    @Override
    public SignStatus query(String signFlowId) {
        return SignStatus.COMPLETED;
    }
}
