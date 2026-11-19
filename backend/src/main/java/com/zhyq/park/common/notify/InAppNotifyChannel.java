package com.zhyq.park.common.notify;

import com.zhyq.park.system.entity.MsgRecord;
import com.zhyq.park.system.mapper.MsgRecordMapper;
import com.zhyq.park.system.mapper.SysUserMapper;
import com.zhyq.park.todo.entity.Todo;
import com.zhyq.park.todo.mapper.TodoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class InAppNotifyChannel implements NotifyChannel {
    private final SysUserMapper users;
    private final MsgRecordMapper messages;
    private final TodoMapper todos;

    public String channel() { return "inapp"; }

    @Transactional
    public boolean send(NotifyMessage message) {
        if (message.toRefId() == null) return false;
        var user = users.selectById(message.toRefId());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) return false;
        var record = new MsgRecord();
        record.setTenantId(user.getTenantId());
        record.setReceiver(user.getUsername());
        record.setChannel(channel());
        record.setTemplateCode(message.bizType());
        record.setContent(message.title() + " — " + message.content());
        record.setStatus(1);
        record.setSendTime(LocalDateTime.now());
        messages.insert(record);
        var todo = new Todo();
        todo.setTenantId(user.getTenantId());
        todo.setOwner(user.getUsername());
        todo.setTitle(message.content());
        todo.setBizType(message.bizType());
        todo.setBizId(message.bizId());
        todo.setStatus(1);
        todos.insert(todo);
        return true;
    }
}
