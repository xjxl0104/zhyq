package com.zhyq.park.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.property.entity.MeetingRoom;
import com.zhyq.park.property.mapper.MeetingRoomMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "物业-会议室")
@RestController
@RequestMapping("/property/meeting/room")
@RequiredArgsConstructor
public class MeetingRoomController {

    private final MeetingRoomMapper meetingRoomMapper;

    @Operation(summary = "分页查询会议室")
    @GetMapping("/page")
    public Result<PageResult<MeetingRoom>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                @RequestParam(defaultValue = "10") int pageSize,
                                                @RequestParam(required = false) String name,
                                                @RequestParam(required = false) Integer status,
                                                @RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<MeetingRoom> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(name), MeetingRoom::getName, name)
          .eq(status != null, MeetingRoom::getStatus, status)
          .eq(projectId != null, MeetingRoom::getProjectId, projectId)
          .orderByDesc(MeetingRoom::getId);
        IPage<MeetingRoom> p = meetingRoomMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "会议室详情")
    @GetMapping("/{id}")
    public Result<MeetingRoom> get(@PathVariable Long id) {
        return Result.ok(meetingRoomMapper.selectById(id));
    }

    @Operation(summary = "全部会议室(下拉)")
    @GetMapping("/list")
    public Result<List<MeetingRoom>> list() {
        return Result.ok(meetingRoomMapper.selectList(
                new LambdaQueryWrapper<MeetingRoom>().orderByDesc(MeetingRoom::getId)));
    }

    @Operation(summary = "新增会议室")
    @PostMapping
    public Result<Long> add(@RequestBody MeetingRoom room) {
        meetingRoomMapper.insert(room);
        return Result.ok(room.getId());
    }

    @Operation(summary = "修改会议室")
    @PutMapping
    public Result<Void> update(@RequestBody MeetingRoom room) {
        meetingRoomMapper.updateById(room);
        return Result.ok();
    }

    @Operation(summary = "删除会议室")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        meetingRoomMapper.deleteById(id);
        return Result.ok();
    }
}
