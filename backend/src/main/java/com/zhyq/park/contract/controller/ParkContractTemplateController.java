package com.zhyq.park.contract.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.audit.OperationLog;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.contract.entity.ParkContractTemplate;
import com.zhyq.park.contract.mapper.ParkContractTemplateMapper;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 合同模板库(统一管理租赁合同、物业合同、简易合同、补充协议等模板文件)。
 */
@Tag(name = "合同管理-合同模板")
@RestController
@RequestMapping("/contract/template")
@RequiredArgsConstructor
public class ParkContractTemplateController {

    private static final String BIZ_TYPE = "contract_template";

    private final ParkContractTemplateMapper mapper;
    private final SysFileMapper fileMapper;

    @Operation(summary = "查询合同模板列表(附带模板文件列表)")
    @GetMapping("/list")
    public Result<List<ParkContractTemplate>> list(@RequestParam(required = false) String category,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<ParkContractTemplate> qw = new LambdaQueryWrapper<ParkContractTemplate>()
                .eq(StringUtils.hasText(category), ParkContractTemplate::getCategory, category)
                .eq(status != null, ParkContractTemplate::getStatus, status)
                .orderByAsc(ParkContractTemplate::getSort)
                .orderByDesc(ParkContractTemplate::getUpdateTime)
                .orderByDesc(ParkContractTemplate::getId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(ParkContractTemplate::getName, kw)
                    .or().like(ParkContractTemplate::getVersionTag, kw)
                    .or().like(ParkContractTemplate::getApplicableScope, kw)
                    .or().like(ParkContractTemplate::getRemark, kw));
        }
        List<ParkContractTemplate> rows = mapper.selectList(qw);
        fillFiles(rows);
        return Result.ok(rows);
    }

    @Operation(summary = "新增合同模板")
    @OperationLog(module = "合同模板", action = "新增")
    @PostMapping
    public Result<ParkContractTemplate> add(@RequestBody ParkContractTemplate input) {
        validate(input);
        if (input.getStatus() == null) input.setStatus(1);
        if (input.getSort() == null) input.setSort(10);
        if (!StringUtils.hasText(input.getVersionTag())) input.setVersionTag("V1.0");
        mapper.insert(input);
        input.setFiles(List.of());
        return Result.ok(input);
    }

    @Operation(summary = "修改合同模板")
    @OperationLog(module = "合同模板", action = "修改")
    @PutMapping
    public Result<ParkContractTemplate> update(@RequestBody ParkContractTemplate input) {
        if (input == null || input.getId() == null) throw new BizException("缺少模板 ID");
        if (mapper.selectById(input.getId()) == null) throw new BizException(404, "合同模板不存在");
        validate(input);
        mapper.updateById(input);
        ParkContractTemplate fresh = mapper.selectById(input.getId());
        fillFiles(List.of(fresh));
        return Result.ok(fresh);
    }

    @Operation(summary = "删除合同模板")
    @OperationLog(module = "合同模板", action = "删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return Result.ok();
    }

    private static void validate(ParkContractTemplate t) {
        if (t == null) throw new BizException("模板信息不能为空");
        if (!StringUtils.hasText(t.getName())) throw new BizException("请填写模板名称");
        if (!StringUtils.hasText(t.getCategory())) throw new BizException("请选择模板分类");
        t.setName(t.getName().trim());
        t.setCategory(t.getCategory().trim());
    }

    private void fillFiles(List<ParkContractTemplate> rows) {
        if (rows == null || rows.isEmpty()) return;
        List<Long> ids = rows.stream().map(ParkContractTemplate::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return;
        List<SysFile> all = fileMapper.selectList(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getBizType, BIZ_TYPE)
                .in(SysFile::getBizId, ids)
                .orderByDesc(SysFile::getId));
        Map<Long, List<SysFile>> grouped = new LinkedHashMap<>();
        for (SysFile f : all) {
            grouped.computeIfAbsent(f.getBizId(), k -> new ArrayList<>()).add(f);
        }
        for (ParkContractTemplate row : rows) {
            row.setFiles(grouped.getOrDefault(row.getId(), List.of()));
        }
    }
}
