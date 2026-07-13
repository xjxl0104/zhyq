package com.zhyq.park.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.finance.entity.Setting;
import com.zhyq.park.finance.mapper.SettingMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "财务-业务配置")
@RestController
@RequestMapping("/finance/setting")
@RequiredArgsConstructor
public class SettingController {

    private final SettingMapper settingMapper;

    @Operation(summary = "按模块查询配置列表")
    @GetMapping("/list")
    public Result<List<Setting>> list(@RequestParam(required = false) String module) {
        LambdaQueryWrapper<Setting> qw = new LambdaQueryWrapper<>();
        qw.eq(StringUtils.hasText(module), Setting::getModule, module)
          .orderByAsc(Setting::getId);
        return Result.ok(settingMapper.selectList(qw));
    }

    @Operation(summary = "批量保存配置")
    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/batch")
    public Result<Void> batch(@RequestBody List<Setting> settings) {
        if (settings != null) {
            for (Setting s : settings) {
                settingMapper.updateById(s);
            }
        }
        return Result.ok();
    }

    @Operation(summary = "新增配置")
    @PostMapping
    public Result<Long> add(@RequestBody Setting setting) {
        settingMapper.insert(setting);
        return Result.ok(setting.getId());
    }

    @Operation(summary = "删除配置")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        settingMapper.deleteById(id);
        return Result.ok();
    }
}
