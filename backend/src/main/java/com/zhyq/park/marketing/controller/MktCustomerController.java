package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktCustomerGrade;
import com.zhyq.park.marketing.entity.MktCustomerLock;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.mapper.MktCustomerGradeMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktLockService;
import com.zhyq.park.marketing.service.MktServiceContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 客户的全民营销侧(复用 crm_customer):评级、推荐人、签约方式、锁定。建档仍在「招商 › 意向客户」。 */
@Tag(name = "全民营销-客户")
@RestController
@RequestMapping("/crm/marketing/customer")
@RequiredArgsConstructor
public class MktCustomerController {

    private final CustomerMapper customerMapper;
    private final MktPromoterMapper promoterMapper;
    private final MktCustomerGradeMapper gradeMapper;
    private final MktServiceContractMapper contractMapper;
    private final MktLockService lockService;
    private final MktAuditService auditService;

    @Operation(summary = "分页(附推荐人姓名与锁定状态)")
    @PreAuthorize("hasAuthority('crm:marketing:customer:query')")
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                        @RequestParam(defaultValue = "10") int pageSize,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String grade,
                                                        @RequestParam(required = false) Integer referredOnly,
                                                        @RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<Customer> qw = new LambdaQueryWrapper<Customer>()
                .and(StringUtils.hasText(keyword), w -> w.like(Customer::getName, keyword).or().like(Customer::getPhone, keyword))
                .eq(StringUtils.hasText(grade), Customer::getGrade, grade)
                .isNotNull(Integer.valueOf(1).equals(referredOnly), Customer::getReferrerId)
                .eq(projectId != null, Customer::getProjectId, projectId)
                .orderByDesc(Customer::getId);
        IPage<Customer> p = customerMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        List<Map<String, Object>> rows = p.getRecords().stream().map(this::enrich).collect(Collectors.toList());
        return Result.ok(PageResult.of(p.getTotal(), rows));
    }

    @Operation(summary = "详情")
    @PreAuthorize("hasAuthority('crm:marketing:customer:query')")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        return Result.ok(enrich(require(id)));
    }

    @Operation(summary = "系统建议评级(租赁线按月租金、云仓线按月单量门槛;这里按现有字段粗估,专员确认)")
    @PreAuthorize("hasAuthority('crm:marketing:customer:query')")
    @GetMapping("/{id}/suggest-grade")
    public Result<Map<String, Object>> suggestGrade(@PathVariable Long id) {
        Customer c = require(id);
        List<MktCustomerGrade> grades = gradeMapper.selectList(new LambdaQueryWrapper<MktCustomerGrade>().orderByAsc(MktCustomerGrade::getSort));
        // 阶段 A:客户表没有月租金/月单量数值字段,demand_area 是文本;只能给出"无数据 → D"并说明依据。有合同后再按合同金额建议。
        Map<String, Object> m = new HashMap<>();
        m.put("grade", grades.isEmpty() ? "D" : grades.get(grades.size() - 1).getCode());
        m.put("basis", "客户档案暂无月租金/月单量数值,默认最低档;请按洽谈金额人工确认");
        return Result.ok(m);
    }

    @Operation(summary = "评级(专员确认,写依据)")
    @PreAuthorize("hasAuthority('crm:marketing:customer:edit')")
    @PostMapping("/{id}/grade")
    public Result<Void> grade(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String g = body.get("grade");
        if (!StringUtils.hasText(g) || gradeMapper.selectCount(new LambdaQueryWrapper<MktCustomerGrade>().eq(MktCustomerGrade::getCode, g)) == 0) {
            throw new BizException("评级不存在: " + g);
        }
        if (!StringUtils.hasText(body.get("reason"))) throw new BizException("请填写评级依据");
        Customer c = require(id);
        customerMapper.update(null, new LambdaUpdateWrapper<Customer>().eq(Customer::getId, id).set(Customer::getGrade, g));
        auditService.log("customer.grade", "customer", id, body.get("reason"), c.getGrade(), g);
        return Result.ok();
    }

    @Operation(summary = "设置/更换推荐伙伴(按邀请码)")
    @PreAuthorize("hasAuthority('crm:marketing:customer:edit')")
    @PostMapping("/{id}/referrer")
    public Result<Void> referrer(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Customer c = require(id);
        MktPromoter p = promoterMapper.selectOne(new LambdaQueryWrapper<MktPromoter>()
                .eq(MktPromoter::getInviteCode, String.valueOf(body.get("inviteCode")).trim().toUpperCase()).last("limit 1"));
        if (p == null) throw new BizException("邀请码不存在");
        if (StringUtils.hasText(c.getPhone()) && c.getPhone().equals(p.getPhone())) throw new BizException("不能自我推荐");
        if (c.getReferrerId() != null && !StringUtils.hasText(body.get("reason"))) throw new BizException("更换推荐人必须填写原因");
        customerMapper.update(null, new LambdaUpdateWrapper<Customer>().eq(Customer::getId, id)
                .set(Customer::getReferrerId, p.getId())
                .set(Customer::getAttributionNote, "后台设置推荐人 " + p.getInviteCode() + (StringUtils.hasText(body.get("reason")) ? ":" + body.get("reason") : "")));
        auditService.log("customer.referrer", "customer", id, body.get("reason"), c.getReferrerId(), p.getId());
        return Result.ok();
    }

    @Operation(summary = "签约方式(有生效合同时不可改)")
    @PreAuthorize("hasAuthority('crm:marketing:customer:edit')")
    @PostMapping("/{id}/sign-mode")
    public Result<Void> signMode(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer mode = body.get("signMode");
        if (mode == null || (mode != 1 && mode != 2)) throw new BizException("签约方式只能是 1 园区签 / 2 云仓直签");
        Customer c = require(id);
        long active = contractMapper.selectCount(new LambdaQueryWrapper<MktServiceContract>()
                .eq(MktServiceContract::getCustomerId, id)
                .in(MktServiceContract::getStatus, MktServiceContractService.ST_EFFECTIVE, MktServiceContractService.ST_PERFORMING, MktServiceContractService.ST_AMENDING));
        if (active > 0) throw new BizException("客户已有生效合同,不能更改签约方式(新合同可另选)");
        customerMapper.update(null, new LambdaUpdateWrapper<Customer>().eq(Customer::getId, id).set(Customer::getSignMode, mode));
        auditService.log("customer.signMode", "customer", id, null, c.getSignMode(), mode);
        return Result.ok();
    }

    @Operation(summary = "标记流失")
    @PreAuthorize("hasAuthority('crm:marketing:customer:edit')")
    @PostMapping("/{id}/lose")
    public Result<Void> lose(@PathVariable Long id, @RequestBody Map<String, String> body) {
        if (!StringUtils.hasText(body.get("reason"))) throw new BizException("请填写流失原因");
        int updated = customerMapper.update(null, new LambdaUpdateWrapper<Customer>()
                .eq(Customer::getId, id).ne(Customer::getStatus, 3).set(Customer::getStatus, 3));
        if (updated == 0) throw new BizException("客户状态已变化");
        auditService.log("customer.lose", "customer", id, body.get("reason"));
        return Result.ok();
    }

    // ---------------- 锁定 ----------------

    @Operation(summary = "当前有效锁(无则 null)")
    @PreAuthorize("hasAuthority('crm:marketing:customer:query')")
    @GetMapping("/{id}/lock")
    public Result<MktCustomerLock> lock(@PathVariable Long id) {
        return Result.ok(lockService.activeLockOf(id));
    }

    @Operation(summary = "代伙伴报备(预锁)")
    @PreAuthorize("hasAuthority('crm:marketing:customer:edit')")
    @PostMapping("/{id}/lock/prelock")
    public Result<MktCustomerLock> prelock(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return Result.ok(lockService.prelock(id, body.get("promoterId")));
    }

    @Operation(summary = "专员确认 → 有效锁定")
    @PreAuthorize("hasAuthority('crm:marketing:customer:edit')")
    @PostMapping("/lock/{lockId}/confirm")
    public Result<Void> confirmLock(@PathVariable Long lockId) {
        lockService.confirm(lockId, MktAuditService.currentOperator());
        return Result.ok();
    }

    @Operation(summary = "延期一次")
    @PreAuthorize("hasAuthority('crm:marketing:customer:edit')")
    @PostMapping("/lock/{lockId}/extend")
    public Result<Void> extendLock(@PathVariable Long lockId, @RequestBody Map<String, String> body) {
        lockService.extend(lockId, body.get("reason"));
        return Result.ok();
    }

    @Operation(summary = "释放到公海")
    @PreAuthorize("hasAuthority('crm:marketing:customer:edit')")
    @PostMapping("/lock/{lockId}/release")
    public Result<Void> releaseLock(@PathVariable Long lockId, @RequestBody Map<String, String> body) {
        lockService.release(lockId, body.get("reason"), MktAuditService.currentOperator());
        return Result.ok();
    }

    @Operation(summary = "转移给其他伙伴")
    @PreAuthorize("hasAuthority('crm:marketing:customer:edit')")
    @PostMapping("/lock/{lockId}/transfer")
    public Result<MktCustomerLock> transferLock(@PathVariable Long lockId, @RequestBody Map<String, Object> body) {
        return Result.ok(lockService.transfer(lockId, Long.valueOf(body.get("toPromoterId").toString()), (String) body.get("reason")));
    }

    // ---------------- 内部 ----------------

    private Customer require(Long id) {
        Customer c = customerMapper.selectById(id);
        if (c == null) throw new BizException("客户不存在: " + id);
        return c;
    }

    private Map<String, Object> enrich(Customer c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId()); m.put("name", c.getName()); m.put("contact", c.getContact()); m.put("phone", c.getPhone());
        m.put("grade", c.getGrade()); m.put("referrerId", c.getReferrerId()); m.put("serviceType", c.getServiceType());
        m.put("bizLine", c.getBizLine()); m.put("signMode", c.getSignMode()); m.put("status", c.getStatus());
        m.put("attributionNote", c.getAttributionNote()); m.put("intentLevel", c.getIntentLevel()); m.put("owner", c.getOwner());
        if (c.getReferrerId() != null) {
            MktPromoter p = promoterMapper.selectById(c.getReferrerId());
            m.put("referrerName", p == null ? null : p.getName());
        }
        MktCustomerLock lock = lockService.activeLockOf(c.getId());
        if (lock != null) {
            m.put("lockStatus", lock.getStatus());
            LocalDateTime until = lock.getStatus() == MktLockService.LS_PRELOCK ? lock.getPrelockUntil() : lock.getLockUntil();
            m.put("lockDaysLeft", until == null ? null : Math.max(0, Duration.between(LocalDateTime.now(), until).toDays()));
        }
        return m;
    }
}
