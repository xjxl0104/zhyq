package com.zhyq.park.archive.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.archive.entity.ArcDoc;
import com.zhyq.park.archive.entity.ArcFolder;
import com.zhyq.park.archive.mapper.ArcDocMapper;
import com.zhyq.park.archive.mapper.ArcFolderMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资料管理 · 文件夹。像电脑文件夹一样:随时新建、改名、删除、无限层级。
 * 文件夹按 id 路径物化(path=/1/5/),改名不影响子孙,子树查询用前缀匹配。
 */
@Tag(name = "资料管理-文件夹")
@RestController
@RequestMapping("/archive/folder")
@RequiredArgsConstructor
public class ArcFolderController {

    private final ArcFolderMapper folderMapper;
    private final ArcDocMapper docMapper;

    @Data
    public static class FolderReq {
        private Long id;
        private Long parentId;
        private String name;
    }

    @Operation(summary = "文件夹树(含每个文件夹内文件数,含子文件夹)")
    @GetMapping("/tree")
    public Result<List<Map<String, Object>>> tree(@RequestParam(required = false) Long projectId) {
        List<ArcFolder> all = folderMapper.selectList(new LambdaQueryWrapper<ArcFolder>()
            .orderByAsc(ArcFolder::getSort, ArcFolder::getId));
        Map<Long, Long> counts = docCounts(projectId);

        Map<Long, Map<String, Object>> nodes = new LinkedHashMap<>();
        for (ArcFolder f : all) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", f.getId());
            n.put("parentId", f.getParentId());
            n.put("name", f.getName());
            n.put("path", f.getPath());
            n.put("level", f.getLevel());
            // 自身文件数
            n.put("count", counts.getOrDefault(f.getId(), 0L));
            n.put("children", new ArrayList<Map<String, Object>>());
            nodes.put(f.getId(), n);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (ArcFolder f : all) {
            Map<String, Object> n = nodes.get(f.getId());
            Map<String, Object> parent = f.getParentId() == null ? null : nodes.get(f.getParentId());
            if (parent == null) roots.add(n);
            else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> kids = (List<Map<String, Object>>) parent.get("children");
                kids.add(n);
            }
        }
        // 自底向上累加子文件夹文件数,让父文件夹角标显示全部
        for (int i = roots.size() - 1; i >= 0; i--) rollup(roots.get(i));
        return Result.ok(roots);
    }

    @Operation(summary = "新建文件夹")
    @PostMapping
    public Result<ArcFolder> create(@RequestBody FolderReq req) {
        String name = cleanName(req.getName());
        Long parentId = req.getParentId() == null || req.getParentId() <= 0 ? null : req.getParentId();
        ArcFolder parent = null;
        if (parentId != null) {
            parent = folderMapper.selectById(parentId);
            if (parent == null) throw new BizException("上级文件夹不存在");
        }
        assertSiblingNameFree(parentId, name, null);
        ArcFolder f = new ArcFolder();
        f.setParentId(parentId);
        f.setName(name);
        f.setLevel(parent == null ? 1 : (parent.getLevel() == null ? 1 : parent.getLevel()) + 1);
        f.setSort(0);
        f.setPath("/");
        folderMapper.insert(f);
        // 插入后才有 id,再回填物化路径
        f.setPath((parent == null ? "/" : parent.getPath()) + f.getId() + "/");
        folderMapper.update(null, new LambdaUpdateWrapper<ArcFolder>()
            .eq(ArcFolder::getId, f.getId()).set(ArcFolder::getPath, f.getPath()));
        return Result.ok(f);
    }

    @Operation(summary = "重命名文件夹(改名不影响子文件夹与文件)")
    @PutMapping
    public Result<Void> rename(@RequestBody FolderReq req) {
        if (req.getId() == null) throw new BizException("缺少文件夹");
        ArcFolder f = folderMapper.selectById(req.getId());
        if (f == null) throw new BizException("文件夹不存在");
        String name = cleanName(req.getName());
        assertSiblingNameFree(f.getParentId(), name, f.getId());
        folderMapper.update(null, new LambdaUpdateWrapper<ArcFolder>()
            .eq(ArcFolder::getId, f.getId()).set(ArcFolder::getName, name));
        return Result.ok();
    }

    @Operation(summary = "删除空文件夹(非空需先清空)")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ArcFolder f = folderMapper.selectById(id);
        if (f == null) return Result.ok();
        if (folderMapper.selectCount(new LambdaQueryWrapper<ArcFolder>().eq(ArcFolder::getParentId, id)) > 0) {
            throw new BizException("文件夹里还有子文件夹,请先清空");
        }
        if (docMapper.selectCount(new LambdaQueryWrapper<ArcDoc>().eq(ArcDoc::getFolderId, id)) > 0) {
            throw new BizException("文件夹里还有资料,请先移走或删除");
        }
        folderMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "面包屑(从根到当前文件夹)")
    @GetMapping("/breadcrumb")
    public Result<List<Map<String, Object>>> breadcrumb(@RequestParam(required = false) Long id) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (id == null) return Result.ok(out);
        ArcFolder f = folderMapper.selectById(id);
        if (f == null || !StringUtils.hasText(f.getPath())) return Result.ok(out);
        String[] ids = f.getPath().split("/");
        List<Long> chain = new ArrayList<>();
        for (String s : ids) if (StringUtils.hasText(s)) chain.add(Long.parseLong(s));
        for (Long fid : chain) {
            ArcFolder n = folderMapper.selectById(fid);
            if (n != null) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", n.getId());
                m.put("name", n.getName());
                out.add(m);
            }
        }
        return Result.ok(out);
    }

    /** 每个文件夹自身的文件数(不含子文件夹,汇总在 tree 里做) */
    private Map<Long, Long> docCounts(Long projectId) {
        QueryWrapper<ArcDoc> qw = new QueryWrapper<ArcDoc>().select("folder_id AS fid", "COUNT(*) AS cnt")
            .isNotNull("folder_id").eq(projectId != null, "project_id", projectId).groupBy("folder_id");
        Map<Long, Long> out = new LinkedHashMap<>();
        for (Map<String, Object> row : docMapper.selectMaps(qw)) {
            out.put(((Number) row.get("fid")).longValue(), ((Number) row.get("cnt")).longValue());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private long rollup(Map<String, Object> node) {
        long sum = ((Number) node.get("count")).longValue();
        List<Map<String, Object>> kids = (List<Map<String, Object>>) node.get("children");
        for (Map<String, Object> kid : kids) sum += rollup(kid);
        node.put("count", sum);
        return sum;
    }

    private void assertSiblingNameFree(Long parentId, String name, Long excludeId) {
        LambdaQueryWrapper<ArcFolder> qw = new LambdaQueryWrapper<ArcFolder>()
            .eq(ArcFolder::getName, name)
            .last("LIMIT 1");
        if (parentId == null) qw.isNull(ArcFolder::getParentId);
        else qw.eq(ArcFolder::getParentId, parentId);
        if (excludeId != null) qw.ne(ArcFolder::getId, excludeId);
        if (folderMapper.selectCount(qw) > 0) throw new BizException("同一位置已有同名文件夹");
    }

    private String cleanName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) throw new BizException("请填写文件夹名称");
        if (n.length() > 100) throw new BizException("文件夹名称不能超过100个字");
        if (n.contains("/") || n.contains("\\")) throw new BizException("文件夹名称不能包含斜杠");
        return n;
    }
}
