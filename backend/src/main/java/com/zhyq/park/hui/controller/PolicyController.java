package com.zhyq.park.hui.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.hui.entity.Policy;
import com.zhyq.park.hui.entity.TenantRef;
import com.zhyq.park.hui.mapper.PolicyMapper;
import com.zhyq.park.hui.mapper.TenantRefMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 惠企服务-政策服务(政策库 + 政策匹配)
 * 状态:1有效 0已过期
 */
@Tag(name = "惠企服务-政策服务")
@RestController
@RequestMapping("/service/policy")
@RequiredArgsConstructor
public class PolicyController {

    /** 状态:有效 */
    public static final int ST_ACTIVE = 1;

    private final PolicyMapper policyMapper;
    private final TenantRefMapper tenantRefMapper;

    @Operation(summary = "分页查询政策")
    @GetMapping("/page")
    public Result<PageResult<Policy>> page(@RequestParam(defaultValue = "1") int pageNo,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) String title,
                                           @RequestParam(required = false) String ptype,
                                           @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Policy> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(title), Policy::getTitle, title)
          .eq(StringUtils.hasText(ptype), Policy::getPtype, ptype)
          .eq(status != null, Policy::getStatus, status)
          .orderByDesc(Policy::getId);
        IPage<Policy> p = policyMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "政策详情")
    @GetMapping("/{id}")
    public Result<Policy> get(@PathVariable Long id) {
        return Result.ok(policyMapper.selectById(id));
    }

    @Operation(summary = "新增政策")
    @PostMapping
    public Result<Long> add(@RequestBody Policy policy) {
        if (policy.getStatus() == null) {
            policy.setStatus(ST_ACTIVE);
        }
        policyMapper.insert(policy);
        return Result.ok(policy.getId());
    }

    @Operation(summary = "修改政策")
    @PutMapping
    public Result<Void> update(@RequestBody Policy policy) {
        policyMapper.updateById(policy);
        return Result.ok();
    }

    @Operation(summary = "删除政策")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        policyMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "政策匹配(按租客行业匹配有效政策)")
    @GetMapping("/match")
    public Result<List<Map<String, Object>>> match(@RequestParam Long tenantRefId) {
        TenantRef tenant = tenantRefMapper.selectById(tenantRefId);
        String industry = tenant == null ? null : tenant.getIndustry();

        LambdaQueryWrapper<Policy> qw = new LambdaQueryWrapper<>();
        qw.eq(Policy::getStatus, ST_ACTIVE).orderByDesc(Policy::getId);
        List<Policy> policies = policyMapper.selectList(qw);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Policy policy : policies) {
            boolean generic = !StringUtils.hasText(policy.getIndustry());
            boolean industryMatch = StringUtils.hasText(industry) && industry.equals(policy.getIndustry());
            if (!generic && !industryMatch) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("id", policy.getId());
            item.put("title", policy.getTitle());
            item.put("source", policy.getSource());
            item.put("ptype", policy.getPtype());
            item.put("industry", policy.getIndustry());
            item.put("publishDate", policy.getPublishDate());
            item.put("deadline", policy.getDeadline());
            item.put("content", policy.getContent());
            item.put("status", policy.getStatus());
            item.put("matchReason", generic ? "通用政策" : "行业匹配:" + industry);
            result.add(item);
        }
        return Result.ok(result);
    }
}
