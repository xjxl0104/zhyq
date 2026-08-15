package com.zhyq.park.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.audit.OperationLog;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.property.entity.ResponsibleUnit;
import com.zhyq.park.property.mapper.ResponsibleUnitMapper;
import com.zhyq.park.property.service.ResponsibleUnitImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 责任单位主数据:工单(尤其报修)的承接方 */
@Tag(name = "责任单位")
@RestController
@RequestMapping("/property/responsible-unit")
@RequiredArgsConstructor
public class ResponsibleUnitController {

    private final ResponsibleUnitMapper unitMapper;
    private final ResponsibleUnitImportService importService;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('property:unit:query')")
    public Result<PageResult<ResponsibleUnit>> page(@RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "10") long size,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String unitType,
                                                    @RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<ResponsibleUnit> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            q.and(w -> w.like(ResponsibleUnit::getName, keyword)
                    .or().like(ResponsibleUnit::getContact, keyword)
                    .or().like(ResponsibleUnit::getServiceScope, keyword));
        }
        if (StringUtils.hasText(unitType)) {
            q.eq(ResponsibleUnit::getUnitType, unitType);
        }
        if (projectId != null) {
            q.eq(ResponsibleUnit::getProjectId, projectId);
        }
        q.orderByDesc(ResponsibleUnit::getId);
        IPage<ResponsibleUnit> p = unitMapper.selectPage(new Page<>(current, size), q);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "启用中的单位列表(下拉用)")
    @GetMapping("/options")
    public Result<List<ResponsibleUnit>> options(@RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<ResponsibleUnit> q = new LambdaQueryWrapper<ResponsibleUnit>()
                .eq(ResponsibleUnit::getEnabled, 1)
                .select(ResponsibleUnit::getId, ResponsibleUnit::getName,
                        ResponsibleUnit::getUnitType, ResponsibleUnit::getServiceScope)
                .orderByAsc(ResponsibleUnit::getName);
        if (projectId != null) {
            // 园区专属 + 全局通用一并返回
            q.and(w -> w.eq(ResponsibleUnit::getProjectId, projectId)
                    .or().isNull(ResponsibleUnit::getProjectId));
        }
        return Result.ok(unitMapper.selectList(q));
    }

    @Operation(summary = "新增")
    @PostMapping
    @PreAuthorize("hasAuthority('property:unit:save')")
    @OperationLog(module = "责任单位", action = "新增")
    public Result<Long> add(@RequestBody ResponsibleUnit unit) {
        requireName(unit);
        if (unit.getEnabled() == null) unit.setEnabled(1);
        unitMapper.insert(unit);
        return Result.ok(unit.getId());
    }

    @Operation(summary = "修改")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('property:unit:save')")
    @OperationLog(module = "责任单位", action = "修改")
    public Result<Void> update(@PathVariable Long id, @RequestBody ResponsibleUnit unit) {
        requireName(unit);
        unit.setId(id);
        unitMapper.updateById(unit);
        return Result.ok();
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('property:unit:delete')")
    @OperationLog(module = "责任单位", action = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        unitMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "下载导入模板")
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] body = importService.template();
        String filename = new String("责任单位导入模板.xlsx".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.ISO_8859_1);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    @Operation(summary = "Excel 导入(按单位名称 upsert)")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('property:unit:save')")
    @OperationLog(module = "责任单位", action = "导入", saveParams = false)
    public Result<Map<String, Object>> importUnits(@RequestPart("file") MultipartFile file,
                                                   @RequestParam(required = false) Long projectId) {
        return Result.ok(importService.importUnits(file, projectId));
    }

    @Operation(summary = "导入前校验(只解析不落库)")
    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('property:unit:save')")
    @OperationLog(module = "责任单位", action = "导入预览", saveParams = false)
    public Result<Map<String, Object>> preview(@RequestPart("file") MultipartFile file,
                                               @RequestParam(required = false) Long projectId) {
        ResponsibleUnitImportService.ParseResult r = importService.parse(file, projectId);
        return Result.ok(Map.of(
                "valid", r.rows().size(),
                "errors", r.errors(),
                "rows", r.rows()));
    }

    private static void requireName(ResponsibleUnit unit) {
        if (unit == null || !StringUtils.hasText(unit.getName())) {
            throw new BizException("单位名称不能为空");
        }
    }
}
