package com.zhyq.park.hui.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.hui.entity.Pass;
import com.zhyq.park.hui.mapper.PassMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 惠企服务-物品放行
 * 状态:1待核验 2已放行 3已失效
 */
@Tag(name = "惠企服务-物品放行")
@RestController
@RequestMapping("/service/pass")
@RequiredArgsConstructor
public class PassController {

    /** 状态:待核验 */
    public static final int ST_PENDING = 1;
    /** 状态:已放行 */
    public static final int ST_RELEASED = 2;
    /** 状态:已失效 */
    public static final int ST_EXPIRED = 3;

    private final PassMapper passMapper;

    @Operation(summary = "分页查询物品放行")
    @GetMapping("/page")
    public Result<PageResult<Pass>> page(@RequestParam(defaultValue = "1") int pageNo,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String item,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String carrier) {
        LambdaQueryWrapper<Pass> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(item), Pass::getItem, item)
          .eq(status != null, Pass::getStatus, status)
          .like(StringUtils.hasText(carrier), Pass::getCarrier, carrier)
          .orderByDesc(Pass::getId);
        IPage<Pass> p = passMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "放行单详情")
    @GetMapping("/{id}")
    public Result<Pass> get(@PathVariable Long id) {
        return Result.ok(passMapper.selectById(id));
    }

    @Operation(summary = "新增物品放行")
    @PostMapping
    public Result<Long> add(@RequestBody Pass pass) {
        if (pass.getStatus() == null) {
            pass.setStatus(ST_PENDING);
        }
        passMapper.insert(pass);
        return Result.ok(pass.getId());
    }

    @Operation(summary = "修改物品放行")
    @PutMapping
    public Result<Void> update(@RequestBody Pass pass) {
        passMapper.updateById(pass);
        return Result.ok();
    }

    @Operation(summary = "删除物品放行")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        passMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "门岗核验放行(1→2)")
    @PostMapping("/{id}/verify")
    public Result<Void> verify(@PathVariable Long id) {
        LambdaUpdateWrapper<Pass> uw = new LambdaUpdateWrapper<>();
        uw.eq(Pass::getId, id)
          .eq(Pass::getStatus, ST_PENDING)
          .set(Pass::getStatus, ST_RELEASED);
        int updated = passMapper.update(null, uw);
        if (updated == 0) {
            throw new BizException("仅待核验状态可放行");
        }
        return Result.ok();
    }

    @Operation(summary = "批量失效检查(有效期已过且待核验的置为已失效)")
    @PostMapping("/expire-check")
    public Result<Integer> expireCheck() {
        LambdaUpdateWrapper<Pass> uw = new LambdaUpdateWrapper<>();
        uw.lt(Pass::getValidUntil, LocalDateTime.now())
          .eq(Pass::getStatus, ST_PENDING)
          .set(Pass::getStatus, ST_EXPIRED);
        int updated = passMapper.update(null, uw);
        return Result.ok(updated);
    }
}
