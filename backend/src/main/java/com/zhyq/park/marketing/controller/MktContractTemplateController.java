package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
    public Result<Long> add(@RequestBody MktContractTemplate t) {
        if (t.getTplVersion() == null) t.setTplVersion(1);
        templateMapper.insert(t);
        auditService.log("template.add", "template", t.getId(), null);
        return Result.ok(t.getId());
    }

    @Operation(summary = "修改") @PreAuthorize("hasAuthority('crm:marketing:template:config')") @PutMapping
    public Result<Void> update(@RequestBody MktContractTemplate t) {
        templateMapper.updateById(t);
        auditService.log("template.update", "template", t.getId(), null);
        return Result.ok();
    }

    @Operation(summary = "删除(逻辑)") @PreAuthorize("hasAuthority('crm:marketing:template:config')") @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        templateMapper.deleteById(id);
        auditService.log("template.delete", "template", id, null);
        return Result.ok();
    }
}
