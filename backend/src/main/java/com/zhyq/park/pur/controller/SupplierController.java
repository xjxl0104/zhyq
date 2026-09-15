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
import com.zhyq.park.pur.mapper.SupplierContractMapper;
import com.zhyq.park.pur.entity.SupplierContract;
import com.zhyq.park.pur.mapper.SupplierMapper;
import com.zhyq.park.pur.service.SupplierImportService;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 供应商档案(V53)。纯主数据 CRUD,按项目规范不加 service 层。
 *
 * <p>类别 category 存字典 supplier_category 的 value,后端不做枚举校验 ——
 * 使用方在「系统管理→字典管理」加类别后即可直接使用,无需改代码。
 */
@Tag(name = "供应商管理-供应商档案")
@RestController
@RequestMapping("/pur/supplier")
@RequiredArgsConstructor
public class SupplierController {

    /** 1正常 2停用 3已归档 */
    private static final int ST_NORMAL = 1;
    private static final int ST_DISABLED = 2;
    private static final int ST_ARCHIVED = 3;

    private final SupplierMapper supplierMapper;
    private final SupplierContractMapper contractMapper;
    private final SupplierImportService supplierImportService;

    @Operation(summary = "分页查询供应商")
    @PreAuthorize("hasAuthority('pur:supplier:query')")
    @GetMapping("/page")
    public Result<PageResult<Supplier>> page(@RequestParam(defaultValue = "1") int pageNo,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) String name,
                                             @RequestParam(required = false) String code,
                                             @RequestParam(required = false) String category,
                                             @RequestParam(required = false) String contact,
                                             @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Supplier> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(name), Supplier::getName, name)
          .like(StringUtils.hasText(code), Supplier::getCode, code)
          .eq(StringUtils.hasText(category), Supplier::getCategory, category)
          .like(StringUtils.hasText(contact), Supplier::getContact, contact)
          .eq(status != null, Supplier::getStatus, status)
          .orderByDesc(Supplier::getId);
        IPage<Supplier> p = supplierMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "供应商统计")
    @PreAuthorize("hasAuthority('pur:supplier:query')")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> map = new HashMap<>();
        map.put("total", supplierMapper.selectCount(new LambdaQueryWrapper<>()));
        map.put("normal", countByStatus(ST_NORMAL));
        map.put("disabled", countByStatus(ST_DISABLED));
        map.put("archived", countByStatus(ST_ARCHIVED));
        return Result.ok(map);
    }

    @Operation(summary = "导入供应商档案(xlsx/xls/et/csv/txt/docx)")
    @PreAuthorize("hasAuthority('pur:supplier:add')")
    @PostMapping("/import")
    public Result<SupplierImportService.ImportResult> importFile(@RequestParam("file") MultipartFile file) {
        return Result.ok(supplierImportService.importFile(file));
    }

    @Operation(summary = "供应商详情")
    @PreAuthorize("hasAuthority('pur:supplier:query')")
    @GetMapping("/{id}")
    public Result<Supplier> get(@PathVariable Long id) {
        return Result.ok(supplierMapper.selectById(id));
    }

    @Operation(summary = "新增供应商")
    @PreAuthorize("hasAuthority('pur:supplier:add')")
    @PostMapping
    public Result<Long> add(@RequestBody Supplier supplier) {
        if (!StringUtils.hasText(supplier.getName())) {
            throw new BizException("供应商名称不能为空");
        }
        supplier.setCode(supplierMapper.nextCode());
        if (supplier.getStatus() == null) {
            supplier.setStatus(ST_NORMAL);
        }
        supplierMapper.insert(supplier);
        return Result.ok(supplier.getId());
    }

    @Operation(summary = "修改供应商")
    @PreAuthorize("hasAuthority('pur:supplier:edit')")
    @PutMapping
    public Result<Void> update(@RequestBody Supplier supplier) {
        if (supplier.getId() == null) {
            throw new BizException("缺少供应商 id");
        }
        // MyBatis-Plus 默认 NOT_NULL 策略只跳过 null,空串会照写进库,故必填项要单独拦
        if (supplier.getName() != null && !StringUtils.hasText(supplier.getName())) {
            throw new BizException("供应商名称不能为空");
        }
        // 编号由服务端生成、状态只走 /{id}/status(那里有状态机与独立权限),
        // 二者都不接受前端直改,否则只有 edit 权限即可绕过状态机。
        supplier.setCode(null);
        supplier.setStatus(null);
        supplierMapper.updateById(supplier);
        return Result.ok();
    }

    @Operation(summary = "删除供应商(已有合同则拒绝)")
    @PreAuthorize("hasAuthority('pur:supplier:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        long contracts = contractMapper.selectCount(
                new LambdaQueryWrapper<SupplierContract>().eq(SupplierContract::getSupplierId, id));
        if (contracts > 0) {
            throw new BizException("该供应商下还有 " + contracts + " 份合同,不能删除;如需停用请改状态");
        }
        supplierMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "启用/停用/归档(status:1正常 2停用 3已归档)")
    @PreAuthorize("hasAuthority('pur:supplier:status')")
    @PostMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        List<Integer> from = allowedFrom(status);
        // 条件更新抢状态:并发下只有一方成功(项目硬约束,禁止先查后改)。
        // entity 传 null 时 MetaObjectHandler.updateFill 不触发,故审计字段手工补,
        // 否则状态被谁在什么时候改的库里查不出来。
        int updated = supplierMapper.update(null, new LambdaUpdateWrapper<Supplier>()
                .eq(Supplier::getId, id)
                .in(Supplier::getStatus, from)
                .set(Supplier::getStatus, status)
                .set(Supplier::getUpdateTime, LocalDateTime.now())
                .set(Supplier::getUpdateBy, MyMetaObjectHandler.currentOperator()));
        if (updated == 0) {
            throw new BizException("当前状态不允许该操作,请刷新后重试");
        }
        return Result.ok();
    }

    @Operation(summary = "全部供应商(下拉,仅正常)")
    @PreAuthorize("hasAuthority('pur:supplier:query')")
    @GetMapping("/list")
    public Result<List<Supplier>> list() {
        return Result.ok(supplierMapper.selectList(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getStatus, ST_NORMAL)
                .orderByDesc(Supplier::getId)));
    }

    /** 目标状态允许的前置状态 */
    private static List<Integer> allowedFrom(Integer target) {
        if (target == null) {
            throw new BizException("目标状态不能为空");
        }
        switch (target) {
            case ST_NORMAL:
                return List.of(ST_DISABLED, ST_ARCHIVED);
            case ST_DISABLED:
                return List.of(ST_NORMAL);
            case ST_ARCHIVED:
                return List.of(ST_NORMAL, ST_DISABLED);
            default:
                throw new BizException("非法的目标状态:" + target);
        }
    }

    private long countByStatus(int status) {
        return supplierMapper.selectCount(
                new LambdaQueryWrapper<Supplier>().eq(Supplier::getStatus, status));
    }

}
