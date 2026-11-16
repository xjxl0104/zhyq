package com.zhyq.park.energy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.audit.OperationLog;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.energy.entity.FeeNotice;
import com.zhyq.park.energy.entity.FeePeriod;
import com.zhyq.park.energy.entity.Meter;
import com.zhyq.park.energy.mapper.FeePeriodMapper;
import com.zhyq.park.energy.service.FeeNoticeExportService;
import com.zhyq.park.energy.service.FeeNoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 月度缴费账期(电费 / 水费 / 垃圾处理费 / 缴费汇总 四个页面共用)。
 *
 * <p>流程:选月份 → 填南网/光伏度数、各表本期读数、垃圾总额 → 生成通知单 → 核对/打印/导出 → 确认出账。</p>
 */
@Tag(name = "能耗管理-缴费账期与通知单")
@RestController
@RequestMapping("/energy/fee-period")
@RequiredArgsConstructor
public class FeePeriodController {

    private final FeePeriodMapper periodMapper;
    private final FeeNoticeService service;
    private final FeeNoticeExportService exportService;

    @Data
    public static class OpenRequest {
        private String period;
        private Long projectId;
    }

    @Data
    public static class MeterFlagRequest {
        private Long meterId;
        private Integer sharePublic;
        private BigDecimal leaseArea;
        /** 同区域的表一起改面积 */
        private boolean applyAreaToUnit = true;
    }

    @Operation(summary = "分页查询缴费账期")
    @GetMapping("/page")
    public Result<PageResult<FeePeriod>> page(@RequestParam(defaultValue = "1") int pageNo,
                                              @RequestParam(defaultValue = "10") int pageSize,
                                              @RequestParam(required = false) String period,
                                              @RequestParam(required = false) String status) {
        IPage<FeePeriod> p = periodMapper.selectPage(new Page<>(pageNo, pageSize), new LambdaQueryWrapper<FeePeriod>()
                .eq(StringUtils.hasText(period), FeePeriod::getPeriod, period)
                .eq(StringUtils.hasText(status), FeePeriod::getStatus, status)
                .orderByDesc(FeePeriod::getPeriod));
        return Result.ok(PageResult.of(p.getTotal(), service.decorate(p.getRecords())));
    }

    @Operation(summary = "年月表:某一年每个月的总用电量/电费、总用水量/水费、垃圾费、应收合计与已留存单据数")
    @GetMapping("/overview")
    public Result<List<FeeNoticeService.MonthOverview>> overview(@RequestParam int year) {
        return Result.ok(service.overview(year));
    }

    @Operation(summary = "打开某月账期(没有就新建,单价与抬头沿用上月)")
    @PostMapping("/open")
    public Result<FeePeriod> open(@RequestBody OpenRequest req) {
        return Result.ok(service.open(req.getPeriod(), req.getProjectId()));
    }

    @Operation(summary = "账期详情")
    @GetMapping("/{id}")
    public Result<FeePeriod> get(@PathVariable Long id) {
        return Result.ok(service.require(id));
    }

    @Operation(summary = "修改账期录入项(总度数/单价/垃圾总额/通知单抬头)")
    @OperationLog(module = "缴费账期", action = "修改")
    @PutMapping
    public Result<FeePeriod> update(@RequestBody FeePeriod period) {
        return Result.ok(service.update(period));
    }

    @Operation(summary = "删除草稿账期(连同通知单)")
    @OperationLog(module = "缴费账期", action = "删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @Operation(summary = "电表/水表工作表:各分表本期读数、用量、公摊与金额")
    @GetMapping("/{id}/sheet")
    public Result<FeeNoticeService.Sheet> sheet(@PathVariable Long id, @RequestParam String energyType) {
        return Result.ok(service.sheet(id, energyType));
    }

    @Operation(summary = "批量保存本期读数(同表同账期只留一条)")
    @OperationLog(module = "缴费账期", action = "录入读数")
    @PostMapping("/{id}/readings")
    public Result<Map<String, Object>> saveReadings(@PathVariable Long id,
                                                    @RequestBody List<FeeNoticeService.ReadingInput> inputs) {
        int saved = service.saveReadings(id, inputs);
        return Result.ok(Map.of("saved", saved));
    }

    @Operation(summary = "改表计的参与公摊 / 计租面积")
    @OperationLog(module = "缴费账期", action = "修改表计分摊设置")
    @PutMapping("/meter-flags")
    public Result<Void> meterFlags(@RequestBody MeterFlagRequest req) {
        service.updateMeterFlags(req.getMeterId(), req.getSharePublic(), req.getLeaseArea(), req.isApplyAreaToUnit());
        return Result.ok();
    }

    @Data
    public static class IdsRequest {
        private List<Long> ids;
    }

    @Operation(summary = "新增区域/用户(一块租户分表);带 id 即修改")
    @OperationLog(module = "缴费账期", action = "保存区域用户")
    @PostMapping("/meter")
    public Result<Meter> saveMeter(@RequestBody FeeNoticeService.MeterInput input) {
        return Result.ok(service.saveMeter(input));
    }

    @Operation(summary = "删除区域/用户(批量,按表计 id)")
    @OperationLog(module = "缴费账期", action = "删除区域用户")
    @PostMapping("/meter/delete")
    public Result<Map<String, Object>> deleteMeters(@RequestBody IdsRequest req) {
        return Result.ok(Map.of("deleted", service.deleteMeters(req.getIds())));
    }

    @Operation(summary = "删除通知单(单张 / 多选)")
    @OperationLog(module = "缴费账期", action = "删除通知单")
    @PostMapping("/{id}/notices/delete")
    public Result<Map<String, Object>> deleteNotices(@PathVariable Long id, @RequestBody IdsRequest req) {
        return Result.ok(service.deleteNotices(id, req.getIds(), false));
    }

    @Operation(summary = "一键删除本期全部通知单")
    @OperationLog(module = "缴费账期", action = "删除全部通知单")
    @PostMapping("/{id}/notices/delete-all")
    public Result<Map<String, Object>> deleteAllNotices(@PathVariable Long id) {
        return Result.ok(service.deleteNotices(id, null, true));
    }

    @Operation(summary = "垃圾费工作表:各单元计租面积占比与分摊金额")
    @GetMapping("/{id}/garbage-sheet")
    public Result<FeeNoticeService.GarbageSheet> garbageSheet(@PathVariable Long id) {
        return Result.ok(service.garbageSheet(id));
    }

    @Operation(summary = "生成/重新生成本期全部缴费通知单")
    @OperationLog(module = "缴费账期", action = "生成通知单")
    @PostMapping("/{id}/generate")
    public Result<FeeNoticeService.GenerateResult> generate(@PathVariable Long id) {
        return Result.ok(service.generate(id));
    }

    @Operation(summary = "本期全部通知单(含明细行)")
    @GetMapping("/{id}/notices")
    public Result<List<FeeNotice>> notices(@PathVariable Long id) {
        return Result.ok(service.notices(id));
    }

    @Operation(summary = "修改通知单的手工项(已付/前期欠费/其他费用/名称/备注)")
    @OperationLog(module = "缴费账期", action = "修改通知单")
    @PutMapping("/notice")
    public Result<FeeNotice> updateNotice(@RequestBody FeeNotice notice) {
        return Result.ok(service.updateNotice(notice));
    }

    @Operation(summary = "确认出账:每张通知单生成一张能源费账单,账期锁定")
    @OperationLog(module = "缴费账期", action = "确认出账")
    @PostMapping("/{id}/confirm")
    public Result<Map<String, Object>> confirm(@PathVariable Long id) {
        return Result.ok(service.confirm(id));
    }

    @Operation(summary = "撤销确认(已收款的账单会拦下)")
    @OperationLog(module = "缴费账期", action = "撤销确认")
    @PostMapping("/{id}/revoke")
    public Result<Map<String, Object>> revoke(@PathVariable Long id) {
        return Result.ok(service.revoke(id));
    }

    @Operation(summary = "导出 Excel:应收收费明细表 + 电表/水表/垃圾汇总 + 每户一张通知单")
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        FeePeriod p = service.require(id);
        byte[] body = exportService.export(p, service.notices(id),
                service.sheet(id, "电"), service.sheet(id, "水"), service.garbageSheet(id));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(p.getPeriod() + "应收收费明细表.xlsx", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
