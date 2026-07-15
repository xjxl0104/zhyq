package com.zhyq.park.am.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.am.entity.AmAsset;
import com.zhyq.park.am.entity.AmAssetLog;
import com.zhyq.park.am.mapper.AmAssetLogMapper;
import com.zhyq.park.am.mapper.AmAssetMapper;
import com.zhyq.park.am.service.AmAssetService;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 资产管理:台账 CRUD + 签出/签入状态机 + 盘点。
 * 资产状态:1在库 2领用中 3维修 4报废。挂 #3 空间树。price 仅记录不入账。
 */
@Tag(name = "资产管理")
@RestController
@RequestMapping("/am/asset")
@RequiredArgsConstructor
public class AmAssetController {

    private final AmAssetMapper assetMapper;
    private final AmAssetLogMapper logMapper;
    private final AmAssetService assetService;

    @Operation(summary = "分页查询资产(按分类/状态/空间过滤)")
    @GetMapping("/page")
    public Result<PageResult<AmAsset>> page(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String assetNo,
                                            @RequestParam(required = false) String name,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(required = false) Long spaceId) {
        LambdaQueryWrapper<AmAsset> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(assetNo), AmAsset::getAssetNo, assetNo)
          .like(StringUtils.hasText(name), AmAsset::getName, name)
          .eq(StringUtils.hasText(category), AmAsset::getCategory, category)
          .eq(status != null, AmAsset::getStatus, status)
          .eq(spaceId != null, AmAsset::getSpaceId, spaceId)
          .orderByDesc(AmAsset::getId);
        IPage<AmAsset> p = assetMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "资产详情")
    @GetMapping("/{id}")
    public Result<AmAsset> get(@PathVariable Long id) {
        return Result.ok(assetMapper.selectById(id));
    }

    @Operation(summary = "资产流水(签出签入/盘点 timeline)")
    @GetMapping("/{id}/logs")
    public Result<List<AmAssetLog>> logs(@PathVariable Long id) {
        List<AmAssetLog> list = logMapper.selectList(new LambdaQueryWrapper<AmAssetLog>()
                .eq(AmAssetLog::getAssetId, id)
                .orderByDesc(AmAssetLog::getId));
        return Result.ok(list);
    }

    @Operation(summary = "新增资产")
    @PostMapping
    public Result<Long> add(@RequestBody AmAsset asset) {
        if (!StringUtils.hasText(asset.getAssetNo())) {
            asset.setAssetNo("AM" + System.currentTimeMillis());
        }
        if (asset.getStatus() == null) {
            asset.setStatus(AmAssetService.ST_IN_STOCK);
        }
        assetMapper.insert(asset);
        return Result.ok(asset.getId());
    }

    @Operation(summary = "修改资产")
    @PutMapping
    public Result<Void> update(@RequestBody AmAsset asset) {
        assetMapper.updateById(asset);
        return Result.ok();
    }

    @Operation(summary = "删除资产")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        assetMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "签出(在库→领用中)")
    @PostMapping("/{id}/checkout")
    public Result<Void> checkout(@PathVariable Long id, @RequestParam String holder) {
        assetService.checkout(id, holder);
        return Result.ok();
    }

    @Operation(summary = "签入(领用中→在库)")
    @PostMapping("/{id}/checkin")
    public Result<Void> checkin(@PathVariable Long id) {
        assetService.checkin(id);
        return Result.ok();
    }

    @Operation(summary = "报废(→报废,终态)")
    @PostMapping("/{id}/scrap")
    public Result<Void> scrap(@PathVariable Long id) {
        assetService.scrap(id);
        return Result.ok();
    }

    @Operation(summary = "送修(在库→维修)")
    @PostMapping("/{id}/repair")
    public Result<Void> repair(@PathVariable Long id) {
        assetService.repair(id);
        return Result.ok();
    }

    @Operation(summary = "维修完成(维修→在库)")
    @PostMapping("/{id}/repairDone")
    public Result<Void> repairDone(@PathVariable Long id) {
        assetService.repairDone(id);
        return Result.ok();
    }

    @Operation(summary = "盘点(移动空间,不改状态)")
    @PostMapping("/{id}/inventory")
    public Result<Void> inventory(@PathVariable Long id,
                                  @RequestParam(required = false) Long spaceId,
                                  @RequestParam(required = false) String remark) {
        assetService.inventory(id, spaceId, remark);
        return Result.ok();
    }
}
