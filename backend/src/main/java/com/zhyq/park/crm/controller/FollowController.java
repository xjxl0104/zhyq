package com.zhyq.park.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.crm.entity.Follow;
import com.zhyq.park.crm.mapper.FollowMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "招商-跟进记录")
@RestController
@RequestMapping("/crm/follow")
@RequiredArgsConstructor
public class FollowController {

    private final FollowMapper followMapper;

    @Operation(summary = "查某线索的跟进记录")
    @GetMapping("/list")
    public Result<List<Follow>> list(@RequestParam Long leadId) {
        LambdaQueryWrapper<Follow> qw = new LambdaQueryWrapper<>();
        qw.eq(Follow::getLeadId, leadId)
          .orderByDesc(Follow::getCreateTime);
        return Result.ok(followMapper.selectList(qw));
    }

    @Operation(summary = "新增跟进")
    @PostMapping
    public Result<Long> add(@RequestBody Follow follow) {
        followMapper.insert(follow);
        return Result.ok(follow.getId());
    }
}
