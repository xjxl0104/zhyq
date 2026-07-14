package com.zhyq.park.common.notify;

/**
 * 通知渠道(批次② 统一通知出口)。
 *
 * <p>站内信/短信/微信等各实现本接口,{@link NotificationService} 按 {@link #channel()} 路由。
 * 现有 {@code tenant_message}/{@code fin_pay_notice} 的翻状态逻辑保持不动 —— 本层是"新增的统一出口",
 * 供批次② 的审批/催缴/SLA 等主动发通知时调用。真实通道(阿里短信/微信模板消息)后续替换实现,
 * 不动调用方。默认 Mock 实现只记日志,绝不影响业务主流程。</p>
 */
public interface NotifyChannel {

    /** 渠道标识:"inapp"(站内信)/"sms"(短信)/"wechat"(微信)。 */
    String channel();

    /** 发送一条通知。实现须吞异常、失败不得向上抛,避免拖垮业务主流程。返回是否受理成功。 */
    boolean send(NotifyMessage message);

    /**
     * 归一化通知消息。
     * @param channel   目标渠道
     * @param toRefId   接收方业务 id(如 tenantRefId / userId,由调用方按渠道语义填)
     * @param title     标题
     * @param content   正文
     * @param bizType   关联业务类型(如 contract/bill/workorder),便于回链与审计
     * @param bizId     关联业务 id
     */
    record NotifyMessage(String channel, Long toRefId, String title, String content,
                         String bizType, Long bizId) {}
}
