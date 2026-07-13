package com.zhyq.park.hui.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.hui.entity.PassCard;
import com.zhyq.park.hui.mapper.PassCardMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 惠企服务-出入证管理
 * 状态:1有效 2已挂失 3已注销
 */
@Tag(name = "惠企服务-出入证管理")
@RestController
@RequestMapping("/service/passcard")
@RequiredArgsConstructor
public class PassCardController {

    /** 状态:有效 */
    public static final int ST_VALID = 1;
    /** 状态:已挂失 */
    public static final int ST_LOST = 2;
    /** 状态:已注销 */
    public static final int ST_CANCELLED = 3;

    private final PassCardMapper passCardMapper;

    @Operation(summary = "分页查询出入证")
    @GetMapping("/page")
    public Result<PageResult<PassCard>> page(@RequestParam(defaultValue = "1") int pageNo,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) String cardNo,
                                             @RequestParam(required = false) String holder,
                                             @RequestParam(required = false) String cardType,
                                             @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<PassCard> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(cardNo), PassCard::getCardNo, cardNo)
          .like(StringUtils.hasText(holder), PassCard::getHolder, holder)
          .eq(StringUtils.hasText(cardType), PassCard::getCardType, cardType)
          .eq(status != null, PassCard::getStatus, status)
          .orderByDesc(PassCard::getId);
        IPage<PassCard> p = passCardMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "出入证详情")
    @GetMapping("/{id}")
    public Result<PassCard> get(@PathVariable Long id) {
        return Result.ok(passCardMapper.selectById(id));
    }

    @Operation(summary = "新增出入证")
    @PostMapping
    public Result<Long> add(@RequestBody PassCard passCard) {
        if (passCard.getStatus() == null) {
            passCard.setStatus(ST_VALID);
        }
        passCardMapper.insert(passCard);
        return Result.ok(passCard.getId());
    }

    @Operation(summary = "修改出入证")
    @PutMapping
    public Result<Void> update(@RequestBody PassCard passCard) {
        passCardMapper.updateById(passCard);
        return Result.ok();
    }

    @Operation(summary = "删除出入证")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        passCardMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "挂失(1→2)")
    @PostMapping("/{id}/loss")
    public Result<Void> loss(@PathVariable Long id) {
        LambdaUpdateWrapper<PassCard> uw = new LambdaUpdateWrapper<>();
        uw.eq(PassCard::getId, id)
          .eq(PassCard::getStatus, ST_VALID)
          .set(PassCard::getStatus, ST_LOST);
        int updated = passCardMapper.update(null, uw);
        if (updated == 0) {
            throw new BizException("仅有效状态可挂失");
        }
        return Result.ok();
    }

    @Operation(summary = "注销(→3)")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        LambdaUpdateWrapper<PassCard> uw = new LambdaUpdateWrapper<>();
        uw.eq(PassCard::getId, id)
          .ne(PassCard::getStatus, ST_CANCELLED)
          .set(PassCard::getStatus, ST_CANCELLED);
        int updated = passCardMapper.update(null, uw);
        if (updated == 0) {
            throw new BizException("该出入证已注销");
        }
        return Result.ok();
    }
}
