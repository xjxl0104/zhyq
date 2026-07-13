package com.zhyq.park.hui.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.hui.entity.IpAsset;
import com.zhyq.park.hui.mapper.IpAssetMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 惠企服务-知产服务
 * 状态:1申请中 2已受理 3已授权 4已驳回
 */
@Tag(name = "惠企服务-知产服务")
@RestController
@RequestMapping("/service/ip")
@RequiredArgsConstructor
public class IpController {

    /** 状态:申请中 */
    public static final int ST_APPLYING = 1;
    /** 状态:已受理 */
    public static final int ST_ACCEPTED = 2;
    /** 状态:已授权 */
    public static final int ST_GRANTED = 3;
    /** 状态:已驳回 */
    public static final int ST_REJECTED = 4;

    private static final Map<Integer, String> STATUS_LABEL = new LinkedHashMap<>();
    static {
        STATUS_LABEL.put(ST_APPLYING, "申请中");
        STATUS_LABEL.put(ST_ACCEPTED, "已受理");
        STATUS_LABEL.put(ST_GRANTED, "已授权");
        STATUS_LABEL.put(ST_REJECTED, "已驳回");
    }

    private final IpAssetMapper ipAssetMapper;

    @Operation(summary = "分页查询知产")
    @GetMapping("/page")
    public Result<PageResult<IpAsset>> page(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String title,
                                            @RequestParam(required = false) String ipType,
                                            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<IpAsset> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(title), IpAsset::getTitle, title)
          .eq(StringUtils.hasText(ipType), IpAsset::getIpType, ipType)
          .eq(status != null, IpAsset::getStatus, status)
          .orderByDesc(IpAsset::getId);
        IPage<IpAsset> p = ipAssetMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "知产详情")
    @GetMapping("/{id}")
    public Result<IpAsset> get(@PathVariable Long id) {
        return Result.ok(ipAssetMapper.selectById(id));
    }

    @Operation(summary = "新增知产")
    @PostMapping
    public Result<Long> add(@RequestBody IpAsset ipAsset) {
        if (ipAsset.getStatus() == null) {
            ipAsset.setStatus(ST_APPLYING);
        }
        ipAssetMapper.insert(ipAsset);
        return Result.ok(ipAsset.getId());
    }

    @Operation(summary = "修改知产")
    @PutMapping
    public Result<Void> update(@RequestBody IpAsset ipAsset) {
        ipAssetMapper.updateById(ipAsset);
        return Result.ok();
    }

    @Operation(summary = "删除知产")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ipAssetMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "知产统计(总数/按类型/按状态)")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new HashMap<>();
        List<IpAsset> all = ipAssetMapper.selectList(null);
        data.put("total", all.size());

        Map<String, Long> byType = new LinkedHashMap<>();
        for (IpAsset ip : all) {
            String type = ip.getIpType() == null ? "未分类" : ip.getIpType();
            byType.merge(type, 1L, Long::sum);
        }
        List<Map<String, Object>> typeList = new ArrayList<>();
        for (Map.Entry<String, Long> e : byType.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("ipType", e.getKey());
            item.put("count", e.getValue());
            typeList.add(item);
        }
        data.put("byType", typeList);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> e : STATUS_LABEL.entrySet()) {
            byStatus.put(e.getValue(), 0L);
        }
        for (IpAsset ip : all) {
            String label = STATUS_LABEL.get(ip.getStatus());
            if (label != null) {
                byStatus.merge(label, 1L, Long::sum);
            }
        }
        List<Map<String, Object>> statusList = new ArrayList<>();
        for (Map.Entry<String, Long> e : byStatus.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("status", e.getKey());
            item.put("count", e.getValue());
            statusList.add(item);
        }
        data.put("byStatus", statusList);

        return Result.ok(data);
    }
}
