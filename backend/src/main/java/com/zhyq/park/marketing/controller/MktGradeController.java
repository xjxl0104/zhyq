package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.entity.MktCustomerGrade;
import com.zhyq.park.marketing.mapper.MktCustomerGradeMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "全民营销-客户评级")
@RestController
@RequestMapping("/crm/marketing/grade")
@RequiredArgsConstructor
public class MktGradeController {

    private final MktCustomerGradeMapper gradeMapper;
    private final MktAuditService auditService;

    @Operation(summary = "评级列表 A–D")
    @PreAuthorize("hasAuthority('crm:marketing:grade:query')")
    @GetMapping("/list")
    public Result<List<MktCustomerGrade>> list() {
        return Result.ok(gradeMapper.selectList(new LambdaQueryWrapper<MktCustomerGrade>().orderByAsc(MktCustomerGrade::getSort)));
    }

    @Operation(summary = "批量保存评级参数")
    @PreAuthorize("hasAuthority('crm:marketing:grade:config')")
    @PutMapping
    @Transactional
    public Result<Void> update(@RequestBody List<MktCustomerGrade> list) {
        if (list == null || list.isEmpty()) throw new BizException("提交内容为空");
        for (MktCustomerGrade g : list) {
            if (g.getId() == null) throw new BizException("评级 id 必填");
            MktCustomerGrade before = gradeMapper.selectById(g.getId());
            if (before == null) throw new BizException("评级不存在: " + g.getId());
            if (g.getLeaseCommissionMonths() != null && (g.getLeaseCommissionMonths().compareTo(new BigDecimal("0.25")) < 0
                    || g.getLeaseCommissionMonths().compareTo(new BigDecimal("2")) > 0)) {
                throw new BizException(before.getCode() + " 租赁佣金月数须在 0.25–2 之间");
            }
            if (g.getErpTotalRate() != null && (before.getErpTotalRate() == null || g.getErpTotalRate().compareTo(before.getErpTotalRate()) != 0)) {
                throw new BizException("入仓岗位固定比例已停用，请由 P4 按客户设置每单金额");
            }
            // 白名单更新:code/sort 是评级身份与排序锚点,改了会让历史 grade 快照失配、导入回落默认档;
            // 名称/描述/参数可改。用 before.code 记审计,避免请求体伪造 code 污染日志。
            gradeMapper.update(null, new LambdaUpdateWrapper<MktCustomerGrade>()
                    .eq(MktCustomerGrade::getId, g.getId())
                    .set(g.getName() != null, MktCustomerGrade::getName, g.getName())
                    .set(g.getDescr() != null, MktCustomerGrade::getDescr, g.getDescr())
                    .set(g.getLeaseCommissionMonths() != null, MktCustomerGrade::getLeaseCommissionMonths, g.getLeaseCommissionMonths())
                    .set(g.getServiceTotalRate() != null, MktCustomerGrade::getServiceTotalRate, g.getServiceTotalRate())
                    .set(g.getContractBonus() != null, MktCustomerGrade::getContractBonus, g.getContractBonus())
                    .set(g.getAutoMinRent() != null, MktCustomerGrade::getAutoMinRent, g.getAutoMinRent())
                    .set(g.getAutoMinOrders() != null, MktCustomerGrade::getAutoMinOrders, g.getAutoMinOrders())
                    .set(g.getStatus() != null, MktCustomerGrade::getStatus, g.getStatus()));
            auditService.log("grade.config", "grade", g.getId(), null, before, g);
        }
        return Result.ok();
    }
}
