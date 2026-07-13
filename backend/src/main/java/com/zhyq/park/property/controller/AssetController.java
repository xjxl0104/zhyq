package com.zhyq.park.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.property.entity.Asset;
import com.zhyq.park.property.mapper.AssetMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 资产管理
 * 资产状态:1在用 2闲置 3维修 4报废
 */
@Tag(name = "物业-资产管理")
@RestController
@RequestMapping("/property/asset")
@RequiredArgsConstructor
public class AssetController {

    /** 资产状态:在用 */
    public static final int ST_IN_USE = 1;
    /** 资产状态:闲置 */
    public static final int ST_IDLE = 2;
    /** 资产状态:维修 */
    public static final int ST_REPAIRING = 3;
    /** 资产状态:报废 */
    public static final int ST_SCRAPPED = 4;

    private final AssetMapper assetMapper;

    @Operation(summary = "分页查询资产")
    @GetMapping("/page")
    public Result<PageResult<Asset>> page(@RequestParam(defaultValue = "1") int pageNo,
                                          @RequestParam(defaultValue = "10") int pageSize,
                                          @RequestParam(required = false) String code,
                                          @RequestParam(required = false) String name,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(required = false) Integer status,
                                          @RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<Asset> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(code), Asset::getCode, code)
          .like(StringUtils.hasText(name), Asset::getName, name)
          .eq(StringUtils.hasText(category), Asset::getCategory, category)
          .eq(status != null, Asset::getStatus, status)
          .eq(projectId != null, Asset::getProjectId, projectId)
          .orderByDesc(Asset::getId);
        IPage<Asset> p = assetMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "资产详情")
    @GetMapping("/{id}")
    public Result<Asset> get(@PathVariable Long id) {
        return Result.ok(assetMapper.selectById(id));
    }

    @Operation(summary = "新增资产")
    @PostMapping
    public Result<Long> add(@RequestBody Asset asset) {
        if (!StringUtils.hasText(asset.getCode())) {
            asset.setCode("ZC" + System.currentTimeMillis());
        }
        if (asset.getStatus() == null) {
            asset.setStatus(ST_IN_USE);
        }
        assetMapper.insert(asset);
        return Result.ok(asset.getId());
    }

    @Operation(summary = "修改资产")
    @PutMapping
    public Result<Void> update(@RequestBody Asset asset) {
        assetMapper.updateById(asset);
        return Result.ok();
    }

    @Operation(summary = "删除资产")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        assetMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "资产统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        long total = assetMapper.selectCount(new LambdaQueryWrapper<>());
        long inUse = assetMapper.selectCount(
                new LambdaQueryWrapper<Asset>().eq(Asset::getStatus, ST_IN_USE));
        long idle = assetMapper.selectCount(
                new LambdaQueryWrapper<Asset>().eq(Asset::getStatus, ST_IDLE));
        long repairing = assetMapper.selectCount(
                new LambdaQueryWrapper<Asset>().eq(Asset::getStatus, ST_REPAIRING));
        long scrapped = assetMapper.selectCount(
                new LambdaQueryWrapper<Asset>().eq(Asset::getStatus, ST_SCRAPPED));
        List<Object> sums = assetMapper.selectObjs(
                new QueryWrapper<Asset>().select("IFNULL(SUM(price), 0)"));
        BigDecimal totalValue = (sums == null || sums.isEmpty() || sums.get(0) == null)
                ? BigDecimal.ZERO : new BigDecimal(String.valueOf(sums.get(0)));
        Map<String, Object> m = new HashMap<>();
        m.put("total", total);
        m.put("inUse", inUse);
        m.put("idle", idle);
        m.put("repairing", repairing);
        m.put("scrapped", scrapped);
        m.put("totalValue", totalValue);
        return Result.ok(m);
    }

    @Operation(summary = "资产报废")
    @PostMapping("/{id}/scrap")
    public Result<Void> scrap(@PathVariable Long id) {
        LambdaUpdateWrapper<Asset> uw = new LambdaUpdateWrapper<>();
        uw.eq(Asset::getId, id)
          .ne(Asset::getStatus, ST_SCRAPPED)
          .set(Asset::getStatus, ST_SCRAPPED);
        int updated = assetMapper.update(null, uw);
        if (updated == 0) {
            throw new BizException("资产已报废");
        }
        return Result.ok();
    }
}
