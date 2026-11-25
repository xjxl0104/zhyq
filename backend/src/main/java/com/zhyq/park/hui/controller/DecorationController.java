package com.zhyq.park.hui.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.hui.entity.Decoration;
import com.zhyq.park.hui.mapper.DecorationMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 物业服务-装修及动火申请
 * applyType: DECORATION(装修报批申请表) / HOT_WORK(动火作业审批表)
 * 状态:1待审批 2施工中 3已完工 4已驳回
 */
@Tag(name = "物业服务-装修及动火")
@RestController
@RequestMapping("/service/decoration")
@RequiredArgsConstructor
public class DecorationController {

    /** 状态:待审批 */
    public static final int ST_PENDING = 1;
    /** 状态:施工中 */
    public static final int ST_IN_PROGRESS = 2;
    /** 状态:已完工 */
    public static final int ST_FINISHED = 3;
    /** 状态:已驳回 */
    public static final int ST_REJECTED = 4;

    private final DecorationMapper decorationMapper;

    @Operation(summary = "分页查询装修及动火申请")
    @GetMapping("/page")
    public Result<PageResult<Decoration>> page(@RequestParam(defaultValue = "1") int pageNo,
                                               @RequestParam(defaultValue = "10") int pageSize,
                                               @RequestParam(required = false) Integer status,
                                               @RequestParam(required = false) String applyType,
                                               @RequestParam(required = false) String contractor,
                                               @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Decoration> qw = new LambdaQueryWrapper<>();
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : (StringUtils.hasText(contractor) ? contractor.trim() : null);
        qw.eq(status != null, Decoration::getStatus, status)
          .eq(StringUtils.hasText(applyType), Decoration::getApplyType, applyType)
          .and(StringUtils.hasText(kw), w -> w
                  .like(Decoration::getApplyNo, kw)
                  .or().like(Decoration::getApplicantUnit, kw)
                  .or().like(Decoration::getContractor, kw)
                  .or().like(Decoration::getWorkArea, kw)
                  .or().like(Decoration::getScope, kw))
          .orderByDesc(Decoration::getId);
        IPage<Decoration> p = decorationMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "申请详情")
    @GetMapping("/{id}")
    public Result<Decoration> get(@PathVariable Long id) {
        return Result.ok(decorationMapper.selectById(id));
    }

    @Operation(summary = "新增装修或动火申请")
    @PostMapping
    public Result<Long> add(@RequestBody Decoration decoration) {
        if (!StringUtils.hasText(decoration.getApplyType())) {
            decoration.setApplyType("DECORATION");
        }
        if (decoration.getApplyDate() == null) {
            decoration.setApplyDate(LocalDate.now());
        }
        if (!StringUtils.hasText(decoration.getApplyNo())) {
            decoration.setApplyNo(generateApplyNo(decoration.getApplyType(), decoration.getApplyDate()));
        }
        if (decoration.getStatus() == null) {
            decoration.setStatus(ST_PENDING);
        }
        decorationMapper.insert(decoration);
        return Result.ok(decoration.getId());
    }

    @Operation(summary = "修改装修或动火申请")
    @PutMapping
    public Result<Void> update(@RequestBody Decoration decoration) {
        if (!StringUtils.hasText(decoration.getApplyType())) {
            decoration.setApplyType("DECORATION");
        }
        decorationMapper.updateById(decoration);
        return Result.ok();
    }

    private String generateApplyNo(String applyType, LocalDate applyDate) {
        int year = (applyDate != null ? applyDate : LocalDate.now()).getYear();
        Long count = decorationMapper.selectCount(new LambdaQueryWrapper<Decoration>()
                .eq(Decoration::getApplyType, applyType));
        long seq = (count != null ? count : 0L) + 1;
        if ("HOT_WORK".equalsIgnoreCase(applyType)) {
            return String.format("〔%d〕动字第%03d号", year, seq);
        }
        return String.format("DIPARK-装批-%d%03d", year, seq);
    }

    @Operation(summary = "删除装修申请")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        decorationMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "审批通过(1→2施工中)")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        LambdaUpdateWrapper<Decoration> uw = new LambdaUpdateWrapper<>();
        uw.eq(Decoration::getId, id)
          .eq(Decoration::getStatus, ST_PENDING)
          .set(Decoration::getStatus, ST_IN_PROGRESS);
        int updated = decorationMapper.update(null, uw);
        if (updated == 0) {
            throw new BizException("仅待审批状态可通过");
        }
        return Result.ok();
    }

    @Operation(summary = "驳回(1→4)")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        LambdaUpdateWrapper<Decoration> uw = new LambdaUpdateWrapper<>();
        uw.eq(Decoration::getId, id)
          .eq(Decoration::getStatus, ST_PENDING)
          .set(Decoration::getStatus, ST_REJECTED);
        int updated = decorationMapper.update(null, uw);
        if (updated == 0) {
            throw new BizException("仅待审批状态可驳回");
        }
        return Result.ok();
    }

    @Operation(summary = "完工(2→3)")
    @PostMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id) {
        LambdaUpdateWrapper<Decoration> uw = new LambdaUpdateWrapper<>();
        uw.eq(Decoration::getId, id)
          .eq(Decoration::getStatus, ST_IN_PROGRESS)
          .set(Decoration::getStatus, ST_FINISHED);
        int updated = decorationMapper.update(null, uw);
        if (updated == 0) {
            throw new BizException("仅施工中状态可完工");
        }
        return Result.ok();
    }
}
