package com.zhyq.park.archive.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.archive.entity.ArcCategory;
import com.zhyq.park.archive.entity.ArcDoc;
import com.zhyq.park.archive.mapper.ArcCategoryMapper;
import com.zhyq.park.archive.mapper.ArcDocMapper;
import com.zhyq.park.archive.service.ArcAnalyzeService;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.file.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资料管理:资料库的查询、上传归档、修改、删除,以及「上传后自动给名称和分类建议」。
 * 状态:1现行 2失效;密级:1公开 2内部 3机密。
 */
@Tag(name = "资料管理-资料库")
@RestController
@RequestMapping("/archive/doc")
@RequiredArgsConstructor
public class ArcDocController {

    /** 档号生成的进程内互斥(单实例部署) */
    private static final Object ARCHIVE_NO_LOCK = new Object();
    private static final int ST_ACTIVE = 1;
    private static final int ST_EXPIRED = 2;

    private final ArcDocMapper docMapper;
    private final ArcCategoryMapper categoryMapper;
    private final SysFileMapper fileMapper;
    private final FileStorageService storage;
    private final ArcAnalyzeService analyzer;

    @Data
    public static class AnalyzeRequest {
        private Long fileId;
        /** 来源路径(批量导入时的原文件夹),用于辅助判断;网页上传可不填 */
        private String sourcePath;
        /** 原文件最后修改时间(毫秒),可不填 */
        private Long lastModified;
    }

    @Operation(summary = "分类树 + 字典(文档类型)")
    @GetMapping("/meta")
    public Result<Map<String, Object>> meta() {
        List<ArcCategory> all = categoryMapper.selectList(new LambdaQueryWrapper<ArcCategory>()
            .eq(ArcCategory::getEnabled, 1).orderByAsc(ArcCategory::getSort, ArcCategory::getCode));
        List<Map<String, Object>> tree = new ArrayList<>();
        for (ArcCategory top : all) {
            if (StringUtils.hasText(top.getParentCode())) continue;
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("code", top.getCode());
            node.put("name", top.getName());
            List<Map<String, Object>> children = new ArrayList<>();
            for (ArcCategory sub : all) {
                if (!top.getCode().equals(sub.getParentCode())) continue;
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("code", sub.getCode());
                c.put("name", sub.getName());
                c.put("defaultSecret", sub.getDefaultSecret());
                children.add(c);
            }
            node.put("children", children);
            tree.add(node);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("categories", tree);
        m.put("docTypes", ArcAnalyzeService.DOC_TYPES);
        return Result.ok(m);
    }

    @Operation(summary = "各子类资料数量(分类树角标)")
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats(@RequestParam(required = false) Long projectId) {
        QueryWrapper<ArcDoc> qw = new QueryWrapper<ArcDoc>().select("category_code AS code", "COUNT(*) AS cnt")
            .eq(projectId != null, "project_id", projectId).groupBy("category_code");
        Map<String, Long> out = new LinkedHashMap<>();
        for (Map<String, Object> row : docMapper.selectMaps(qw)) {
            out.put(String.valueOf(row.get("code")), ((Number) row.get("cnt")).longValue());
        }
        return Result.ok(out);
    }

    @Operation(summary = "分页查询资料")
    @GetMapping("/page")
    public Result<PageResult<ArcDoc>> page(@RequestParam(defaultValue = "1") int pageNo,
                                           @RequestParam(defaultValue = "20") int pageSize,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String categoryCode,
                                           @RequestParam(required = false) String docType,
                                           @RequestParam(required = false) Integer secretLevel,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(required = false) Integer year,
                                           @RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<ArcDoc> qw = new LambdaQueryWrapper<>();
        qw.eq(projectId != null, ArcDoc::getProjectId, projectId)
          .eq(StringUtils.hasText(docType), ArcDoc::getDocType, docType)
          .eq(secretLevel != null, ArcDoc::getSecretLevel, secretLevel)
          .eq(status != null, ArcDoc::getStatus, status);
        if (year != null && year >= 1900 && year <= 2999) {
            qw.ge(ArcDoc::getDocDate, LocalDate.of(year, 1, 1)).le(ArcDoc::getDocDate, LocalDate.of(year, 12, 31));
        }
        if (StringUtils.hasText(categoryCode)) {
            if (categoryCode.contains(".")) qw.eq(ArcDoc::getCategoryCode, categoryCode);
            else qw.likeRight(ArcDoc::getCategoryCode, categoryCode + ".");
        }
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            qw.and(w -> w.like(ArcDoc::getName, k).or().like(ArcDoc::getOriginalName, k)
                .or().like(ArcDoc::getArchiveNo, k).or().like(ArcDoc::getCounterparty, k)
                .or().like(ArcDoc::getSummary, k));
        }
        qw.orderByDesc(ArcDoc::getId);
        IPage<ArcDoc> p = docMapper.selectPage(new Page<>(pageNo, Math.min(pageSize, 200)), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "资料详情")
    @GetMapping("/{id}")
    public Result<ArcDoc> get(@PathVariable Long id) {
        return Result.ok(docMapper.selectById(id));
    }

    @Operation(summary = "分析已上传的文件:给出规范化名称、分类、密级等建议")
    @PostMapping("/analyze")
    public Result<ArcAnalyzeService.Analysis> analyze(@RequestBody AnalyzeRequest req) {
        return Result.ok(analyzer.analyze(req.getFileId(), req.getSourcePath(), req.getLastModified()));
    }

    @Operation(summary = "确认归档:保存资料并关联文件,自动生成档号")
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<ArcDoc> add(@RequestBody ArcDoc doc) {
        SysFile file = doc.getFileId() == null ? null : fileMapper.selectById(doc.getFileId());
        if (file == null) throw new BizException("请先上传文件");
        analyzer.assertUsable(file);
        if (docMapper.selectCount(new LambdaQueryWrapper<ArcDoc>().eq(ArcDoc::getFileId, file.getId())) > 0) {
            throw new BizException("该文件已归档");
        }
        normalize(doc, true);
        doc.setId(null);
        doc.setOriginalName(file.getOriginalName());
        doc.setFileExt(file.getExt());
        doc.setFileSize(file.getFileSize());
        if (StringUtils.hasText(doc.getFileHash()) && !doc.getFileHash().matches("[0-9a-f]{64}")) doc.setFileHash(null);
        synchronized (ARCHIVE_NO_LOCK) {
            doc.setArchiveNo(nextArchiveNo(doc.getCategoryCode(), doc.getDocDate(), doc.getProjectId()));
            docMapper.insert(doc);
        }
        int attached = fileMapper.update(null, new LambdaUpdateWrapper<SysFile>()
            .eq(SysFile::getId, file.getId()).isNull(SysFile::getBizId)
            .set(SysFile::getBizType, ArcAnalyzeService.BIZ_TYPE).set(SysFile::getBizId, doc.getId()));
        if (attached != 1) throw new BizException("文件关联失败,请重试");
        return Result.ok(doc);
    }

    @Operation(summary = "修改资料信息")
    @PutMapping
    public Result<Void> update(@RequestBody ArcDoc doc) {
        if (doc.getId() == null || docMapper.selectById(doc.getId()) == null) throw new BizException("资料不存在");
        normalize(doc, false);
        docMapper.update(null, new LambdaUpdateWrapper<ArcDoc>().eq(ArcDoc::getId, doc.getId())
            .set(ArcDoc::getName, doc.getName()).set(ArcDoc::getCategoryCode, doc.getCategoryCode())
            .set(ArcDoc::getDocType, doc.getDocType()).set(ArcDoc::getSecretLevel, doc.getSecretLevel())
            .set(ArcDoc::getDocDate, doc.getDocDate()).set(ArcDoc::getPeriod, doc.getPeriod())
            .set(ArcDoc::getVersionLabel, doc.getVersionLabel()).set(ArcDoc::getCounterparty, doc.getCounterparty())
            .set(ArcDoc::getExpireDate, doc.getExpireDate()).set(ArcDoc::getStatus, doc.getStatus())
            .set(ArcDoc::getRemark, doc.getRemark()));
        return Result.ok();
    }

    @Operation(summary = "删除资料(同时删除文件)")
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable Long id) {
        ArcDoc doc = docMapper.selectById(id);
        if (doc == null) return Result.ok();
        docMapper.deleteById(id);
        if (doc.getFileId() != null) {
            SysFile f = fileMapper.selectById(doc.getFileId());
            if (f != null && ArcAnalyzeService.BIZ_TYPE.equals(f.getBizType()) && id.equals(f.getBizId())) {
                fileMapper.deleteById(f.getId());
                storage.deletePhysical(f.getStorePath());
            }
        }
        return Result.ok();
    }

    /** 校验并整理可编辑字段 */
    private void normalize(ArcDoc d, boolean create) {
        d.setName(trim(d.getName()));
        if (!StringUtils.hasText(d.getName())) throw new BizException("请填写名称");
        if (d.getName().length() > 200) throw new BizException("名称不能超过200个字");
        String code = trim(d.getCategoryCode());
        if (!StringUtils.hasText(code) || categoryMapper.selectCount(new LambdaQueryWrapper<ArcCategory>()
                .eq(ArcCategory::getCode, code).isNotNull(ArcCategory::getParentCode)) == 0) {
            throw new BizException("请选择有效的分类");
        }
        d.setCategoryCode(code);
        d.setDocType(ArcAnalyzeService.DOC_TYPES.contains(d.getDocType()) ? d.getDocType() : "其他");
        if (d.getSecretLevel() == null || d.getSecretLevel() < 1 || d.getSecretLevel() > 3) d.setSecretLevel(2);
        if (d.getStatus() == null || (d.getStatus() != ST_ACTIVE && d.getStatus() != ST_EXPIRED)) d.setStatus(ST_ACTIVE);
        d.setPeriod(limit(trim(d.getPeriod()), 32));
        d.setVersionLabel(limit(trim(d.getVersionLabel()), 32));
        d.setCounterparty(limit(trim(d.getCounterparty()), 100));
        d.setRemark(limit(trim(d.getRemark()), 500));
        if (create) {
            d.setSummary(limit(trim(d.getSummary()), 1000));
            d.setSourcePath(limit(trim(d.getSourcePath()), 500));
        }
    }

    private String nextArchiveNo(String code, LocalDate docDate, Long projectId) {
        int year = docDate != null ? docDate.getYear() : LocalDate.now().getYear();
        String prefix = code + "-" + year + "-";
        String max = docMapper.maxArchiveNo(prefix, projectId);
        int seq = 0;
        if (max != null) {
            try {
                seq = Integer.parseInt(max.substring(prefix.length()));
            } catch (NumberFormatException ignore) {
                // 档号被手工改过时按 0 重新起算
            }
        }
        return prefix + String.format("%04d", seq + 1);
    }

    private static String trim(String s) { return s == null ? null : s.trim(); }

    private static String limit(String s, int n) { return s == null || s.length() <= n ? s : s.substring(0, n); }
}
