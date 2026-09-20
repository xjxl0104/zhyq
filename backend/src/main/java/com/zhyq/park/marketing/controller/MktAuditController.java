package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.entity.SysAudit;
import com.zhyq.park.marketing.mapper.SysAuditMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "全民营销-审计日志")
@RestController
@RequestMapping("/crm/marketing/audit")
@RequiredArgsConstructor
public class MktAuditController {

    private final SysAuditMapper auditMapper;

    @Operation(summary = "分页查询本模块审计")
    @PreAuthorize("hasAuthority('crm:marketing:audit:query')")
    @GetMapping("/page")
    public Result<PageResult<SysAudit>> page(@RequestParam(defaultValue = "1") int pageNo,
                                             @RequestParam(defaultValue = "20") int pageSize,
                                             @RequestParam(required = false) String action,
                                             @RequestParam(required = false) String bizType,
                                             @RequestParam(required = false) Long bizId) {
        LambdaQueryWrapper<SysAudit> qw = new LambdaQueryWrapper<SysAudit>()
                .eq(SysAudit::getModule, MktAuditService.MODULE)
                .likeRight(StringUtils.hasText(action), SysAudit::getAction, action)
                .eq(StringUtils.hasText(bizType), SysAudit::getBizType, bizType)
                .eq(bizId != null, SysAudit::getBizId, bizId)
                .orderByDesc(SysAudit::getId);
        IPage<SysAudit> p = auditMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }
}
