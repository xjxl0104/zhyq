package com.zhyq.park.hui.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.hui.entity.Declare;
import com.zhyq.park.hui.mapper.DeclareMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 惠企服务-申报服务
 * 状态:1材料准备 2已提交 3已通过 4未通过
 */
@Tag(name = "惠企服务-申报服务")
@RestController
@RequestMapping("/service/declare")
@RequiredArgsConstructor
public class DeclareController {

    /** 状态:材料准备 */
    public static final int ST_PREPARING = 1;
    /** 状态:已提交 */
    public static final int ST_SUBMITTED = 2;
    /** 状态:已通过 */
    public static final int ST_PASSED = 3;
    /** 状态:未通过 */
    public static final int ST_FAILED = 4;

    private final DeclareMapper declareMapper;

    @Operation(summary = "分页查询申报")
    @GetMapping("/page")
    public Result<PageResult<Declare>> page(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String title,
                                            @RequestParam(required = false) String dtype,
                                            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Declare> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(title), Declare::getTitle, title)
          .eq(StringUtils.hasText(dtype), Declare::getDtype, dtype)
          .eq(status != null, Declare::getStatus, status)
          .orderByDesc(Declare::getId);
        IPage<Declare> p = declareMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "申报详情")
    @GetMapping("/{id}")
    public Result<Declare> get(@PathVariable Long id) {
        return Result.ok(declareMapper.selectById(id));
    }

    @Operation(summary = "新增申报")
    @PostMapping
    public Result<Long> add(@RequestBody Declare declare) {
        if (declare.getStatus() == null) {
            declare.setStatus(ST_PREPARING);
        }
        declareMapper.insert(declare);
        return Result.ok(declare.getId());
    }

    @Operation(summary = "修改申报")
    @PutMapping
    public Result<Void> update(@RequestBody Declare declare) {
        declareMapper.updateById(declare);
        return Result.ok();
    }

    @Operation(summary = "删除申报")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        declareMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "提交(材料准备→已提交)")
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        LambdaUpdateWrapper<Declare> uw = new LambdaUpdateWrapper<>();
        uw.eq(Declare::getId, id).eq(Declare::getStatus, ST_PREPARING).set(Declare::getStatus, ST_SUBMITTED);
        int rows = declareMapper.update(null, uw);
        if (rows == 0) {
            throw new BizException("仅材料准备状态的申报可提交");
        }
        return Result.ok();
    }

    @Operation(summary = "审核通过(已提交→已通过)")
    @PostMapping("/{id}/pass")
    public Result<Void> pass(@PathVariable Long id) {
        LambdaUpdateWrapper<Declare> uw = new LambdaUpdateWrapper<>();
        uw.eq(Declare::getId, id).eq(Declare::getStatus, ST_SUBMITTED).set(Declare::getStatus, ST_PASSED);
        int rows = declareMapper.update(null, uw);
        if (rows == 0) {
            throw new BizException("仅已提交状态的申报可通过");
        }
        return Result.ok();
    }

    @Operation(summary = "审核未通过(已提交→未通过)")
    @PostMapping("/{id}/fail")
    public Result<Void> fail(@PathVariable Long id) {
        LambdaUpdateWrapper<Declare> uw = new LambdaUpdateWrapper<>();
        uw.eq(Declare::getId, id).eq(Declare::getStatus, ST_SUBMITTED).set(Declare::getStatus, ST_FAILED);
        int rows = declareMapper.update(null, uw);
        if (rows == 0) {
            throw new BizException("仅已提交状态的申报可标记未通过");
        }
        return Result.ok();
    }
}
