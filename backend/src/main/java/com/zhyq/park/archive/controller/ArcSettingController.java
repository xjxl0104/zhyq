package com.zhyq.park.archive.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.archive.entity.ArcCategory;
import com.zhyq.park.archive.entity.ArcDoc;
import com.zhyq.park.archive.entity.ArcRule;
import com.zhyq.park.archive.mapper.ArcCategoryMapper;
import com.zhyq.park.archive.mapper.ArcDocMapper;
import com.zhyq.park.archive.mapper.ArcRuleMapper;
import com.zhyq.park.archive.service.ArcAnalyzeService;
import com.zhyq.park.archive.service.ArcClassifier;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** 资料管理:分类架构与自动归类关键词规则的维护 */
@Tag(name = "资料管理-分类与规则")
@RestController
@RequestMapping("/archive/setting")
@RequiredArgsConstructor
public class ArcSettingController {

    private final ArcCategoryMapper categoryMapper;
    private final ArcRuleMapper ruleMapper;
    private final ArcDocMapper docMapper;
    private final ArcAnalyzeService analyzer;

    @Data
    public static class TestRequest {
        private String fileName;
        private String sourcePath;
        private String text;
    }

    // ---------------- 分类 ----------------

    @Operation(summary = "全部分类(含停用),按编码排序")
    @GetMapping("/category/list")
    public Result<List<ArcCategory>> categories() {
        return Result.ok(categoryMapper.selectList(new LambdaQueryWrapper<ArcCategory>()
            .orderByAsc(ArcCategory::getSort, ArcCategory::getCode)));
    }

    @Operation(summary = "新增分类")
    @PostMapping("/category")
    public Result<Long> addCategory(@RequestBody ArcCategory c) {
        String code = clean(c.getCode());
        if (code == null || !code.matches("\\d{2}(\\.\\d{2})?")) throw new BizException("编码格式应为 03 或 03.04");
        if (categoryMapper.selectCount(new LambdaQueryWrapper<ArcCategory>().eq(ArcCategory::getCode, code)) > 0) {
            throw new BizException("编码已存在");
        }
        String parent = code.contains(".") ? code.substring(0, code.indexOf('.')) : null;
        if (parent != null && categoryMapper.selectCount(new LambdaQueryWrapper<ArcCategory>()
                .eq(ArcCategory::getCode, parent).isNull(ArcCategory::getParentCode)) == 0) {
            throw new BizException("上级门类 " + parent + " 不存在");
        }
        c.setId(null);
        c.setCode(code);
        c.setParentCode(parent);
        checkCategory(c);
        categoryMapper.insert(c);
        return Result.ok(c.getId());
    }

    @Operation(summary = "修改分类(编码不可改)")
    @PutMapping("/category")
    public Result<Void> updateCategory(@RequestBody ArcCategory c) {
        ArcCategory old = c.getId() == null ? null : categoryMapper.selectById(c.getId());
        if (old == null) throw new BizException("分类不存在");
        c.setCode(old.getCode());
        c.setParentCode(old.getParentCode());
        checkCategory(c);
        old.setName(c.getName());
        old.setSort(c.getSort());
        old.setDefaultSecret(c.getDefaultSecret());
        old.setEnabled(c.getEnabled());
        old.setRemark(c.getRemark());
        categoryMapper.updateById(old);
        return Result.ok();
    }

    @Operation(summary = "删除分类(有下级、资料或规则引用时不可删,请改为停用)")
    @DeleteMapping("/category/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        ArcCategory c = categoryMapper.selectById(id);
        if (c == null) return Result.ok();
        if ("99".equals(c.getCode()) || ArcClassifier.UNSORTED.equals(c.getCode())) {
            throw new BizException("「待归类」是系统兜底分类,不能删除");
        }
        if (categoryMapper.selectCount(new LambdaQueryWrapper<ArcCategory>().eq(ArcCategory::getParentCode, c.getCode())) > 0) {
            throw new BizException("该门类下还有子类,不能删除");
        }
        long docs = docMapper.selectCount(new LambdaQueryWrapper<ArcDoc>().eq(ArcDoc::getCategoryCode, c.getCode()));
        if (docs > 0) throw new BizException("该分类下已有 " + docs + " 份资料,不能删除,可改为停用");
        if (ruleMapper.selectCount(new LambdaQueryWrapper<ArcRule>().eq(ArcRule::getCategoryCode, c.getCode())) > 0) {
            throw new BizException("还有关键词规则指向该分类,请先处理规则或改为停用");
        }
        categoryMapper.deleteById(id);
        return Result.ok();
    }

    // ---------------- 规则 ----------------

    @Operation(summary = "分页查询关键词规则")
    @GetMapping("/rule/page")
    public Result<PageResult<ArcRule>> rulePage(@RequestParam(defaultValue = "1") int pageNo,
                                                @RequestParam(defaultValue = "20") int pageSize,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String categoryCode,
                                                @RequestParam(required = false) Integer enabled) {
        LambdaQueryWrapper<ArcRule> qw = new LambdaQueryWrapper<ArcRule>()
            .like(StringUtils.hasText(keyword), ArcRule::getKeyword, keyword == null ? null : keyword.trim())
            .eq(StringUtils.hasText(categoryCode), ArcRule::getCategoryCode, categoryCode)
            .eq(enabled != null, ArcRule::getEnabled, enabled)
            .orderByAsc(ArcRule::getCategoryCode).orderByDesc(ArcRule::getWeight).orderByAsc(ArcRule::getId);
        IPage<ArcRule> p = ruleMapper.selectPage(new Page<>(pageNo, Math.min(pageSize, 200)), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "新增规则")
    @PostMapping("/rule")
    public Result<Long> addRule(@RequestBody ArcRule r) {
        r.setId(null);
        checkRule(r);
        ruleMapper.insert(r);
        return Result.ok(r.getId());
    }

    @Operation(summary = "修改规则")
    @PutMapping("/rule")
    public Result<Void> updateRule(@RequestBody ArcRule r) {
        if (r.getId() == null || ruleMapper.selectById(r.getId()) == null) throw new BizException("规则不存在");
        checkRule(r);
        ruleMapper.updateById(r);
        return Result.ok();
    }

    @Operation(summary = "删除规则")
    @DeleteMapping("/rule/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        ruleMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "用文件名(和可选的文件夹/正文)试一试当前规则会怎么归类")
    @PostMapping("/test")
    public Result<ArcClassifier.Suggestion> test(@RequestBody TestRequest req) {
        if (!StringUtils.hasText(req.getFileName())) throw new BizException("请输入文件名");
        return Result.ok(analyzer.suggest(req.getFileName(), req.getSourcePath(), req.getText(), LocalDate.now()));
    }

    // ---------------- 校验 ----------------

    private void checkCategory(ArcCategory c) {
        c.setName(clean(c.getName()));
        if (!StringUtils.hasText(c.getName()) || c.getName().length() > 64) throw new BizException("请填写分类名称(64字以内)");
        if (c.getSort() == null) c.setSort(0);
        if (c.getDefaultSecret() == null || c.getDefaultSecret() < 1 || c.getDefaultSecret() > 3) c.setDefaultSecret(2);
        if (c.getEnabled() == null) c.setEnabled(1);
    }

    private void checkRule(ArcRule r) {
        r.setKeyword(clean(r.getKeyword()));
        if (!StringUtils.hasText(r.getKeyword()) || r.getKeyword().length() > 64) throw new BizException("请填写关键词(64字以内)");
        if (r.getScope() == null || (r.getScope() != 1 && r.getScope() != 2)) r.setScope(1);
        if (r.getWeight() == null || r.getWeight() < 1 || r.getWeight() > 20) throw new BizException("权重请填写 1~20");
        if (r.getEnabled() == null) r.setEnabled(1);
        String code = clean(r.getCategoryCode());
        r.setCategoryCode(StringUtils.hasText(code) ? code : null);
        String type = clean(r.getDocType());
        r.setDocType(StringUtils.hasText(type) ? type : null);
        if (r.getCategoryCode() == null && r.getDocType() == null && r.getSecretLevel() == null) {
            throw new BizException("请至少选择一项:倾向的分类、文档类型或密级");
        }
        if (r.getCategoryCode() != null && categoryMapper.selectCount(new LambdaQueryWrapper<ArcCategory>()
                .eq(ArcCategory::getCode, r.getCategoryCode()).isNotNull(ArcCategory::getParentCode)) == 0) {
            throw new BizException("规则指向的子类不存在");
        }
        if (r.getDocType() != null && !ArcAnalyzeService.DOC_TYPES.contains(r.getDocType())) throw new BizException("文档类型不正确");
        if (r.getSecretLevel() != null && (r.getSecretLevel() < 1 || r.getSecretLevel() > 3)) throw new BizException("密级不正确");
    }

    private static String clean(String s) { return s == null ? null : s.trim(); }
}
