package com.zhyq.park.integration;

/**
 * 服务类连接器(批次① 地基,第三方对接适配器层)。
 *
 * <p>统一"第三方服务平台"接入契约:短信/推送/支付/电子签/OCR 等外部服务,各自实现本接口。
 * 与 {@link DeviceConnector} 并列,区别在于面向"能力调用"而非"设备状态"。调用方只依赖接口,
 * 按 {@code platform} 选择实现(见 {@link ConnectorRegistry})。本轮先落地 Mock 实现。</p>
 */
public interface ServiceConnector {

    /** 平台标识(如 "mock"/"aliyun-sms"/"wechat-pay")。 */
    String platform();

    /** 该连接器支持的能力类别(如 "sms"/"push"/"pay"/"sign"/"ocr"),用于调用方选型。 */
    String capability();

    /** 调用第三方能力。请求/响应用 JSON 字符串承载,避免为每个平台定制 DTO;真实实现负责签名与协议细节。 */
    InvokeResult invoke(String action, String payloadJson);

    /** 调用结果(归一化)。 */
    record InvokeResult(boolean success, String code, String message, String dataJson) {
        public static InvokeResult ok(String dataJson) {
            return new InvokeResult(true, "0", "ok", dataJson);
        }
        public static InvokeResult fail(String code, String message) {
            return new InvokeResult(false, code, message, null);
        }
    }
}
