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
import com.zhyq.park.marketing.service.MktCustomerAssignmentService;
import com.zhyq.park.marketing.service.MktCustomerDeletionService;
import com.zhyq.park.marketing.service.MktLockService;
import com.zhyq.park.marketing.service.MktPromoterService;
import com.zhyq.park.marketing.service.MktServiceContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
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
    private final MktCustomerAssignmentService assignmentService;
    private final MktCustomerDeletionService deletionService;

    @Operation(summary = "分页(附推荐人姓名与锁定状态)")
    @PreAuthorize("hasAuthority('crm:marketing:customer:query')")
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                        @RequestParam(defaultValue = "10") int pageSize,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String grade,
                                                        @RequestParam(required = false) Integer referredOnly,
                                                        @RequestParam(required = false) Long projectId,
                                                        @RequestParam(required = false) Integer warehouseAssignmentStatus) {
        LambdaQueryWrapper<Customer> qw = new LambdaQueryWrapper<Customer>()
                .and(StringUtils.hasText(keyword), w -> w.like(Customer::getName, keyword).or().like(Customer::getPhone, keyword))
                .eq(StringUtils.hasText(grade), Customer::getGrade, grade)
                .isNotNull(Integer.valueOf(1).equals(referredOnly), Customer::getReferrerId)
                .and(projectId != null, q -> q.eq(Customer::getProjectId, projectId).or().isNull(Customer::getProjectId))
                .eq(warehouseAssignmentStatus != null, Customer::getWarehouseAssignmentStatus, warehouseAssignmentStatus)
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

    @Operation(summary = "管理员删除客户（保留业务历史，有合同或订单不可删除）")
    @PreAuthorize("hasRole('admin')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deletionService.deleteAsAdmin(id);
        return Result.ok();
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
    @Transactional
    public Result<Void> referrer(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Customer c = customerMapper.selectForUpdate(id);
        if (c == null) throw new BizException("客户不存在");
        MktPromoter p = promoterMapper.selectOne(new LambdaQueryWrapper<MktPromoter>()
                .eq(MktPromoter::getInviteCode, String.valueOf(body.get("inviteCode")).trim().toUpperCase()).last("limit 1 FOR UPDATE"));
        if (p == null) throw new BizException("邀请码不存在");
        // 推荐人必须是正常在册伙伴:冻结/待审核/已退出的人挂上去,后续计佣会落到不可用主体
        if (!Integer.valueOf(MktPromoterService.ST_NORMAL).equals(p.getStatus())) {
            throw new BizException("该伙伴当前状态不可作为推荐人(status=" + p.getStatus() + ")");
        }
        if (StringUtils.hasText(c.getPhone()) && c.getPhone().equals(p.getPhone())) throw new BizException("不能自我推荐");
        if (c.getProjectId() != null && p.getProjectId() != null && !c.getProjectId().equals(p.getProjectId())) throw new BizException("不能跨园区调整推荐归属");
        if (c.getReferrerId() != null && !StringUtils.hasText(body.get("reason"))) throw new BizException("更换推荐人必须填写原因");
        if (!p.getId().equals(c.getReferrerId())) assignmentService.assertNoActiveContracts(id);
        // 已存在别的伙伴的有效锁/预锁时,变更推荐人会和锁定归属打架,要求先释放或转移
        MktCustomerLock active = lockService.activeLockOf(id);
        if (active != null && active.getPromoterId() != null && !active.getPromoterId().equals(p.getId())) {
            throw new BizException("该客户已有其它伙伴的有效锁,请先释放或转移锁定再改推荐人");
        }
        int updated = customerMapper.update(null, new LambdaUpdateWrapper<Customer>().eq(Customer::getId, id)
                .eq(c.getVersion() != null, Customer::getVersion, c.getVersion()).setSql("version = version + 1")
                .set(Customer::getReferrerId, p.getId())
                .set(Customer::getAttributionNote, "后台设置推荐人 " + p.getInviteCode() + (StringUtils.hasText(body.get("reason")) ? ":" + body.get("reason") : "")));
        if (updated == 0) throw new BizException("客户状态已变化,请刷新后重试");
        auditService.log("customer.referrer", "customer", id, body.get("reason"), c.getReferrerId(), p.getId());
        return Result.ok();
    }

    public record WarehouseAssignmentRequest(Long warehouseId, String reason) {}

    @Operation(summary = "分派或调整承接云仓，等待商家确认")
    @PreAuthorize("hasAuthority('crm:marketing:customer:edit')")
    @PostMapping("/{id}/assign-warehouse")
    public Result<Void> assignWarehouse(@PathVariable Long id, @RequestBody WarehouseAssignmentRequest request) {
        assignmentService.assign(id, request.warehouseId(), request.reason());
        return Result.ok();
    }

    @Operation(summary = "发布伙伴可见的跟进进度（不公开内部备注）")
    @PreAuthorize("hasAuthority('crm:marketing:customer:edit')")
    @PostMapping("/{id}/progress")
    public Result<Void> publishProgress(@PathVariable Long id, @RequestBody Map<String, String> body) {
        assignmentService.updateProgress(id, body.get("summary"));
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
    @Transactional
    public Result<Void> lose(@PathVariable Long id, @RequestBody Map<String, String> body) {
        if (!StringUtils.hasText(body.get("reason"))) throw new BizException("请填写流失原因");
        Customer current = customerMapper.selectForUpdate(id);
        if (current == null) throw new BizException("客户不存在");
        assignmentService.assertNoActiveContracts(id);
        int updated = customerMapper.update(null, new LambdaUpdateWrapper<Customer>()
                .eq(Customer::getId, id).ne(Customer::getStatus, 3).set(Customer::getStatus, 3));
        if (updated == 0) throw new BizException("客户状态已变化");
        auditService.log("customer.lose", "customer", id, body.get("reason"));
        return Result.ok();
    }

    @Operation(summary = "流失客户恢复跟进")
    @PreAuthorize("hasAuthority('crm:marketing:customer:edit')")
    @PostMapping("/{id}/restore")
    @Transactional
    public Result<Void> restore(@PathVariable Long id, @RequestBody Map<String, String> body) {
        if (!StringUtils.hasText(body.get("reason"))) throw new BizException("请填写恢复跟进原因");
        Customer current = customerMapper.selectForUpdate(id);
        if (current == null) throw new BizException("客户不存在");
        assignmentService.assertNoActiveContracts(id);
        int updated = customerMapper.update(null, new LambdaUpdateWrapper<Customer>()
                .eq(Customer::getId, id).eq(Customer::getStatus, 3)
                .set(Customer::getStatus, 1).setSql("version = version + 1"));
        if (updated != 1) throw new BizException("仅已流失客户可恢复跟进，请刷新状态");
        auditService.log("customer.restore", "customer", id, body.get("reason"));
        return Result.ok();
    }

    // ---------------- 锁定 ----------------

    @Operation(summary = "最新锁定状态（包含成交及释放历史，无记录返回 null）")
    @PreAuthorize("hasAuthority('crm:marketing:customer:query')")
    @GetMapping("/{id}/lock")
    public Result<MktCustomerLock> lock(@PathVariable Long id) {
        return Result.ok(lockService.displayLockOf(id));
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
        m.put("projectId", c.getProjectId());
        m.put("grade", c.getGrade()); m.put("referrerId", c.getReferrerId()); m.put("serviceType", c.getServiceType());
        m.put("bizLine", c.getBizLine()); m.put("signMode", c.getSignMode()); m.put("status", c.getStatus());
        m.put("attributionNote", c.getAttributionNote()); m.put("intentLevel", c.getIntentLevel()); m.put("owner", c.getOwner());
        m.putAll(assignmentService.assignmentView(c));
        if (c.getReferrerId() != null) {
            MktPromoter p = promoterMapper.selectById(c.getReferrerId());
            m.put("referrerName", p == null ? null : p.getName());
        }
        MktCustomerLock lock = lockService.displayLockOf(c.getId());
        if (lock != null) {
            m.put("lockStatus", lock.getStatus());
            LocalDateTime until = lock.getStatus() == MktLockService.LS_PRELOCK ? lock.getPrelockUntil() : lock.getLockUntil();
            m.put("lockDaysLeft", until == null ? null : Math.max(0, (Duration.between(LocalDateTime.now(), until).getSeconds() + 86_399) / 86_400));
        }
        return m;
    }
}
