package com.zhyq.park.rule.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.rule.entity.Rule;
import com.zhyq.park.rule.mapper.RuleMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "事件-联动规则")
@RestController
@RequestMapping("/rule")
@RequiredArgsConstructor
public class RuleController {

    private final RuleMapper ruleMapper;

    @Operation(summary = "规则列表")
    @GetMapping("/list")
    public Result<List<Rule>> list() {
        LambdaQueryWrapper<Rule> qw = new LambdaQueryWrapper<>();
        qw.orderByAsc(Rule::getPriority).orderByDesc(Rule::getId);
        return Result.ok(ruleMapper.selectList(qw));
    }

    @Operation(summary = "规则详情")
    @GetMapping("/{id}")
    public Result<Rule> get(@PathVariable Long id) {
        return Result.ok(ruleMapper.selectById(id));
    }

    @Operation(summary = "新增规则")
    @PostMapping
    public Result<Long> add(@RequestBody Rule rule) {
        ruleMapper.insert(rule);
        return Result.ok(rule.getId());
    }

    @Operation(summary = "修改规则")
    @PutMapping
    public Result<Void> update(@RequestBody Rule rule) {
        ruleMapper.updateById(rule);
        return Result.ok();
    }

    @Operation(summary = "删除规则")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ruleMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "启用/停用规则")
    @PostMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable Long id) {
        Rule rule = ruleMapper.selectById(id);
        if (rule != null) {
            rule.setEnabled(rule.getEnabled() != null && rule.getEnabled() == 1 ? 0 : 1);
            ruleMapper.updateById(rule);
        }
        return Result.ok();
    }
}
