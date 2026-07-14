package com.zhyq.park.common.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 统一通知服务(批次② 统一通知出口入口)。
 *
 * <p>Spring 启动注入全部 {@link NotifyChannel},按 channel 建索引。调用方只依赖本服务,
 * 不感知具体渠道实现;渠道缺失或发送异常一律吞掉并记日志,绝不影响业务主流程。
 * 本服务是"新增的统一出口",不改动 tenant_message / fin_pay_notice 的既有逻辑。</p>
 */
@Slf4j
@Service
public class NotificationService {

    private final Map<String, NotifyChannel> channels;

    public NotificationService(List<NotifyChannel> channelList) {
        this.channels = channelList.stream()
                .collect(Collectors.toMap(NotifyChannel::channel, Function.identity(), (a, b) -> a));
    }

    /** 按消息自带的 channel 发送;渠道不存在则记日志跳过。 */
    public boolean send(NotifyChannel.NotifyMessage message) {
        NotifyChannel ch = channels.get(message.channel());
        if (ch == null) {
            log.warn("[notify] 无渠道实现 channel={},已跳过 title={}", message.channel(), message.title());
            return false;
        }
        try {
            return ch.send(message);
        } catch (Exception e) {
            log.warn("[notify] 发送失败(已吞异常) channel={} title={}", message.channel(), message.title(), e);
            return false;
        }
    }

    /** 便捷方法:站内信。 */
    public boolean sendInApp(Long toRefId, String title, String content, String bizType, Long bizId) {
        return send(new NotifyChannel.NotifyMessage("inapp", toRefId, title, content, bizType, bizId));
    }
}
