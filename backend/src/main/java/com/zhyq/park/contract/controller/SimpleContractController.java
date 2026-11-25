package com.zhyq.park.contract.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.audit.OperationLog;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.contract.entity.SimpleContract;
import com.zhyq.park.contract.mapper.SimpleContractMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 简易合同管理(主要用于注册公司挂靠,按楼层 + 自编号房号 X01/X02 管理)。
 */
@Tag(name = "合同管理-简易合同")
@RestController
@RequestMapping("/contract/simple")
@RequiredArgsConstructor
public class SimpleContractController {

    private static final String DEFAULT_PARK = "DI PARK数智云仓产业园";

    private final SimpleContractMapper mapper;
    private final JdbcTemplate jdbc;

    @Operation(summary = "查询简易合同列表(支持按楼层、关键字、状态筛选)")
    @GetMapping("/list")
    public Result<List<SimpleContract>> list(@RequestParam(required = false) Integer floorNo,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<SimpleContract> qw = new LambdaQueryWrapper<SimpleContract>()
                .eq(floorNo != null, SimpleContract::getFloorNo, floorNo)
                .eq(status != null, SimpleContract::getStatus, status)
                .orderByAsc(SimpleContract::getFloorNo)
                .orderByAsc(SimpleContract::getRoomCode)
                .orderByAsc(SimpleContract::getId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(SimpleContract::getRoomCode, kw)
                    .or().like(SimpleContract::getCompanyName, kw)
                    .or().like(SimpleContract::getContractNo, kw)
                    .or().like(SimpleContract::getContactName, kw)
                    .or().like(SimpleContract::getCreditCode, kw));
        }
        List<SimpleContract> rows = mapper.selectList(qw);
        fillAttachmentCounts(rows);
        return Result.ok(rows);
    }

    @Operation(summary = "获取指定楼层的下一个自编号房号(如 101, 102, 201)")
    @GetMapping("/next-room")
    public Result<Map<String, Object>> nextRoom(@RequestParam(defaultValue = "1") int floorNo) {
        if (floorNo < 1 || floorNo > 99) throw new BizException("楼层号需在 1 ~ 99 之间");
        String code = computeNextRoomCode(floorNo);
        return Result.ok(Map.of(
                "floorNo", floorNo,
                "roomCode", code,
                "contractNo", "JY-" + LocalDate.now().getYear() + "-" + code,
                "regAddress", DEFAULT_PARK + floorNo + "层" + code + "室"
        ));
    }

    @Operation(summary = "新增简易合同")
    @OperationLog(module = "简易合同", action = "新增")
    @PostMapping
    public Result<SimpleContract> add(@RequestBody SimpleContract input) {
        prepareAndValidate(input, true);
        mapper.insert(input);
        input.setAttachmentCount(0);
        return Result.ok(input);
    }

    @Operation(summary = "修改简易合同")
    @OperationLog(module = "简易合同", action = "修改")
    @PutMapping
    public Result<SimpleContract> update(@RequestBody SimpleContract input) {
        if (input == null || input.getId() == null) throw new BizException("缺少合同 ID");
        SimpleContract existed = mapper.selectById(input.getId());
        if (existed == null) throw new BizException(404, "简易合同不存在");
        prepareAndValidate(input, false);
        mapper.updateById(input);
        SimpleContract fresh = mapper.selectById(input.getId());
        fillAttachmentCounts(List.of(fresh));
        return Result.ok(fresh);
    }

    @Operation(summary = "删除简易合同")
    @OperationLog(module = "简易合同", action = "删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return Result.ok();
    }

    private void prepareAndValidate(SimpleContract c, boolean creating) {
        if (c == null) throw new BizException("合同信息不能为空");
        if (c.getFloorNo() == null || c.getFloorNo() < 1 || c.getFloorNo() > 99) {
            throw new BizException("请选择有效的楼层（1~99楼）");
        }
        if (!StringUtils.hasText(c.getCompanyName())) {
            throw new BizException("请填写注册公司名称");
        }
        c.setCompanyName(c.getCompanyName().trim());
        if (!StringUtils.hasText(c.getRoomCode())) {
            c.setRoomCode(computeNextRoomCode(c.getFloorNo()));
        } else {
            c.setRoomCode(c.getRoomCode().trim().toUpperCase());
        }
        if (!StringUtils.hasText(c.getContractNo())) {
            c.setContractNo("JY-" + LocalDate.now().getYear() + "-" + c.getRoomCode());
        } else {
            c.setContractNo(c.getContractNo().trim());
        }
        if (!StringUtils.hasText(c.getRegAddress())) {
            c.setRegAddress(DEFAULT_PARK + c.getFloorNo() + "层" + c.getRoomCode() + "室");
        }
        if (c.getStatus() == null) c.setStatus(1);
        if (c.getAnnualFee() == null) c.setAnnualFee(BigDecimal.ZERO);
        if (c.getStartDate() != null && c.getEndDate() != null && c.getEndDate().isBefore(c.getStartDate())) {
            throw new BizException("合同到期日不能早于起始日");
        }
        // 同楼层同一自编号房号不允许同时存在两份生效中的简易合同
        if (Objects.equals(c.getStatus(), 1)) {
            LambdaQueryWrapper<SimpleContract> dup = new LambdaQueryWrapper<SimpleContract>()
                    .eq(SimpleContract::getFloorNo, c.getFloorNo())
                    .eq(SimpleContract::getRoomCode, c.getRoomCode())
                    .eq(SimpleContract::getStatus, 1)
                    .ne(!creating && c.getId() != null, SimpleContract::getId, c.getId());
            if (mapper.selectCount(dup) > 0) {
                throw new BizException(c.getFloorNo() + "楼自编号房号「" + c.getRoomCode() + "」已有生效中的简易合同，请更换房号或先注销原记录");
            }
        }
    }

    private String computeNextRoomCode(int floorNo) {
        List<SimpleContract> existing = mapper.selectList(new LambdaQueryWrapper<SimpleContract>()
                .eq(SimpleContract::getFloorNo, floorNo));
        Pattern p = Pattern.compile("^" + floorNo + "(\\d{2,})$");
        int max = 0;
        for (SimpleContract s : existing) {
            if (s.getRoomCode() == null) continue;
            Matcher m = p.matcher(s.getRoomCode().trim());
            if (m.matches()) {
                try {
                    int n = Integer.parseInt(m.group(1));
                    if (n > max) max = n;
                } catch (NumberFormatException ignored) { /* ignore */ }
            }
        }
        return floorNo + String.format("%02d", max + 1);
    }

    private void fillAttachmentCounts(List<SimpleContract> rows) {
        if (rows == null || rows.isEmpty()) return;
        String ids = rows.stream().map(SimpleContract::getId).filter(Objects::nonNull)
                .map(String::valueOf).collect(Collectors.joining(","));
        if (ids.isEmpty()) return;
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT biz_id, COUNT(*) AS c FROM sys_file WHERE deleted = 0 AND biz_type = 'simple_contract'"
                        + " AND biz_id IN (" + ids + ") GROUP BY biz_id")) {
            counts.put(((Number) r.get("biz_id")).longValue(), ((Number) r.get("c")).intValue());
        }
        for (SimpleContract row : rows) {
            row.setAttachmentCount(counts.getOrDefault(row.getId(), 0));
        }
    }
}
