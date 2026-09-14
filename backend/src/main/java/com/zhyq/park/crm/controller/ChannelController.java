package com.zhyq.park.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.crm.entity.Channel;
import com.zhyq.park.crm.entity.ChannelFollow;
import com.zhyq.park.crm.mapper.ChannelFollowMapper;
import com.zhyq.park.crm.mapper.ChannelMapper;
import com.zhyq.park.crm.service.ChannelFollowService;
import com.zhyq.park.crm.service.ChannelImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "招商-中介管理")
@RestController
@RequestMapping("/crm/channel")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelMapper channelMapper;
    private final ChannelFollowMapper channelFollowMapper;
    private final ChannelFollowService channelFollowService;
    private final ChannelImportService channelImportService;

    @Operation(summary = "分页查询中介")
    @GetMapping("/page")
    public Result<PageResult<Channel>> page(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String name,
                                            @RequestParam(required = false) String contact,
                                            @RequestParam(required = false) String agencyType,
                                            @RequestParam(required = false) String grade,
                                            @RequestParam(required = false) String ownerName,
                                            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Channel> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(name), Channel::getName, name)
          .like(StringUtils.hasText(contact), Channel::getContact, contact)
          .eq(StringUtils.hasText(agencyType), Channel::getAgencyType, agencyType)
          .eq(StringUtils.hasText(grade), Channel::getGrade, grade)
          .like(StringUtils.hasText(ownerName), Channel::getOwnerName, ownerName)
          .eq(status != null, Channel::getStatus, status)
          .orderByDesc(Channel::getId);
        IPage<Channel> p = channelMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "中介统计卡片")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> m = new HashMap<>();
        m.put("total", channelMapper.selectCount(new LambdaQueryWrapper<>()));
        m.put("active", channelMapper.selectCount(new LambdaQueryWrapper<Channel>().eq(Channel::getStatus, 1)));
        m.put("gradeA", channelMapper.selectCount(new LambdaQueryWrapper<Channel>().likeRight(Channel::getGrade, "A")));
        m.put("monthNew", channelMapper.selectCount(new LambdaQueryWrapper<Channel>()
                .ge(Channel::getCreateTime, LocalDate.now().withDayOfMonth(1).atStartOfDay())));
        List<Channel> counts = channelMapper.selectList(new LambdaQueryWrapper<Channel>()
                .select(Channel::getReferralCount, Channel::getDealCount));
        m.put("referral", counts.stream().mapToInt(c -> c.getReferralCount() == null ? 0 : c.getReferralCount()).sum());
        m.put("deal", counts.stream().mapToInt(c -> c.getDealCount() == null ? 0 : c.getDealCount()).sum());
        return Result.ok(m);
    }

    @Operation(summary = "导入中介登记表(xlsx/xls/et/csv/txt/docx)")
    @PostMapping("/import")
    public Result<ChannelImportService.ImportResult> importFile(@RequestParam("file") MultipartFile file) {
        return Result.ok(channelImportService.importFile(file));
    }

    @Operation(summary = "中介详情")
    @GetMapping("/{id}")
    public Result<Channel> get(@PathVariable Long id) {
        return Result.ok(channelMapper.selectById(id));
    }

    @Operation(summary = "新增中介(自动发编号)")
    @PostMapping
    public Result<Long> add(@RequestBody Channel channel) {
        channel.setId(null);
        channel.setAgencyNo(channelFollowService.nextAgencyNo());
        clearFollowStats(channel);
        channelMapper.insert(channel);
        return Result.ok(channel.getId());
    }

    @Operation(summary = "修改中介")
    @PutMapping
    public Result<Void> update(@RequestBody Channel channel) {
        // 编号与跟进统计由服务端维护,置空后 updateById 不会覆盖
        channel.setAgencyNo(null);
        clearFollowStats(channel);
        channelMapper.updateById(channel);
        return Result.ok();
    }

    @Operation(summary = "删除中介")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        channelMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "全部合作中的中介(下拉)")
    @GetMapping("/list")
    public Result<List<Channel>> list() {
        return Result.ok(channelMapper.selectList(new LambdaQueryWrapper<Channel>().eq(Channel::getStatus, 1)));
    }

    @Operation(summary = "某个中介的跟进记录(按跟进日期倒序)")
    @GetMapping("/follow/list")
    public Result<List<ChannelFollow>> followList(@RequestParam Long channelId) {
        return Result.ok(channelFollowMapper.selectList(new LambdaQueryWrapper<ChannelFollow>()
                .eq(ChannelFollow::getChannelId, channelId)
                .orderByDesc(ChannelFollow::getFollowDate)
                .orderByDesc(ChannelFollow::getId)));
    }

    @Operation(summary = "新增中介跟进记录(自动发编号并回写跟进统计)")
    @PostMapping("/follow")
    public Result<Long> addFollow(@RequestBody ChannelFollow follow) {
        return Result.ok(channelFollowService.add(follow));
    }

    private static void clearFollowStats(Channel channel) {
        channel.setFollowCount(null);
        channel.setLastFollowDate(null);
        channel.setNextFollow(null);
    }
}
