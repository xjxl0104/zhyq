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
import com.zhyq.park.property.entity.WorkOrder;
import com.zhyq.park.property.mapper.WorkOrderMapper;
import com.zhyq.park.tenant.entity.BizTenant;
import com.zhyq.park.tenant.mapper.BizTenantMapper;
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
    private final WorkOrderMapper workOrderMapper;
    private final BizTenantMapper tenantMapper;

    public record TenantContactOption(Long id, String name, Long projectId, String contact, String phone) {}
    public record TenantContactRequest(Long tenantRefId, String contact, String phone) {}

    @Operation(summary = "租客联系人选项（供应商档案维护，工单关联）")
    @PreAuthorize("hasAnyAuthority('pur:supplier:query', 'property:workorder:query')")
    @GetMapping("/tenant-contacts")
    public Result<List<TenantContactOption>> tenantContacts() {
        return Result.ok(tenantMapper.selectList(new LambdaQueryWrapper<BizTenant>()
                .eq(BizTenant::getStatus, 1)
                .eq(BizTenant::getTenantId, MyMetaObjectHandler.DEFAULT_TENANT_ID)
                .select(BizTenant::getId, BizTenant::getName, BizTenant::getProjectId,
                        BizTenant::getContact, BizTenant::getPhone)
                .orderByAsc(BizTenant::getName)).stream()
                .map(t -> new TenantContactOption(t.getId(), t.getName(), t.getProjectId(), t.getContact(), t.getPhone()))
                .toList());
    }

    @Operation(summary = "维护租客联系人")
    @PreAuthorize("hasAuthority('pur:supplier:edit')")
    @PutMapping("/tenant-contacts")
    public Result<Void> updateTenantContact(@RequestBody TenantContactRequest req) {
        if (req == null || req.tenantRefId() == null) throw new BizException("请选择租客");
        if (!StringUtils.hasText(req.contact())) throw new BizException("请填写租客联系人");
        if (req.contact().length() > 64 || (req.phone() != null && req.phone().length() > 20)) {
            throw new BizException("租客联系人或电话超过长度限制");
        }
        int updated = tenantMapper.update(null, new LambdaUpdateWrapper<BizTenant>()
                .eq(BizTenant::getId, req.tenantRefId())
                .eq(BizTenant::getStatus, 1)
                .eq(BizTenant::getTenantId, MyMetaObjectHandler.DEFAULT_TENANT_ID)
                .set(BizTenant::getContact, req.contact().trim())
                .set(BizTenant::getPhone, req.phone() == null ? null : req.phone().trim())
                .set(BizTenant::getUpdateTime, LocalDateTime.now())
                .set(BizTenant::getUpdateBy, MyMetaObjectHandler.currentOperator()));
        if (updated == 0) throw new BizException("租客不存在或已归档");
        return Result.ok();
    }

    @Operation(summary = "删除租客联系人（保留租客档案和历史工单）")
    @PreAuthorize("hasAuthority('pur:supplier:edit')")
    @DeleteMapping("/tenant-contacts/{tenantRefId}")
    public Result<Void> removeTenantContact(@PathVariable Long tenantRefId) {
        int updated = tenantMapper.update(null, new LambdaUpdateWrapper<BizTenant>()
                .eq(BizTenant::getId, tenantRefId)
                .eq(BizTenant::getStatus, 1)
                .eq(BizTenant::getTenantId, MyMetaObjectHandler.DEFAULT_TENANT_ID)
                .isNotNull(BizTenant::getContact)
                .set(BizTenant::getContact, null)
                .set(BizTenant::getPhone, null)
                .set(BizTenant::getUpdateTime, LocalDateTime.now())
                .set(BizTenant::getUpdateBy, MyMetaObjectHandler.currentOperator()));
        if (updated == 0) throw new BizException("租客联系人不存在或已删除");
        return Result.ok();
    }

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
        if (workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getSupplierId, id)) > 0) {
            throw new BizException("该供应商已关联物业工单，不能删除；请停用或归档");
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
    @PreAuthorize("hasAnyAuthority('pur:supplier:query', 'property:workorder:query', 'pur:supplierContract:query')")
    @GetMapping("/list")
    public Result<List<Supplier>> list() {
        return Result.ok(supplierMapper.selectList(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getStatus, ST_NORMAL)
                .select(Supplier::getId, Supplier::getName, Supplier::getContact,
                        Supplier::getPhone, Supplier::getServiceScope, Supplier::getProjectId)
                .orderByDesc(Supplier::getId)));
    }

    @Operation(summary = "工单供应商选项（含停用历史档案，不含敏感信息）")
    @PreAuthorize("hasAnyAuthority('pur:supplier:query', 'property:workorder:query')")
    @GetMapping("/options")
    public Result<List<Supplier>> options() {
        return Result.ok(supplierMapper.selectList(new LambdaQueryWrapper<Supplier>()
                .select(Supplier::getId, Supplier::getName, Supplier::getContact,
                        Supplier::getPhone, Supplier::getServiceScope,
                        Supplier::getProjectId, Supplier::getStatus)
                .orderByAsc(Supplier::getName)));
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
