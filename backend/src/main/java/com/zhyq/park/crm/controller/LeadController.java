package com.zhyq.park.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.crm.entity.Lead;
import com.zhyq.park.crm.mapper.LeadMapper;
import com.zhyq.park.crm.service.LeadImportService;
import com.zhyq.park.crm.service.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
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
    private final LeadService leadService;
    private final LeadImportService leadImportService;

    @Operation(summary = "分页查询线索")
    @PreAuthorize("hasAuthority('crm:lead:query')")
    @GetMapping("/page")
    public Result<PageResult<Lead>> page(@RequestParam(defaultValue = "1") int pageNo,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String contact,
                                         @RequestParam(required = false) String phone,
                                         @RequestParam(required = false) String company,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String source,
                                         @RequestParam(required = false) String leadNo,
                                         @RequestParam(required = false) String grade,
                                         @RequestParam(required = false) String customerType,
                                         @RequestParam(required = false) String ownerName) {
        LambdaQueryWrapper<Lead> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(contact), Lead::getContact, contact)
          .like(StringUtils.hasText(phone), Lead::getPhone, phone)
          .like(StringUtils.hasText(company), Lead::getCompany, company)
          .eq(status != null, Lead::getStatus, status)
          .eq(StringUtils.hasText(source), Lead::getSource, source)
          .like(StringUtils.hasText(leadNo), Lead::getLeadNo, leadNo)
          .eq(StringUtils.hasText(grade), Lead::getGrade, grade)
          .eq(StringUtils.hasText(customerType), Lead::getCustomerType, customerType)
          .like(StringUtils.hasText(ownerName), Lead::getOwnerName, ownerName)
          .orderByDesc(Lead::getId);
        IPage<Lead> p = leadMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "线索统计")
    @PreAuthorize("hasAuthority('crm:lead:query')")
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
                .filter(l -> Integer.valueOf(LeadService.ST_LOST).equals(l.getStatus()))
                .count();
        long monthConverted = all.stream()
                .filter(l -> l.getCreateTime() != null && !l.getCreateTime().isBefore(monthStart))
                .filter(l -> Integer.valueOf(LeadService.ST_SIGNED).equals(l.getStatus()))
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
    @PreAuthorize("hasAuthority('crm:lead:query')")
    @GetMapping("/{id}")
    public Result<Lead> get(@PathVariable Long id) {
        return Result.ok(leadMapper.selectById(id));
    }

    @Operation(summary = "新增线索(自动发客户编号 KH-xxxx)")
    @PreAuthorize("hasAuthority('crm:lead:add')")
    @PostMapping
    public Result<Long> add(@RequestBody Lead lead) {
        return Result.ok(leadService.create(lead));
    }

    @Operation(summary = "修改线索(白名单字段,编号与跟进统计不可改)")
    @PreAuthorize("hasAuthority('crm:lead:edit')")
    @PutMapping
    public Result<Void> update(@RequestBody Lead lead) {
        leadService.update(lead);
        return Result.ok();
    }

    @Operation(summary = "删除线索")
    @PreAuthorize("hasAuthority('crm:lead:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        leadMapper.deleteById(id);
        return Result.ok();
    }

    /**
     * 导入《云仓产业园客户信息收集与回访登记表》的「客户信息登记表」页。
     * 按表头文字认列,不依赖列顺序;同姓名+电话已存在则跳过,重复导入同一份文件不会翻倍。
     */
    @Operation(summary = "导入客户信息登记表(xlsx/xls)")
    @PreAuthorize("hasAuthority('crm:lead:import')")
    @PostMapping("/import")
    public Result<LeadImportService.ImportResult> importExcel(@RequestParam("file") MultipartFile file) {
        return Result.ok(leadImportService.importWorkbook(file));
    }

    @Operation(summary = "转客户(线索转化,置为已签约/已成交)")
    @PreAuthorize("hasAuthority('crm:lead:edit')")
    @PostMapping("/{id}/convert")
    public Result<Void> convert(@PathVariable Long id) {
        Lead lead = new Lead();
        lead.setId(id);
        lead.setStatus(LeadService.ST_SIGNED);
        leadMapper.updateById(lead);
        return Result.ok();
    }
}
