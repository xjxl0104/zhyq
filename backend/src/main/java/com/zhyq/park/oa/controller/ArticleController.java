package com.zhyq.park.oa.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.oa.entity.Article;
import com.zhyq.park.oa.mapper.ArticleMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "办公管理-文章")
@RestController
@RequestMapping("/oa/article")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleMapper articleMapper;

    // 文章状态:1草稿 2已发布
    private static final int ST_DRAFT = 1;
    private static final int ST_PUBLISHED = 2;

    @Operation(summary = "分页查询文章")
    @GetMapping("/page")
    public Result<PageResult<Article>> page(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String title,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Article> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(title), Article::getTitle, title)
          .eq(StringUtils.hasText(category), Article::getCategory, category)
          .eq(status != null, Article::getStatus, status)
          .orderByDesc(Article::getId);
        IPage<Article> p = articleMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "文章详情")
    @GetMapping("/{id}")
    public Result<Article> get(@PathVariable Long id) {
        return Result.ok(articleMapper.selectById(id));
    }

    @Operation(summary = "新增文章")
    @PostMapping
    public Result<Long> add(@RequestBody Article article) {
        articleMapper.insert(article);
        return Result.ok(article.getId());
    }

    @Operation(summary = "修改文章")
    @PutMapping
    public Result<Void> update(@RequestBody Article article) {
        articleMapper.updateById(article);
        return Result.ok();
    }

    @Operation(summary = "删除文章")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        articleMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "发布文章:草稿(1)→已发布(2)")
    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        int updated = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, id)
                .eq(Article::getStatus, ST_DRAFT)
                .set(Article::getStatus, ST_PUBLISHED));
        if (updated == 0) {
            throw new BizException("仅草稿状态的文章可发布");
        }
        return Result.ok();
    }

    @Operation(summary = "浏览计数:阅读量原子+1")
    @PostMapping("/{id}/view")
    public Result<Void> view(@PathVariable Long id) {
        LambdaUpdateWrapper<Article> uw = new LambdaUpdateWrapper<>();
        uw.eq(Article::getId, id)
          .setSql("views = views + 1");
        articleMapper.update(null, uw);
        return Result.ok();
    }
}
