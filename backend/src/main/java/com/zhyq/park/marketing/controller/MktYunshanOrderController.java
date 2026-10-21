package com.zhyq.park.marketing.controller;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.service.MktYunshanVolumeOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 手动触发云山发货票数 → 计佣汇总订单；幂等，已生成的日期不会重复计佣。 */
@Tag(name = "全民营销-云山发货量汇总订单")
@RestController
@RequestMapping("/crm/marketing/yunshan/orders")
@RequiredArgsConstructor
public class MktYunshanOrderController {
    private final MktYunshanVolumeOrderService service;

    public record GenerateInput(Long mapId, LocalDate startDate, LocalDate endDate) {}

    @Operation(summary = "按已拉取的发货票数生成计佣汇总订单（可限定客户关联和日期区间）")
    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('crm:marketing:order:edit')")
    public Result<MktYunshanVolumeOrderService.Result> generate(@RequestBody(required = false) GenerateInput input) {
        Long mapId = input == null ? null : input.mapId();
        LocalDate start = input == null ? null : input.startDate();
        LocalDate end = input == null ? null : input.endDate();
        if (start != null && end != null && start.isAfter(end)) throw new BizException("开始日期不能晚于结束日期");
        return Result.ok(service.generate(mapId, start, end));
    }
}
