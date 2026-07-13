package com.zhyq.park.app.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.app.entity.App;
import com.zhyq.park.app.entity.AppFavorite;
import com.zhyq.park.app.mapper.AppFavoriteMapper;
import com.zhyq.park.app.mapper.AppMapper;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "应用中心")
@RestController
@RequestMapping("/app")
@RequiredArgsConstructor
public class AppController {

    /** 当前登录用户(演示环境固定为 1) */
    private static final Long CURRENT_USER_ID = 1L;

    private final AppMapper appMapper;
    private final AppFavoriteMapper favoriteMapper;

    @Operation(summary = "全部上架应用(按分类+排序)")
    @GetMapping("/list")
    public Result<List<App>> list(@RequestParam(required = false) String category,
                                  @RequestParam(required = false) String name) {
        LambdaQueryWrapper<App> qw = new LambdaQueryWrapper<>();
        qw.eq(App::getStatus, 1)
          .eq(StringUtils.hasText(category), App::getCategory, category)
          .like(StringUtils.hasText(name), App::getName, name)
          .orderByAsc(App::getCategory)
          .orderByAsc(App::getSort);
        return Result.ok(appMapper.selectList(qw));
    }

    @Operation(summary = "应用分类列表")
    @GetMapping("/categories")
    public Result<List<String>> categories() {
        List<App> apps = appMapper.selectList(new LambdaQueryWrapper<App>()
                .eq(App::getStatus, 1).orderByAsc(App::getSort));
        List<String> categories = apps.stream()
                .map(App::getCategory)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        return Result.ok(categories);
    }

    @Operation(summary = "分页查询应用(管理)")
    @GetMapping("/page")
    public Result<PageResult<App>> page(@RequestParam(defaultValue = "1") int pageNo,
                                        @RequestParam(defaultValue = "10") int pageSize,
                                        @RequestParam(required = false) String name,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<App> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(name), App::getName, name)
          .eq(StringUtils.hasText(category), App::getCategory, category)
          .eq(status != null, App::getStatus, status)
          .orderByAsc(App::getCategory)
          .orderByAsc(App::getSort);
        IPage<App> p = appMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "应用详情")
    @GetMapping("/{id}")
    public Result<App> get(@PathVariable Long id) {
        return Result.ok(appMapper.selectById(id));
    }

    @Operation(summary = "新增应用")
    @PostMapping
    public Result<Long> add(@RequestBody App app) {
        appMapper.insert(app);
        return Result.ok(app.getId());
    }

    @Operation(summary = "修改应用")
    @PutMapping
    public Result<Void> update(@RequestBody App app) {
        appMapper.updateById(app);
        return Result.ok();
    }

    @Operation(summary = "删除应用")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        appMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "我的常用应用")
    @GetMapping("/favorite/list")
    public Result<List<App>> favoriteList() {
        List<AppFavorite> favorites = favoriteMapper.selectList(
                new LambdaQueryWrapper<AppFavorite>().eq(AppFavorite::getUserId, CURRENT_USER_ID));
        if (favorites.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> appIds = favorites.stream().map(AppFavorite::getAppId).collect(Collectors.toList());
        List<App> apps = appMapper.selectList(new LambdaQueryWrapper<App>()
                .in(App::getId, appIds)
                .eq(App::getStatus, 1)
                .orderByAsc(App::getSort));
        return Result.ok(apps);
    }

    @Operation(summary = "收藏应用(幂等)")
    @PostMapping("/favorite/{appId}")
    public Result<Void> addFavorite(@PathVariable Long appId) {
        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<AppFavorite>()
                .eq(AppFavorite::getUserId, CURRENT_USER_ID)
                .eq(AppFavorite::getAppId, appId));
        if (count == 0) {
            AppFavorite favorite = new AppFavorite();
            favorite.setUserId(CURRENT_USER_ID);
            favorite.setAppId(appId);
            favoriteMapper.insert(favorite);
        }
        return Result.ok();
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/favorite/{appId}")
    public Result<Void> removeFavorite(@PathVariable Long appId) {
        favoriteMapper.delete(new LambdaQueryWrapper<AppFavorite>()
                .eq(AppFavorite::getUserId, CURRENT_USER_ID)
                .eq(AppFavorite::getAppId, appId));
        return Result.ok();
    }
}
