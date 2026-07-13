package com.zhyq.park.oa.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.oa.entity.Document;
import com.zhyq.park.oa.mapper.DocumentMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 公文管理(oa_document)
 * 状态流转:1拟稿 → 2核稿 → 3签发 → 4归档,条件更新防并发重复操作。
 */
@Tag(name = "办公管理-公文")
@RestController
@RequestMapping("/oa/document")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentMapper documentMapper;

    private static final int ST_DRAFT = 1;
    private static final int ST_REVIEW = 2;
    private static final int ST_SIGNED = 3;
    private static final int ST_ARCHIVED = 4;

    @Operation(summary = "分页查询公文")
    @GetMapping("/page")
    public Result<PageResult<Document>> page(@RequestParam(defaultValue = "1") int pageNo,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) String title,
                                             @RequestParam(required = false) String docType,
                                             @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Document> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(title), Document::getTitle, title)
          .eq(StringUtils.hasText(docType), Document::getDocType, docType)
          .eq(status != null, Document::getStatus, status)
          .orderByDesc(Document::getId);
        IPage<Document> p = documentMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "公文详情")
    @GetMapping("/{id}")
    public Result<Document> get(@PathVariable Long id) {
        return Result.ok(documentMapper.selectById(id));
    }

    @Operation(summary = "新增公文")
    @PostMapping
    public Result<Long> add(@RequestBody Document document) {
        if (document.getStatus() == null) {
            document.setStatus(ST_DRAFT);
        }
        documentMapper.insert(document);
        return Result.ok(document.getId());
    }

    @Operation(summary = "修改公文")
    @PutMapping
    public Result<Void> update(@RequestBody Document document) {
        documentMapper.updateById(document);
        return Result.ok();
    }

    @Operation(summary = "删除公文")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "核稿:拟稿→核稿")
    @PostMapping("/{id}/review")
    public Result<Void> review(@PathVariable Long id) {
        int updated = documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                .eq(Document::getId, id)
                .eq(Document::getStatus, ST_DRAFT)
                .set(Document::getStatus, ST_REVIEW));
        if (updated == 0) {
            throw new BizException("当前状态不可执行该操作");
        }
        return Result.ok();
    }

    @Operation(summary = "签发:核稿→签发")
    @PostMapping("/{id}/sign")
    public Result<Void> sign(@PathVariable Long id) {
        int updated = documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                .eq(Document::getId, id)
                .eq(Document::getStatus, ST_REVIEW)
                .set(Document::getStatus, ST_SIGNED)
                .set(Document::getSignBy, "system")
                .set(Document::getSignTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BizException("当前状态不可执行该操作");
        }
        return Result.ok();
    }

    @Operation(summary = "归档:签发→归档")
    @PostMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id) {
        int updated = documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                .eq(Document::getId, id)
                .eq(Document::getStatus, ST_SIGNED)
                .set(Document::getStatus, ST_ARCHIVED));
        if (updated == 0) {
            throw new BizException("当前状态不可执行该操作");
        }
        return Result.ok();
    }
}
