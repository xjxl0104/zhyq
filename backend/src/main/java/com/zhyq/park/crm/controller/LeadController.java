package com.zhyq.park.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.crm.entity.Lead;
import com.zhyq.park.crm.mapper.LeadMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "招商-线索")
@RestController
@RequestMapping("/crm/lead")
@RequiredArgsConstructor
public class LeadController {

    private final LeadMapper leadMapper;

    @Operation(summary = "分页查询线索")
    @GetMapping("/page")
    public Result<PageResult<Lead>> page(@RequestParam(defaultValue = "1") int pageNo,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String contact,
                                         @RequestParam(required = false) String phone,
                                         @RequestParam(required = false) String company,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String source) {
        LambdaQueryWrapper<Lead> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(contact), Lead::getContact, contact)
          .like(StringUtils.hasText(phone), Lead::getPhone, phone)
          .like(StringUtils.hasText(company), Lead::getCompany, company)
          .eq(status != null, Lead::getStatus, status)
          .eq(StringUtils.hasText(source), Lead::getSource, source)
          .orderByDesc(Lead::getId);
        IPage<Lead> p = leadMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "线索统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        List<Lead> all = leadMapper.selectList(new LambdaQueryWrapper<>());
        LocalDate today = LocalDate.now();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

        long total = all.size();
        long todayNew = all.stream()
                .filter(l -> l.getCreateTime() != null && l.getCreateTime().toLocalDate().isEqual(today))
                .count();
        long monthNew = all.stream()
                .filter(l -> l.getCreateTime() != null && !l.getCreateTime().isBefore(monthStart))
                .count();
        long invalid = all.stream()
                .filter(l -> Integer.valueOf(6).equals(l.getStatus()))
                .count();
        long monthConverted = all.stream()
                .filter(l -> l.getCreateTime() != null && !l.getCreateTime().isBefore(monthStart))
                .filter(l -> Integer.valueOf(5).equals(l.getStatus()))
                .count();
        BigDecimal convertRate = monthNew == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(monthConverted * 100.0 / monthNew).setScale(1, RoundingMode.HALF_UP);

        Map<String, Object> map = new HashMap<>();
        map.put("total", total);
        map.put("todayNew", todayNew);
        map.put("monthNew", monthNew);
        map.put("invalid", invalid);
        map.put("convertRate", convertRate);
        return Result.ok(map);
    }

    @Operation(summary = "线索详情")
    @GetMapping("/{id}")
    public Result<Lead> get(@PathVariable Long id) {
        return Result.ok(leadMapper.selectById(id));
    }

    @Operation(summary = "新增线索")
    @PostMapping
    public Result<Long> add(@RequestBody Lead lead) {
        leadMapper.insert(lead);
        return Result.ok(lead.getId());
    }

    @Operation(summary = "修改线索")
    @PutMapping
    public Result<Void> update(@RequestBody Lead lead) {
        leadMapper.updateById(lead);
        return Result.ok();
    }

    @Operation(summary = "删除线索")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        leadMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "转客户(线索转化)")
    @PostMapping("/{id}/convert")
    public Result<Void> convert(@PathVariable Long id) {
        Lead lead = new Lead();
        lead.setId(id);
        lead.setStatus(5);
        leadMapper.updateById(lead);
        return Result.ok();
    }
}
