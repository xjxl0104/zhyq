package com.zhyq.park.acc.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.acc.entity.AccAccessRecord;
import com.zhyq.park.acc.mapper.AccAccessRecordMapper;
import com.zhyq.park.acc.service.AccAccessService;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 便捷通行-门禁通行记录(#21)。只增不改的流水,按 gate/space/time 筛。
 */
@Tag(name = "便捷通行-门禁")
@RestController
@RequestMapping("/acc/access")
@RequiredArgsConstructor
public class AccAccessController {

    private final AccAccessService accessService;
    private final AccAccessRecordMapper accessMapper;

    @Operation(summary = "分页查询通行流水(按门/空间/时间筛)")
    @GetMapping("/page")
    public Result<PageResult<AccAccessRecord>> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String gateCode,
            @RequestParam(required = false) Long spaceId,
            @RequestParam(required = false) String personType,
            @RequestParam(required = false) Integer direction,
            @RequestParam(required = false) Integer result,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to) {
        LambdaQueryWrapper<AccAccessRecord> qw = new LambdaQueryWrapper<>();
        qw.eq(gateCode != null && !gateCode.isBlank(), AccAccessRecord::getGateCode, gateCode)
          .eq(spaceId != null, AccAccessRecord::getSpaceId, spaceId)
          .eq(personType != null && !personType.isBlank(), AccAccessRecord::getPersonType, personType)
          .eq(direction != null, AccAccessRecord::getDirection, direction)
          .eq(result != null, AccAccessRecord::getResult, result)
          .ge(from != null, AccAccessRecord::getAccessTime, from)
          .le(to != null, AccAccessRecord::getAccessTime, to)
          .orderByDesc(AccAccessRecord::getId);
        IPage<AccAccessRecord> p = accessMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "登记通行(裸插流水，可选Mock开门)")
    @PostMapping("/record")
    public Result<Long> record(@RequestBody AccAccessRecord record) {
        return Result.ok(accessService.record(record));
    }
}
