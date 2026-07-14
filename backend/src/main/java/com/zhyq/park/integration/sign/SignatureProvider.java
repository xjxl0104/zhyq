package com.zhyq.park.integration.sign;

/**
 * 电子签章服务(批次② 预留接口位,P2 池条目,只留位不实现)。
 *
 * <p>合同电子签的统一契约:发起签署 → 查询状态 → 下载已签文件。本轮仅落地接口 + Mock,
 * 真实对接(e签宝/法大大等)后续替换实现,不动调用方。与 {@code integration} 适配器层同址。</p>
 */
public interface SignatureProvider {

    /** 平台标识(如 "mock"/"esign"/"fadada")。 */
    String platform();

    /** 发起签署,返回签署单据。 */
    SignResult initiate(SignRequest request);

    /** 查询签署状态。 */
    SignStatus query(String signFlowId);

    /** 签署发起入参(文件与签署方用引用/JSON 承载,避免绑定具体文件存储)。 */
    record SignRequest(String bizType, Long bizId, String fileRef, String signersJson) {}

    /** 签署发起结果。 */
    record SignResult(boolean accepted, String signFlowId, String message) {}

    /** 签署状态:PENDING/SIGNING/COMPLETED/REJECTED/EXPIRED。 */
    enum SignStatus { PENDING, SIGNING, COMPLETED, REJECTED, EXPIRED }
}
