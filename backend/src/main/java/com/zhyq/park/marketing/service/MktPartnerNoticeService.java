package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.tenant.entity.TenantMessage;
import com.zhyq.park.tenant.mapper.TenantMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MktPartnerNoticeService {
    private final TenantMessageMapper messages;

    @Transactional
    public void push(Long promoterId, String title, String content) {
        if (promoterId == null) return;
        if (messages.selectCount(new LambdaQueryWrapper<TenantMessage>().eq(TenantMessage::getChannel,"mp")
                .eq(TenantMessage::getTenantRefId,promoterId).eq(TenantMessage::getTitle,title)
                .eq(TenantMessage::getContent,content).ge(TenantMessage::getSendTime,LocalDateTime.now().minusDays(1))) > 0) return;
        TenantMessage m = new TenantMessage(); m.setTenantRefId(promoterId); m.setChannel("mp");
        m.setTitle(title); m.setContent(content); m.setStatus(2); m.setReadFlag(0); m.setSendTime(LocalDateTime.now());
        messages.insert(m);
    }

    public void read(Long id, Long promoterId) {
        messages.update(null,new LambdaUpdateWrapper<TenantMessage>().eq(TenantMessage::getId,id)
                .eq(TenantMessage::getTenantRefId,promoterId).eq(TenantMessage::getChannel,"mp").set(TenantMessage::getReadFlag,1));
    }
}
