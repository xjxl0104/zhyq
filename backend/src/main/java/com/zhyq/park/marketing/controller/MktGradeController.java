package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        for (MktCustomerGrade g : list) {
            if (g.getLeaseCommissionMonths() == null || g.getLeaseCommissionMonths().compareTo(new BigDecimal("0.25")) < 0
                    || g.getLeaseCommissionMonths().compareTo(new BigDecimal("2")) > 0) {
                throw new BizException(g.getCode() + " 租赁佣金月数须在 0.25–2 之间");
            }
            if (g.getErpTotalRate() == null || g.getErpTotalRate().signum() < 0 || g.getErpTotalRate().compareTo(new BigDecimal("100")) > 0) {
                throw new BizException(g.getCode() + " 入仓总比例须在 0–100 之间");
            }
            MktCustomerGrade before = gradeMapper.selectById(g.getId());
            gradeMapper.updateById(g);
            auditService.log("grade.config", "grade", g.getId(), null, before, g);
        }
        return Result.ok();
    }
}
