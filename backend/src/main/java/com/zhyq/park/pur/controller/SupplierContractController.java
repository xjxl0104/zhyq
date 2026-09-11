package com.zhyq.park.pur.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.pur.entity.Supplier;
import com.zhyq.park.pur.entity.SupplierContract;
import com.zhyq.park.pur.mapper.SupplierContractMapper;
import com.zhyq.park.pur.mapper.SupplierMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供应商合同(V53)。与租赁合同 biz_contract 分表,互不影响。
 *
 * <p>状态 1草稿 2执行中 3已到期 4已终止;流转一律条件更新抢状态(项目硬约束)。
 */
@Tag(name = "供应商管理-供应商合同")
@RestController
@RequestMapping("/pur/supplier-contract")
@RequiredArgsConstructor
public class SupplierContractController {

    /** 1草稿 2执行中 3已到期 4已终止 */
    private static final int ST_DRAFT = 1;
    private static final int ST_RUNNING = 2;
    private static final int ST_EXPIRED = 3;
    private static final int ST_TERMINATED = 4;

    /** 「即将到期」口径:执行中且 30 天内到期 */
    private static final int EXPIRING_DAYS = 30;

    private final SupplierContractMapper contractMapper;
    private final SupplierMapper supplierMapper;

    @Operation(summary = "分页查询供应商合同")
    @PreAuthorize("hasAuthority('pur:supplierContract:query')")
    @GetMapping("/page")
    public Result<PageResult<SupplierContract>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                     @RequestParam(defaultValue = "10") int pageSize,
                                                     @RequestParam(required = false) Long supplierId,
                                                     @RequestParam(required = false) String name,
                                                     @RequestParam(required = false) String code,
                                                     @RequestParam(required = false) String contractType,
                                                     @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<SupplierContract> qw = new LambdaQueryWrapper<>();
        qw.eq(supplierId != null, SupplierContract::getSupplierId, supplierId)
          .like(StringUtils.hasText(name), SupplierContract::getName, name)
          .like(StringUtils.hasText(code), SupplierContract::getCode, code)
          .eq(StringUtils.hasText(contractType), SupplierContract::getContractType, contractType)
          .eq(status != null, SupplierContract::getStatus, status)
          .orderByDesc(SupplierContract::getId);
        IPage<SupplierContract> p = contractMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        fillSupplierNames(p.getRecords());
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "供应商合同统计")
    @PreAuthorize("hasAuthority('pur:supplierContract:query')")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> map = new HashMap<>();
        map.put("total", contractMapper.selectCount(new LambdaQueryWrapper<>()));
        map.put("running", contractMapper.selectCount(new LambdaQueryWrapper<SupplierContract>()
                .eq(SupplierContract::getStatus, ST_RUNNING)));
        map.put("expiring", contractMapper.selectCount(new LambdaQueryWrapper<SupplierContract>()
                .eq(SupplierContract::getStatus, ST_RUNNING)
                .isNotNull(SupplierContract::getEndDate)
                .le(SupplierContract::getEndDate, LocalDate.now().plusDays(EXPIRING_DAYS))
                .ge(SupplierContract::getEndDate, LocalDate.now())));
        map.put("expired", contractMapper.selectCount(new LambdaQueryWrapper<SupplierContract>()
                .eq(SupplierContract::getStatus, ST_EXPIRED)));
        return Result.ok(map);
    }

    @Operation(summary = "供应商合同详情")
    @PreAuthorize("hasAuthority('pur:supplierContract:query')")
    @GetMapping("/{id}")
    public Result<SupplierContract> get(@PathVariable Long id) {
        SupplierContract c = contractMapper.selectById(id);
        if (c != null) {
            fillSupplierNames(List.of(c));
        }
        return Result.ok(c);
    }

    @Operation(summary = "新增供应商合同")
    @PreAuthorize("hasAuthority('pur:supplierContract:add')")
    @PostMapping
    public Result<Long> add(@RequestBody SupplierContract contract) {
        validate(contract);
        contract.setCode(nextCode());
        if (contract.getStatus() == null) {
            contract.setStatus(ST_DRAFT);
        }
        contractMapper.insert(contract);
        return Result.ok(contract.getId());
    }

    @Operation(summary = "修改供应商合同")
    @PreAuthorize("hasAuthority('pur:supplierContract:edit')")
    @PutMapping
    public Result<Void> update(@RequestBody SupplierContract contract) {
        if (contract.getId() == null) {
            throw new BizException("缺少合同 id");
        }
        // MyBatis-Plus 默认 NOT_NULL 策略只跳过 null,空串会照写进库,故必填项单独拦
        if (contract.getName() != null && !StringUtils.hasText(contract.getName())) {
            throw new BizException("合同名称不能为空");
        }
        if (contract.getSupplierId() != null && supplierMapper.selectById(contract.getSupplierId()) == null) {
            throw new BizException("供应商不存在或已删除");
        }
        if (contract.getStartDate() != null && contract.getEndDate() != null
                && contract.getEndDate().isBefore(contract.getStartDate())) {
            throw new BizException("到期日期不能早于生效日期");
        }
        // 编号与状态不接受前端直改,状态走 /{id}/status
        contract.setCode(null);
        contract.setStatus(null);
        contractMapper.updateById(contract);
        return Result.ok();
    }

    @Operation(summary = "删除供应商合同")
    @PreAuthorize("hasAuthority('pur:supplierContract:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        contractMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "状态流转(status:2执行中 3已到期 4已终止)")
    @PreAuthorize("hasAuthority('pur:supplierContract:status')")
    @PostMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        List<Integer> from = allowedFrom(status);
        // entity 传 null 时 MetaObjectHandler.updateFill 不触发,审计字段手工补
        int updated = contractMapper.update(null, new LambdaUpdateWrapper<SupplierContract>()
                .eq(SupplierContract::getId, id)
                .in(SupplierContract::getStatus, from)
                .set(SupplierContract::getStatus, status)
                .set(SupplierContract::getUpdateTime, LocalDateTime.now())
                .set(SupplierContract::getUpdateBy, MyMetaObjectHandler.currentOperator()));
        if (updated == 0) {
            throw new BizException("当前状态不允许该操作,请刷新后重试");
        }
        return Result.ok();
    }

    /**
     * 目标状态允许的前置状态。
     * 生效后不退回草稿(要结束用终止);但允许「已到期 → 执行中」撤销误标,
     * 否则误点一次"标记到期"这份合同就永久卡死、只能改库。
     */
    private static List<Integer> allowedFrom(Integer target) {
        if (target == null) {
            throw new BizException("目标状态不能为空");
        }
        switch (target) {
            case ST_RUNNING:
                return List.of(ST_DRAFT, ST_EXPIRED);
            case ST_EXPIRED:
                return List.of(ST_RUNNING);
            case ST_TERMINATED:
                return List.of(ST_DRAFT, ST_RUNNING);
            default:
                throw new BizException("非法的目标状态:" + target);
        }
    }

    private void validate(SupplierContract contract) {
        if (contract.getSupplierId() == null) {
            throw new BizException("请选择供应商");
        }
        if (!StringUtils.hasText(contract.getName())) {
            throw new BizException("合同名称不能为空");
        }
        if (supplierMapper.selectById(contract.getSupplierId()) == null) {
            throw new BizException("供应商不存在或已删除");
        }
        if (contract.getStartDate() != null && contract.getEndDate() != null
                && contract.getEndDate().isBefore(contract.getStartDate())) {
            throw new BizException("到期日期不能早于生效日期");
        }
    }

    /** 回填供应商名称(展示字段,不落库) */
    private void fillSupplierNames(List<SupplierContract> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> ids = rows.stream().map(SupplierContract::getSupplierId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> nameById = supplierMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getName, (a, b) -> a));
        rows.forEach(r -> r.setSupplierName(nameById.get(r.getSupplierId())));
    }

    /**
     * 生成合同编号 GYSHT-2026-0001(按年份分段),取当年最大编号 +1。
     *
     * <p>已知边界同 SupplierController#nextCode:并发重号会让后插入者因唯一键失败
     * 并提示重试(<b>不重试</b>);超过 9999 后字符串比较失真。取最大编号走
     * selectMaxCodeIncludingDeleted(含软删行)——唯一键跨软删生效,发号必须看得见软删行。
     */
    private String nextCode() {
        String prefix = "GYSHT-" + LocalDate.now().getYear() + "-";
        // 必须用含软删的查询,理由同 SupplierController#nextCode
        String max = contractMapper.selectMaxCodeIncludingDeleted(prefix);
        int next = 1;
        if (StringUtils.hasText(max)) {
            try {
                next = Integer.parseInt(max.substring(prefix.length())) + 1;
            } catch (NumberFormatException | IndexOutOfBoundsException ignored) {
                // 历史编号格式异常:退回从 1 起,若撞号则本次请求失败提示重试
            }
        }
        return prefix + String.format("%04d", next);
    }
}
