package com.zhyq.park.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.system.entity.MsgRecord;
import com.zhyq.park.system.entity.MsgTemplate;
import com.zhyq.park.system.mapper.MsgRecordMapper;
import com.zhyq.park.system.mapper.MsgTemplateMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "系统管理-消息中心")
@RestController
@RequestMapping("/system/message")
@RequiredArgsConstructor
public class MsgCenterController {

    private final MsgTemplateMapper templateMapper;
    private final MsgRecordMapper recordMapper;

    // ===== 消息模板 =====

    @Operation(summary = "分页查询消息模板")
    @PreAuthorize("hasAuthority('system:message:query')")
    @GetMapping("/template/page")
    public Result<PageResult<MsgTemplate>> templatePage(@RequestParam(defaultValue = "1") int pageNo,
                                                          @RequestParam(defaultValue = "10") int pageSize,
                                                          @RequestParam(required = false) String code,
                                                          @RequestParam(required = false) String name,
                                                          @RequestParam(required = false) String channel) {
        LambdaQueryWrapper<MsgTemplate> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(code), MsgTemplate::getCode, code)
          .like(StringUtils.hasText(name), MsgTemplate::getName, name)
          .eq(StringUtils.hasText(channel), MsgTemplate::getChannel, channel)
          .orderByDesc(MsgTemplate::getId);
        IPage<MsgTemplate> p = templateMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "新增消息模板")
    @PreAuthorize("hasAuthority('system:message:add')")
    @PostMapping("/template")
    public Result<Long> templateAdd(@RequestBody MsgTemplate template) {
        templateMapper.insert(template);
        return Result.ok(template.getId());
    }

    @Operation(summary = "修改消息模板")
    @PreAuthorize("hasAuthority('system:message:edit')")
    @PutMapping("/template")
    public Result<Void> templateUpdate(@RequestBody MsgTemplate template) {
        templateMapper.updateById(template);
        return Result.ok();
    }

    @Operation(summary = "删除消息模板")
    @PreAuthorize("hasAuthority('system:message:delete')")
    @DeleteMapping("/template/{id}")
    public Result<Void> templateDelete(@PathVariable Long id) {
        templateMapper.deleteById(id);
        return Result.ok();
    }

    // ===== 发送记录 =====

    @Operation(summary = "分页查询发送记录")
    @PreAuthorize("hasAuthority('system:message:query')")
    @GetMapping("/record/page")
    public Result<PageResult<MsgRecord>> recordPage(@RequestParam(defaultValue = "1") int pageNo,
                                                      @RequestParam(defaultValue = "10") int pageSize,
                                                      @RequestParam(required = false) String receiver,
                                                      @RequestParam(required = false) Integer status,
                                                      @RequestParam(required = false) String channel) {
        LambdaQueryWrapper<MsgRecord> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(receiver), MsgRecord::getReceiver, receiver)
          .eq(status != null, MsgRecord::getStatus, status)
          .eq(StringUtils.hasText(channel), MsgRecord::getChannel, channel)
          .orderByDesc(MsgRecord::getId);
        IPage<MsgRecord> p = recordMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "删除发送记录")
    @PreAuthorize("hasAuthority('system:message:delete')")
    @DeleteMapping("/record/{id}")
    public Result<Void> recordDelete(@PathVariable Long id) {
        recordMapper.deleteById(id);
        return Result.ok();
    }

    // ===== 模拟发送 =====

    @Operation(summary = "模拟发送消息")
    @PreAuthorize("hasAuthority('system:message:send')")
    @PostMapping("/send")
    public Result<String> send(@RequestBody SendRequest req) {
        LambdaQueryWrapper<MsgTemplate> qw = new LambdaQueryWrapper<MsgTemplate>()
                .eq(MsgTemplate::getCode, req.getTemplateCode())
                .eq(MsgTemplate::getStatus, 1);
        MsgTemplate template = templateMapper.selectOne(qw);
        if (template == null) {
            throw new BizException("模板不存在或已停用");
        }
        String content = template.getContent() == null ? "" : template.getContent();
        Map<String, String> vars = req.getVars();
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                content = content.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }

        MsgRecord record = new MsgRecord();
        record.setTemplateCode(template.getCode());
        record.setReceiver(req.getReceiver());
        record.setChannel(template.getChannel());
        record.setContent(content);
        record.setStatus(1);
        record.setSendTime(LocalDateTime.now());
        recordMapper.insert(record);

        return Result.ok(content);
    }

    public static class SendRequest {
        private String templateCode;
        private String receiver;
        private Map<String, String> vars;

        public String getTemplateCode() {
            return templateCode;
        }

        public void setTemplateCode(String templateCode) {
            this.templateCode = templateCode;
        }

        public String getReceiver() {
            return receiver;
        }

        public void setReceiver(String receiver) {
            this.receiver = receiver;
        }

        public Map<String, String> getVars() {
            return vars;
        }

        public void setVars(Map<String, String> vars) {
            this.vars = vars;
        }
    }
}
