package com.zhyq.park.contract.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.contract.entity.Contract;
import com.zhyq.park.contract.entity.ContractRoom;
import com.zhyq.park.contract.mapper.ContractMapper;
import com.zhyq.park.contract.mapper.ContractRoomMapper;
import com.zhyq.park.contract.service.ContractService;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.mapper.ReceivableRegisterMapper;
import com.zhyq.park.tenant.entity.BizTenant;
import com.zhyq.park.tenant.mapper.BizTenantMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Tag(name = "合同管理-合同")
@RestController
@RequestMapping("/contract")
@RequiredArgsConstructor
public class ContractController {

    private final ContractMapper contractMapper;
    private final ContractRoomMapper contractRoomMapper;
    private final ContractService contractService;
    private final BillMapper billMapper;
    private final ReceivableRegisterMapper receivableRegisterMapper;
    private final BizTenantMapper bizTenantMapper;

    @Operation(summary = "分页查询合同")
    @PreAuthorize("hasAuthority('contract:query')")
    @GetMapping("/page")
    public Result<PageResult<Contract>> page(@RequestParam(defaultValue = "1") int pageNo,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) String code,
                                             @RequestParam(required = false) Long tenantRefId,
                                             @RequestParam(required = false) Long projectId,
                                             @RequestParam(required = false) Integer status,
                                             @RequestParam(required = false) Integer contractType) {
        LambdaQueryWrapper<Contract> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(code), Contract::getCode, code)
          .eq(tenantRefId != null, Contract::getTenantRefId, tenantRefId)
          .eq(projectId != null, Contract::getProjectId, projectId)
          .eq(status != null, Contract::getStatus, status)
          .eq(contractType != null, Contract::getContractType, contractType)
          .orderByDesc(Contract::getId);
        IPage<Contract> p = contractMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        fillTenantNames(p.getRecords());
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "合同详情(含房源列表)")
    @PreAuthorize("hasAuthority('contract:query')")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        Contract contract = contractMapper.selectById(id);
        List<ContractRoom> rooms = contractRoomMapper.selectList(
                new LambdaQueryWrapper<ContractRoom>().eq(ContractRoom::getContractId, id));
        Map<String, Object> data = new HashMap<>();
        data.put("contract", contract);
        data.put("rooms", rooms);
        return Result.ok(data);
    }

    @Operation(summary = "新增合同")
    @PreAuthorize("hasAuthority('contract:add')")
    @PostMapping
    public Result<Long> add(@RequestBody Contract contract) {
        if (contract.getStatus() == null) {
            contract.setStatus(1); // 默认草稿
        }
        contractMapper.insert(contract);
        return Result.ok(contract.getId());
    }

    @Operation(summary = "修改合同")
    @PreAuthorize("hasAuthority('contract:edit')")
    @PutMapping
    public Result<Void> update(@RequestBody Contract contract) {
        contractMapper.updateById(contract);
        return Result.ok();
    }

    @Operation(summary = "删除合同")
    @PreAuthorize("hasAuthority('contract:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        contractMapper.deleteById(id);
        return Result.ok();
    }

    /**
     * 重置合同:一键作废「未产生任何实收」的全部合同,清掉演示/试录数据后可重新按真实资料录入。
     *
     * <p>与账单页「重置」同一思路,守卫也同口径 —— <b>只要该合同名下有任何一张账单收过款,
     * 整份合同保留</b>,免得把有资金往来的合同抹掉。作废时一并做两件收尾:
     * ① 关联房源放回可租(否则房源永远锁在已作废的合同上);
     * ② 解除应收登记表对该合同的关联(register.contract_id 置空),
     * 这样重新录入合同后还能再挂上去 —— 审批要求合同必须关联已确认登记表。</p>
     *
     * <p>不动账单:账单的权威来源是应收明细登记表,合同只是容器;要清账单走账单页的重置。</p>
     */
    @Operation(summary = "重置合同(未产生实收的全部作废,可重新录入)")
    @PreAuthorize("hasAuthority('contract:delete')")
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/reset")
    public Result<Map<String, Object>> reset() {
        // 有过实收的合同整份保留:按 contract_id 聚合账单实收,paid>0 即受保护
        Set<Long> protectedIds = new HashSet<>();
        billMapper.selectList(new LambdaQueryWrapper<Bill>()
                        .select(Bill::getContractId, Bill::getPaidAmount)
                        .isNotNull(Bill::getContractId))
                .stream()
                .filter(bill -> bill.getPaidAmount() != null
                        && bill.getPaidAmount().compareTo(BigDecimal.ZERO) > 0)
                .forEach(bill -> protectedIds.add(bill.getContractId()));

        LambdaQueryWrapper<Contract> deletable = new LambdaQueryWrapper<>();
        if (!protectedIds.isEmpty()) {
            deletable.notIn(Contract::getId, protectedIds);
        }
        List<Long> ids = contractMapper.selectList(deletable).stream()
                .map(Contract::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Result.ok(summary(0, protectedIds.size()));
        }
        // ① 房源放回可租(复用退租的同一动作,避免房源被作废合同长期占用)
        ids.forEach(contractService::releaseRooms);
        // ② 解除登记表关联,让重新录入的合同能再挂上
        receivableRegisterMapper.update(null, new LambdaUpdateWrapper<ReceivableRegister>()
                .in(ReceivableRegister::getContractId, ids)
                .set(ReceivableRegister::getContractId, null));
        int deleted = contractMapper.deleteBatchIds(ids);
        return Result.ok(summary(deleted, protectedIds.size()));
    }

    /**
     * 批量填租客名:合同列表与合同归档都要显示租客而不是裸 tenant_ref_id,
     * 一次查库按 id 映射,避免逐行查。
     */
    private void fillTenantNames(List<Contract> contracts) {
        if (contracts == null || contracts.isEmpty()) {
            return;
        }
        List<Long> ids = contracts.stream().map(Contract::getTenantRefId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> names = new HashMap<>();
        bizTenantMapper.selectBatchIds(ids)
                .forEach(t -> names.put(t.getId(), t.getName()));
        contracts.forEach(c -> c.setTenantName(names.get(c.getTenantRefId())));
    }

    private static Map<String, Object> summary(int deleted, int kept) {
        Map<String, Object> result = new HashMap<>();
        result.put("deleted", deleted);
        result.put("kept", kept);
        return result;
    }

    @Operation(summary = "提交审批(草稿→待审核)")
    @PreAuthorize("hasAuthority('contract:submit')")
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        contractService.submit(id);
        return Result.ok();
    }

    @Operation(summary = "审批通过(待审核→执行中,生成账单计划)")
    @PreAuthorize("hasAuthority('contract:approve')")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        contractService.approve(id);
        return Result.ok();
    }

    @Operation(summary = "退租(→已终止,释放房源)")
    @PreAuthorize("hasAuthority('contract:terminate')")
    @PostMapping("/{id}/terminate")
    public Result<Void> terminate(@PathVariable Long id) {
        contractService.terminate(id);
        return Result.ok();
    }

    @Operation(summary = "归档(已到期/已终止→已归档)")
    @PreAuthorize("hasAuthority('contract:archive')")
    @PostMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id) {
        int updated = contractMapper.update(null, new LambdaUpdateWrapper<Contract>()
                .eq(Contract::getId, id)
                .in(Contract::getStatus, 8, 9)
                .set(Contract::getStatus, 10));
        if (updated == 0) {
            throw new BizException("仅已到期或已终止的合同可归档");
        }
        return Result.ok();
    }
}
