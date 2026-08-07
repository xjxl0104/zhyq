package com.zhyq.park.suggestion.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.suggestion.entity.Suggestion;
import com.zhyq.park.suggestion.entity.SuggestionImage;
import com.zhyq.park.suggestion.entity.SuggestionLog;
import com.zhyq.park.suggestion.service.SuggestionService;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

@Tag(name = "建议与Bug提交")
@RestController
@RequestMapping("/suggestion")
@RequiredArgsConstructor
public class SuggestionController {

    private final SuggestionService service;
    private final SysUserMapper userMapper;

    @Operation(summary = "提交建议/Bug")
    @PostMapping
    public Result<Suggestion> submit(@Validated @RequestBody SubmitRequest req,
                                     HttpServletRequest httpReq) {
        SysUser user = currentUser();
        Suggestion s = new Suggestion();
        s.setTitle(req.getTitle());
        s.setContent(req.getContent());
        s.setType(req.getType());
        s.setModule(req.getModule());
        s.setSourceUrl(req.getSourceUrl());
        s.setUserAgent(httpReq.getHeader("User-Agent"));
        s.setUserId(user.getId());
        s.setDeptId(user.getDeptId() != null ? user.getDeptId() : 0L);
        return Result.ok(service.create(s, req.getFileIds()));
    }

    @Operation(summary = "我的提交列表")
    @GetMapping("/mine")
    public Result<Page<Suggestion>> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.myList(currentUser().getId(), page, size));
    }

    @Operation(summary = "我的提交详情")
    @GetMapping("/mine/{id}")
    public Result<Map<String, Object>> mineDetail(@PathVariable Long id) {
        Suggestion s = service.getDetail(id);
        if (!s.getUserId().equals(currentUser().getId())) {
            throw new BizException(403, "无权查看");
        }
        return Result.ok(buildDetail(s));
    }

    @Operation(summary = "管理端列表")
    @GetMapping("/manage")
    @PreAuthorize("hasAuthority('suggestion:manage')")
    public Result<Page<Suggestion>> manageList(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Long deptId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.manageList(status, type, module, deptId, page, size));
    }

    @Operation(summary = "管理端详情")
    @GetMapping("/manage/{id}")
    @PreAuthorize("hasAuthority('suggestion:manage')")
    public Result<Map<String, Object>> manageDetail(@PathVariable Long id) {
        return Result.ok(buildDetail(service.getDetail(id)));
    }

    @Operation(summary = "状态流转")
    @PutMapping("/manage/{id}/status")
    @PreAuthorize("hasAuthority('suggestion:manage')")
    public Result<Void> changeStatus(@PathVariable Long id,
                                     @RequestBody StatusChangeRequest req) {
        SysUser user = currentUser();
        service.changeStatus(id, req.getStatus(), user.getId(), user.getNickname(), req.getRemark());
        return Result.ok();
    }

    @Operation(summary = "指派处理人")
    @PutMapping("/manage/{id}/assign")
    @PreAuthorize("hasAuthority('suggestion:manage')")
    public Result<Void> assign(@PathVariable Long id, @RequestBody AssignRequest req) {
        SysUser user = currentUser();
        service.assign(id, req.getAssigneeId(), user.getId(), user.getNickname());
        return Result.ok();
    }

    private Map<String, Object> buildDetail(Suggestion s) {
        List<SuggestionImage> images = service.getImages(s.getId());
        List<SuggestionLog> logs = service.getLogs(s.getId());
        return Map.of("suggestion", s, "images", images, "logs", logs);
    }

    private SysUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (String) auth.getPrincipal();
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username).last("LIMIT 1"));
        if (user == null) throw new BizException(401, "用户不存在");
        return user;
    }

    @Data
    public static class SubmitRequest {
        @NotBlank @Size(max = 50) private String title;
        private String content;
        @NotNull private Integer type;
        private String module;
        private String sourceUrl;
        private List<Long> fileIds;
    }

    @Data
    public static class StatusChangeRequest {
        @NotNull private Integer status;
        private String remark;
    }

    @Data
    public static class AssignRequest {
        @NotNull private Long assigneeId;
    }
}
