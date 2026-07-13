package com.zhyq.park.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.property.entity.Survey;
import com.zhyq.park.property.mapper.SurveyMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 投票问卷
 * 状态:1进行中 2已结束
 */
@Tag(name = "物业-投票问卷")
@RestController
@RequestMapping("/property/survey")
@RequiredArgsConstructor
public class SurveyController {

    /** 状态:进行中 */
    public static final int ST_ONGOING = 1;
    /** 状态:已结束 */
    public static final int ST_CLOSED = 2;

    private final SurveyMapper surveyMapper;

    @Operation(summary = "分页查询投票问卷")
    @GetMapping("/page")
    public Result<PageResult<Survey>> page(@RequestParam(defaultValue = "1") int pageNo,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) String stype,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(required = false) String title) {
        LambdaQueryWrapper<Survey> qw = new LambdaQueryWrapper<>();
        qw.eq(StringUtils.hasText(stype), Survey::getStype, stype)
          .eq(status != null, Survey::getStatus, status)
          .like(StringUtils.hasText(title), Survey::getTitle, title)
          .orderByDesc(Survey::getId);
        IPage<Survey> p = surveyMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "投票问卷详情")
    @GetMapping("/{id}")
    public Result<Survey> get(@PathVariable Long id) {
        return Result.ok(surveyMapper.selectById(id));
    }

    @Operation(summary = "新增投票问卷")
    @PostMapping
    public Result<Long> add(@RequestBody Survey survey) {
        if (survey.getStatus() == null) {
            survey.setStatus(ST_ONGOING);
        }
        if (survey.getVotes() == null) {
            survey.setVotes(0);
        }
        surveyMapper.insert(survey);
        return Result.ok(survey.getId());
    }

    @Operation(summary = "修改投票问卷")
    @PutMapping
    public Result<Void> update(@RequestBody Survey survey) {
        surveyMapper.updateById(survey);
        return Result.ok();
    }

    @Operation(summary = "删除投票问卷")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        surveyMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "结束")
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        LambdaUpdateWrapper<Survey> uw = new LambdaUpdateWrapper<>();
        uw.eq(Survey::getId, id)
          .eq(Survey::getStatus, ST_ONGOING)
          .set(Survey::getStatus, ST_CLOSED);
        int updated = surveyMapper.update(null, uw);
        if (updated == 0) {
            throw new BizException("该投票问卷已结束");
        }
        return Result.ok();
    }
}
