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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "合同管理-合同")
@RestController
@RequestMapping("/contract")
@RequiredArgsConstructor
public class ContractController {

    private final ContractMapper contractMapper;
    private final ContractRoomMapper contractRoomMapper;
    private final ContractService contractService;

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
