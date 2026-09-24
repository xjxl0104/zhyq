package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.entity.MktContractTemplate;
import com.zhyq.park.marketing.mapper.MktContractTemplateMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "全民营销-合同模板")
@RestController
@RequestMapping("/crm/marketing/template")
@RequiredArgsConstructor
public class MktContractTemplateController {

    private final MktContractTemplateMapper templateMapper;
    private final MktAuditService auditService;

    @Operation(summary = "分页") @PreAuthorize("hasAuthority('crm:marketing:template:query')") @GetMapping("/page")
    public Result<PageResult<MktContractTemplate>> page(@RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "10") int pageSize) {
        IPage<MktContractTemplate> p = templateMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<MktContractTemplate>().orderByDesc(MktContractTemplate::getId));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "启用中的模板(起草合同下拉)") @PreAuthorize("hasAuthority('crm:marketing:template:query')") @GetMapping("/list")
    public Result<List<MktContractTemplate>> list() {
        return Result.ok(templateMapper.selectList(new LambdaQueryWrapper<MktContractTemplate>()
                .eq(MktContractTemplate::getStatus, 1).select(MktContractTemplate::getId, MktContractTemplate::getName,
                        MktContractTemplate::getServiceType, MktContractTemplate::getTplVersion)));
    }

    @Operation(summary = "新增") @PreAuthorize("hasAuthority('crm:marketing:template:config')") @PostMapping
    public Result<Long> add(@RequestBody MktContractTemplate req) {
        MktContractTemplate t = new MktContractTemplate();
        t.setName(req.getName());
        t.setServiceType(req.getServiceType());
        t.setTplVersion(req.getTplVersion() == null ? 1 : req.getTplVersion());
        t.setBody(req.getBody());
        t.setVariables(req.getVariables());
        t.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        t.setProjectId(req.getProjectId());
        templateMapper.insert(t);
        auditService.log("template.add", "template", t.getId(), null);
        return Result.ok(t.getId());
    }

    @Operation(summary = "修改") @PreAuthorize("hasAuthority('crm:marketing:template:config')") @PutMapping
    public Result<Void> update(@RequestBody MktContractTemplate req) {
        if (req.getId() == null) throw new BizException("模板 id 必填");
        MktContractTemplate before = templateMapper.selectById(req.getId());
        if (before == null) throw new BizException("模板不存在");
        // 白名单字段更新:不接收 id/tenantId/createBy 等身份字段,防越权覆盖
        int updated = templateMapper.update(null, new LambdaUpdateWrapper<MktContractTemplate>()
                .eq(MktContractTemplate::getId, req.getId())
                .set(MktContractTemplate::getName, req.getName())
                .set(MktContractTemplate::getServiceType, req.getServiceType())
                .set(MktContractTemplate::getTplVersion, req.getTplVersion())
                .set(MktContractTemplate::getBody, req.getBody())
                .set(MktContractTemplate::getVariables, req.getVariables())
                .set(MktContractTemplate::getStatus, req.getStatus())
                .set(MktContractTemplate::getProjectId, req.getProjectId()));
        if (updated == 0) throw new BizException("模板不存在或已删除");
        auditService.log("template.update", "template", req.getId(), null, before, req);
        return Result.ok();
    }

    @Operation(summary = "删除(逻辑)") @PreAuthorize("hasAuthority('crm:marketing:template:config')") @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        templateMapper.deleteById(id);
        auditService.log("template.delete", "template", id, null);
        return Result.ok();
    }
}
