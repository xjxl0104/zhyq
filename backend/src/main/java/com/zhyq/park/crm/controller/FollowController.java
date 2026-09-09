package com.zhyq.park.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.crm.entity.Follow;
import com.zhyq.park.crm.mapper.FollowMapper;
import com.zhyq.park.crm.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "招商-跟进记录")
@RestController
@RequestMapping("/crm/follow")
@RequiredArgsConstructor
public class FollowController {

    private final FollowMapper followMapper;
    private final FollowService followService;

    @Operation(summary = "某条线索的跟进记录(按跟进日期倒序)")
    @PreAuthorize("hasAuthority('crm:follow:query')")
    @GetMapping("/list")
    public Result<List<Follow>> list(@RequestParam Long leadId) {
        return Result.ok(followMapper.selectList(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getLeadId, leadId)
                .orderByDesc(Follow::getFollowDate)
                .orderByDesc(Follow::getId)));
    }

    @Operation(summary = "新增跟进记录(自动发编号并回写线索的跟进统计)")
    @PreAuthorize("hasAuthority('crm:follow:add')")
    @PostMapping
    public Result<Long> add(@RequestBody Follow follow) {
        return Result.ok(followService.add(follow));
    }
}
