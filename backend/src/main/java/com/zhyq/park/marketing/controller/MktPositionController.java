package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.marketing.entity.MktPosition;
import com.zhyq.park.marketing.mapper.MktPositionMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktPositionReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 岗位与份额(PARK-MKT-001 §2.7 / §3):份额严格递增顶格 100;岗位数 2/4 切换写审计。 */
@Tag(name = "全民营销-岗位与份额")
@RestController
@RequestMapping("/crm/marketing/position")
@RequiredArgsConstructor
public class MktPositionController {

    private static final String MODULE = "marketing";
    private static final String KEY_DEPTH = "ladder_depth";

    private final MktPositionMapper positionMapper;
    private final MktPositionReviewService reviewService;
    private final MktAuditService auditService;
    private final BizSettings bizSettings;
    private final JdbcTemplate jdbc;

    @Operation(summary = "岗位列表")
    @PreAuthorize("hasAuthority('crm:marketing:position:query')")
    @GetMapping("/list")
    public Result<List<MktPosition>> list() {
        return Result.ok(positionMapper.selectList(new LambdaQueryWrapper<MktPosition>().orderByAsc(MktPosition::getSort)));
    }

    @Operation(summary = "批量保存份额与门槛")
    @PreAuthorize("hasAuthority('crm:marketing:position:config')")
    @PutMapping
    @Transactional
    public Result<Void> update(@RequestBody List<MktPosition> list) {
        if (list == null || list.isEmpty()) throw new BizException("提交内容为空");
        // 以数据库全量为基准合并本次提交:只校验提交子集会被"只提 P2=100 跳过 P1"绕过,
        // 导致 DB 出现 P1=50/P2=100/P3=85/P4=100 的非法阶梯(引擎跳级)。
        Map<Long, MktPosition> merged = new LinkedHashMap<>();
        for (MktPosition p : positionMapper.selectList(new LambdaQueryWrapper<MktPosition>().orderByAsc(MktPosition::getSort))) {
            merged.put(p.getId(), p);
        }
        Map<Long, MktPosition> beforeById = new LinkedHashMap<>(merged);
        for (MktPosition p : list) {
            if (p.getId() == null) throw new BizException("岗位 id 必填");
            MktPosition cur = merged.get(p.getId());
            if (cur == null) throw new BizException("岗位不存在: " + p.getId());
            // 白名单:只接受可编辑字段,code/sort 是阶梯身份与排序锚点,禁止改
            if (p.getName() != null) cur.setName(p.getName());
            if (p.getSharePct() != null && !p.getSharePct().equals(cur.getSharePct()))
                throw new BizException("岗位固定比例已停用，请由 P4 按客户设置每单金额");
            if (p.getShareMinPct() != null && !p.getShareMinPct().equals(cur.getShareMinPct()))
                throw new BizException("岗位固定比例下限已停用");
            if (p.getLockCap() != null) cur.setLockCap(p.getLockCap());
            if (p.getPromoteAmount() != null) cur.setPromoteAmount(p.getPromoteAmount());
            if (p.getPromoteOrders() != null) cur.setPromoteOrders(p.getPromoteOrders());
            if (p.getTeamCounted() != null) cur.setTeamCounted(p.getTeamCounted());
            if (p.getDemoteEnabled() != null) cur.setDemoteEnabled(p.getDemoteEnabled());
        }
        for (MktPosition p : list) {
            MktPosition cur = merged.get(p.getId());
            positionMapper.update(null, new LambdaUpdateWrapper<MktPosition>()
                    .eq(MktPosition::getId, cur.getId())
                    .set(MktPosition::getName, cur.getName())
                    .set(MktPosition::getLockCap, cur.getLockCap())
                    .set(MktPosition::getPromoteAmount, cur.getPromoteAmount())
                    .set(MktPosition::getPromoteOrders, cur.getPromoteOrders())
                    .set(MktPosition::getTeamCounted, cur.getTeamCounted())
                    .set(MktPosition::getDemoteEnabled, cur.getDemoteEnabled()));
            auditService.log("position.config", "position", cur.getId(), null, beforeById.get(cur.getId()), cur);
        }
        return Result.ok();
    }

    @Operation(summary = "当前岗位数(2/4)")
    @PreAuthorize("hasAuthority('crm:marketing:position:query')")
    @GetMapping("/depth")
    public Result<Integer> depth() {
        return Result.ok(bizSettings.getInt(MODULE, KEY_DEPTH, 2));
    }

    @Operation(summary = "切换岗位数(2/4),写审计;4 级需前端常驻合规提示")
    @PreAuthorize("hasAuthority('crm:marketing:position:config')")
    @PutMapping("/depth")
    public Result<Void> setDepth(@RequestBody Map<String, Integer> body) {
        throw new BizException("岗位数量切换已停用，P1–P4 作为角色保留，金额由 P4 自定义");
    }

    @Operation(summary = "立即执行晋升/降级复核,返回调整人数")
    @PreAuthorize("hasAuthority('crm:marketing:position:config')")
    @PostMapping("/review-now")
    public Result<Integer> reviewNow() {
        return Result.ok(reviewService.reviewAll());
    }
}
