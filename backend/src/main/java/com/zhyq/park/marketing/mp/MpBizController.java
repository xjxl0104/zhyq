package com.zhyq.park.marketing.mp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktCustomerLock;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktPromoterCommission;
import com.zhyq.park.marketing.entity.MktReferralOrder;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWithdrawal;
import com.zhyq.park.marketing.mapper.MktPromoterCommissionMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktReferralOrderMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.mapper.MktWithdrawalMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktCustomerAssignmentService;
import com.zhyq.park.marketing.service.MktCommissionService;
import com.zhyq.park.marketing.service.MktLockService;
import com.zhyq.park.marketing.service.MktWarehouseOnboardingService;
import com.zhyq.park.marketing.service.MktWithdrawalService;
import com.zhyq.park.tenant.entity.TenantMessage;
import com.zhyq.park.tenant.mapper.TenantMessageMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 伙伴小程序:推荐/报备客户、我的客户、收益、提现、消息。金额只回本人;客户不回联系方式以外的他人信息。 */
@Tag(name = "小程序-业务")
@RestController
@RequestMapping("/mp/v1")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MP')")
public class MpBizController {

    private static final String MODULE = "marketing";

    private final CustomerMapper customerMapper;
    private final MktPromoterMapper promoterMapper;
    private final MktWarehouseMapper warehouseMapper;
    private final MktPromoterCommissionMapper commissionMapper;
    private final MktReferralOrderMapper orderMapper;
    private final MktWithdrawalMapper withdrawalMapper;
    private final TenantMessageMapper messageMapper;
    private final MktLockService lockService;
    private final MktWithdrawalService withdrawalService;
    private final BizSettings bizSettings;
    private final MktAuditService auditService;
    private final MktCustomerAssignmentService assignmentService;

    // ---------------- 推荐 / 报备(§2.2 + §2.2a) ----------------

    @Operation(summary = "推荐客户 = 报备(预锁 7 天):自我推荐拒、重复客户拒(不暴露是谁)、日上限")
    @PostMapping("/referral")
    @Transactional
    public Result<Map<String, Object>> referral(@RequestBody Map<String, Object> body) {
        Long pid = MpAuthService.currentPromoterId();
        MktPromoter me = promoterMapper.selectById(pid);
        String phone = str(body, "phone");
        String name = str(body, "name");
        if (!StringUtils.hasText(name) || phone == null || !phone.matches("^1\\d{10}$")) throw new BizException("客户名称与 11 位手机号必填");
        if (phone.equals(me.getPhone())) throw new BizException("不能自我推荐");
        int cap = bizSettings.getInt(MODULE, "daily_referral_cap", 20);
        long today = customerMapper.selectCount(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getReferrerId, pid).ge(Customer::getCreateTime, LocalDate.now().atStartOfDay()));
        if (today >= cap) throw new BizException("今日推荐已达上限 " + cap + " 个");
        if (customerMapper.selectCount(new LambdaQueryWrapper<Customer>().eq(Customer::getPhone, phone)) > 0) {
            throw new BizException("该客户已在系统中(已被推荐)");
        }
        Customer c = new Customer();
        c.setName(name); c.setContact(str(body, "contact")); c.setPhone(phone);
        c.setIndustry(str(body, "industry")); c.setDemandArea(str(body, "demand"));
        Object st = body.get("serviceType");
        int serviceType;
        try { serviceType = st == null ? 2 : Integer.parseInt(st.toString()); }
        catch (NumberFormatException e) { throw new BizException("需求类型不正确"); }
        if (serviceType < 1 || serviceType > 4) throw new BizException("需求类型不正确");
        c.setServiceType(serviceType);
        c.setBizLine(c.getServiceType() == 4 ? 1 : 2);
        String intended = str(body, "warehouseId");
        if (StringUtils.hasText(intended)) {
            Long warehouseId;
            try { warehouseId = Long.valueOf(intended); }
            catch (NumberFormatException e) { throw new BizException("意向云仓不正确"); }
            if (serviceType == 4) throw new BizException("园区入驻需求不选择云仓");
            assignmentService.requireAvailableWarehouse(warehouseId, me.getProjectId());
            c.setIntendedWarehouseId(warehouseId);
        }
        c.setWarehouseAssignmentStatus(MktCustomerAssignmentService.UNASSIGNED);
        c.setReferrerId(pid);
        c.setStatus(1);
        c.setOwner("待分配");
        c.setRemark(str(body, "remark"));
        c.setAttributionNote("小程序推荐,伙伴 " + me.getInviteCode());
        c.setProjectId(me.getProjectId());
        try {
            customerMapper.insert(c);
        } catch (DuplicateKeyException dup) {
            throw new BizException("该客户已在系统中(已被推荐)");
        }
        MktCustomerLock lock = lockService.prelock(c.getId(), pid);
        auditService.log("referral.create", "customer", c.getId(), "小程序推荐");
        Map<String, Object> m = new HashMap<>();
        m.put("customerId", c.getId()); m.put("lockStatus", lock.getStatus()); m.put("prelockUntil", lock.getPrelockUntil());
        return Result.ok(m);
    }

    @Operation(summary = "我的客户:状态·评级·承接云仓·锁定剩余天数(不回金额)")
    @GetMapping("/referral/page")
    public Result<PageResult<Map<String, Object>>> myCustomers(@RequestParam(defaultValue = "1") int pageNo,
                                                               @RequestParam(defaultValue = "20") int pageSize) {
        Long pid = MpAuthService.currentPromoterId();
        IPage<Customer> p = customerMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<Customer>().eq(Customer::getReferrerId, pid).orderByDesc(Customer::getId));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords().stream().map(this::customerView).collect(Collectors.toList())));
    }

    @Operation(summary = "客户详情(只能看自己推荐的)")
    @GetMapping("/referral/{id}")
    public Result<Map<String, Object>> customer(@PathVariable Long id) {
        Customer c = customerMapper.selectById(id);
        if (c == null || !MpAuthService.currentPromoterId().equals(c.getReferrerId())) throw new BizException(403, "无权查看");
        return Result.ok(customerView(c));
    }

    @Operation(summary = "申请延期锁定(运营审核;阶段 B 直接按规则延期)")
    @PostMapping("/referral/{id}/extend")
    public Result<Void> extend(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Customer c = customerMapper.selectById(id);
        if (c == null || !MpAuthService.currentPromoterId().equals(c.getReferrerId())) throw new BizException(403, "无权操作");
        MktCustomerLock lock = lockService.activeLockOf(id);
        if (lock == null) throw new BizException("当前没有有效锁定");
        lockService.extend(lock.getId(), "伙伴申请:" + body.getOrDefault("reason", "近 60 天有到访/方案"));
        return Result.ok();
    }

    @Operation(summary = "意向云仓下拉(已上线；线下履约不要求外部 ERP 接入)")
    @GetMapping("/warehouses")
    public Result<List<Map<String, Object>>> warehouses() {
        return Result.ok(warehouseMapper.selectList(new LambdaQueryWrapper<MktWarehouse>()
                .eq(MktWarehouse::getJoinStatus, MktWarehouseOnboardingService.JS_ONLINE)
                .and(q -> q.eq(MktWarehouse::getOrderMode, "manual").or().eq(MktWarehouse::getErpStatus, MktWarehouseOnboardingService.ERP_LIVE)))
                .stream().map(w -> Map.<String, Object>of("id", w.getId(), "code", w.getCode(), "name", w.getName(), "region", String.valueOf(w.getRegion())))
                .collect(Collectors.toList()));
    }

    // ---------------- 收益 / 提现 ----------------

    @Operation(summary = "收益明细(含 评级×份额/级差=比例)")
    @GetMapping("/commission/page")
    public Result<PageResult<Map<String, Object>>> commissions(@RequestParam(defaultValue = "1") int pageNo,
                                                               @RequestParam(defaultValue = "20") int pageSize,
                                                               @RequestParam(required = false) Integer status) {
        Long pid = MpAuthService.currentPromoterId();
        IPage<MktPromoterCommission> p = commissionMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<MktPromoterCommission>().eq(MktPromoterCommission::getPromoterId, pid)
                        .eq(status != null, MktPromoterCommission::getStatus, status).orderByDesc(MktPromoterCommission::getId));
        List<Map<String, Object>> rows = p.getRecords().stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId()); m.put("amount", c.getAmount()); m.put("sign", c.getSign()); m.put("status", c.getStatus());
            m.put("positionCode", c.getPositionCode()); m.put("sharePct", c.getSharePct()); m.put("diffPct", c.getDiffPct());
            m.put("rate", c.getRate()); m.put("baseAmount", c.getBaseAmount()); m.put("unfreezeAt", c.getUnfreezeAt()); m.put("time", c.getCreateTime());
            MktReferralOrder o = orderMapper.selectById(c.getReferralOrderId());
            if (o != null) {
                m.put("sourceType", o.getSourceType()); m.put("grade", o.getCustomerGrade()); m.put("poolFactor", o.getPoolFactor());
                Customer cu = o.getCustomerId() == null ? null : customerMapper.selectById(o.getCustomerId());
                m.put("customerName", cu == null ? null : cu.getName());
            }
            return m;
        }).collect(Collectors.toList());
        return Result.ok(PageResult.of(p.getTotal(), rows));
    }

    @Operation(summary = "可提现余额 + 最低提现") @GetMapping("/withdrawal/balance")
    public Result<Map<String, Object>> balance() {
        Long pid = MpAuthService.currentPromoterId();
        MktPromoter me = promoterMapper.selectById(pid);
        Map<String, Object> m = new HashMap<>();
        m.put("balance", withdrawalService.balance(pid));
        m.put("minWithdraw", bizSettings.getDecimal(MODULE, "min_withdraw", new BigDecimal("100")));
        m.put("idVerified", me.getIdVerified());
        m.put("taxMode", bizSettings.getInt(MODULE, "tax_mode", 1));
        m.put("taxRate", bizSettings.getDecimal(MODULE, "tax_rate", new BigDecimal("0.20")));
        return Result.ok(m);
    }

    @Operation(summary = "申请提现(需实名)") @PostMapping("/withdrawal")
    public Result<MktWithdrawal> withdraw(@RequestBody Map<String, Object> body) {
        Long pid = MpAuthService.currentPromoterId();
        MktPromoter me = promoterMapper.selectById(pid);
        if (!Integer.valueOf(1).equals(me.getIdVerified())) throw new BizException("请先完成实名与收款账户");
        return Result.ok(withdrawalService.apply(pid, new BigDecimal(body.get("amount").toString())));
    }

    @Operation(summary = "提现记录") @GetMapping("/withdrawal/page")
    public Result<PageResult<MktWithdrawal>> withdrawals(@RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        IPage<MktWithdrawal> p = withdrawalMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<MktWithdrawal>().eq(MktWithdrawal::getPromoterId, MpAuthService.currentPromoterId()).orderByDesc(MktWithdrawal::getId));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "消息(复用 tenant_message,tenant_ref_id = 伙伴 id,channel=mp)") @GetMapping("/notice/page")
    public Result<PageResult<TenantMessage>> notices(@RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        IPage<TenantMessage> p = messageMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<TenantMessage>().eq(TenantMessage::getTenantRefId, MpAuthService.currentPromoterId())
                        .eq(TenantMessage::getChannel, "mp").orderByDesc(TenantMessage::getId));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    // ---------------- 内部 ----------------

    private Map<String, Object> customerView(Customer c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId()); m.put("name", c.getName()); m.put("contact", c.getContact());
        m.put("phone", c.getPhone() == null || c.getPhone().length() < 11 ? c.getPhone() : c.getPhone().substring(0, 3) + "****" + c.getPhone().substring(7));
        m.put("grade", c.getGrade()); m.put("serviceType", c.getServiceType()); m.put("status", c.getStatus()); m.put("createTime", c.getCreateTime());
        m.putAll(assignmentService.assignmentView(c));
        MktCustomerLock lock = lockService.displayLockOf(c.getId());
        if (lock != null) {
            m.put("lockStatus", lock.getStatus());
            LocalDateTime until = lock.getStatus() == MktLockService.LS_PRELOCK ? lock.getPrelockUntil() : lock.getLockUntil();
            m.put("lockDaysLeft", until == null ? null : Math.max(0, (Duration.between(LocalDateTime.now(), until).getSeconds() + 86_399) / 86_400));
            m.put("canExtend", lock.getStatus() == MktLockService.LS_LOCKED && (lock.getExtendedCount() == null || lock.getExtendedCount() == 0));
        }
        return m;
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString().trim();
    }
}
