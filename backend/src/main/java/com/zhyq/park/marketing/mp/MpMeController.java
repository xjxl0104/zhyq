package com.zhyq.park.marketing.mp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.entity.MktPosition;
import com.zhyq.park.marketing.entity.MktPositionOverride;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktPromoterAccount;
import com.zhyq.park.marketing.entity.MktPromoterCommission;
import com.zhyq.park.marketing.entity.MktReferralOrder;
import com.zhyq.park.marketing.mapper.MktPositionMapper;
import com.zhyq.park.marketing.mapper.MktPositionOverrideMapper;
import com.zhyq.park.marketing.mapper.MktPromoterAccountMapper;
import com.zhyq.park.marketing.mapper.MktPromoterCommissionMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktReferralOrderMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktCommissionService;
import com.zhyq.park.marketing.service.MktSelfProfileService;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 伙伴小程序:资料 / 首页 / 岗位 / 团队 / 团队分配 / 海报。所有数据只回本人的(§5.2 安全)。 */
@Tag(name = "小程序-我的")
@RestController
@RequestMapping("/mp/v1")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MP')")
public class MpMeController {

    private static final String MODULE = "marketing";
    private static final String TOP_CODE = "P4";

    private final com.zhyq.park.marketing.service.MktPromoterAccountService accountService;
    private final com.zhyq.park.marketing.service.MktWithdrawalService withdrawalService;
    private final MktPromoterMapper promoterMapper;
    private final MktPromoterAccountMapper accountMapper;
    private final MktPositionMapper positionMapper;
    private final MktPositionOverrideMapper overrideMapper;
    private final MktPromoterCommissionMapper commissionMapper;
    private final MktReferralOrderMapper orderMapper;
    private final com.zhyq.park.marketing.mapper.MktServiceContractMapper contractMapper;
    private final com.zhyq.park.crm.mapper.CustomerMapper customerMapper;
    private final MpAuthService authService;
    private final FieldEncryptionService encryption;
    private final MktAuditService auditService;
    private final com.zhyq.park.common.setting.BizSettings bizSettings;
    private final MktSelfProfileService profiles;
    private final com.zhyq.park.marketing.admin.MktAccountControl accountControl;
    private final com.zhyq.park.marketing.service.MktPromoterService promoterService;

    @Operation(summary = "我的资料") @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        MktPromoter promoter = current();
        Map<String, Object> result = profile(promoter);
        result.put("superAdmin", accountControl.isSuperAdmin(promoter.getId()) && Integer.valueOf(1).equals(promoter.getStatus()));
        result.put("phoneEditable", profiles.promoterPhoneEditable(promoter));
        result.put("contactPhone", MktSelfProfileService.isMobilePhone(promoter.getPhone()) ? promoter.getPhone() : null);
        result.put("profileComplete", StringUtils.hasText(promoter.getName()) && MktSelfProfileService.isMobilePhone(promoter.getPhone()));
        return Result.ok(result);
    }

    @Operation(summary = "修改姓名、头像或补充联系电话") @PutMapping("/me")
    public Result<Void> updateMe(@RequestBody Map<String, String> body) {
        profiles.updatePromoter(MpAuthService.currentPromoterId(), body);
        return Result.ok();
    }

    @Operation(summary = "补填邀请码(仅无上级且在宽限期内)") @PostMapping("/auth/bind-invite")
    public Result<Void> bindInvite(@RequestBody Map<String, String> body) {
        authService.bindInvite(MpAuthService.currentPromoterId(), body.get("inviteCode"));
        return Result.ok();
    }

    @Operation(summary = "同意伙伴协议/隐私协议") @PostMapping("/me/agree")
    public Result<Void> agree(@RequestBody Map<String, String> body) {
        promoterMapper.update(null, new LambdaUpdateWrapper<MktPromoter>()
                .eq(MktPromoter::getId, MpAuthService.currentPromoterId())
                .set(MktPromoter::getAgreementVersion, body.getOrDefault("version", "v1"))
                .set(MktPromoter::getAgreedAt, LocalDateTime.now()));
        return Result.ok();
    }

    @Operation(summary = "收款资料及人工审核状态") @GetMapping("/me/account")
    public Result<Map<String, Object>> accountStatus() {
        return Result.ok(accountService.view(MpAuthService.currentPromoterId(), false));
    }

    @Operation(summary = "提交收款资料，等待人工审核") @PutMapping("/me/account")
    public Result<Void> account(@RequestBody Map<String, String> body) {
        accountService.submit(MpAuthService.currentPromoterId(), body);
        return Result.ok();
    }

    @Operation(summary = "首页:累计 / 可提现 / 冻结 / 本月 + 最近动态") @GetMapping("/home")
    public Result<Map<String, Object>> home() {
        Long pid = MpAuthService.currentPromoterId();
        List<MktPromoterCommission> rows = commissionMapper.selectList(new LambdaQueryWrapper<MktPromoterCommission>()
                .eq(MktPromoterCommission::getPromoterId, pid).ne(MktPromoterCommission::getStatus, MktCommissionService.C_VOID));
        BigDecimal total = BigDecimal.ZERO, frozen = BigDecimal.ZERO, settleable = BigDecimal.ZERO, month = BigDecimal.ZERO;
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        // 本月按订单发货日(event_time)统计,历史回补的佣金不会都算进回补当月;没有订单时间的回退到生成时间
        List<Long> orderIds = rows.stream().map(MktPromoterCommission::getReferralOrderId).filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, LocalDateTime> eventTimes = new HashMap<>();
        if (!orderIds.isEmpty()) for (MktReferralOrder o : orderMapper.selectBatchIds(orderIds)) if (o.getEventTime() != null) eventTimes.put(o.getId(), o.getEventTime());
        for (MktPromoterCommission c : rows) {
            LocalDateTime when = eventTimes.getOrDefault(c.getReferralOrderId(), c.getCreateTime());
            total = total.add(c.getAmount());
            if (c.getStatus() == MktCommissionService.C_FROZEN) frozen = frozen.add(c.getAmount());
            if ((c.getStatus() == MktCommissionService.C_SETTLED && c.getSign() == 1)
                    || (c.getStatus() == MktCommissionService.C_SETTLEABLE && c.getSign() == -1)) settleable = settleable.add(c.getAmount());
            if (when != null && when.isAfter(monthStart)) month = month.add(c.getAmount());
        }
        Map<String, Object> m = new HashMap<>();
        m.put("total", total); m.put("withdrawable", withdrawalService.balance(pid)); m.put("frozen", frozen); m.put("month", month);
        m.put("recent", rows.stream().sorted((a, b) -> b.getId().compareTo(a.getId())).limit(10)
                .map(c -> Map.of("amount", c.getAmount(), "status", c.getStatus(), "time", String.valueOf(c.getCreateTime()))).collect(Collectors.toList()));
        return Result.ok(m);
    }

    @Operation(summary = "我的岗位与晋升进度") @GetMapping("/position")
    public Result<Map<String, Object>> position() {
        MktPromoter p = current();
        List<MktPosition> ladder = positionMapper.selectList(new LambdaQueryWrapper<MktPosition>().eq(MktPosition::getStatus, 1).orderByAsc(MktPosition::getSort));
        MktPosition cur = ladder.stream().filter(x -> x.getCode().equals(p.getPositionCode())).findFirst().orElse(null);
        List<MktReferralOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getPromoterId, p.getId()).eq(MktReferralOrder::getStatus, MktCommissionService.ORDER_CONFIRMED)
                .ge(MktReferralOrder::getEventTime, LocalDateTime.now().minusMonths(12)));
        BigDecimal amount = orders.stream().map(MktReferralOrder::getBaseAmount).filter(a -> a != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> m = new HashMap<>();
        m.put("code", p.getPositionCode()); m.put("name", cur == null ? p.getPositionCode() : cur.getName());
        m.put("pricingMode", "CUSTOM_PER_ORDER"); m.put("canPrice", TOP_CODE.equals(p.getPositionCode()) && Integer.valueOf(1).equals(p.getStatus())); m.put("since", p.getPositionSince());
        m.put("amount12m", amount); m.put("orders12m", orders.size()); m.put("automaticReviewEnabled", false);
        return Result.ok(m);
    }

    @Operation(summary = "我的团队(直属一层,不回隔级、不回金额)") @GetMapping("/team")
    public Result<List<Map<String, Object>>> team() {
        Long pid = MpAuthService.currentPromoterId();
        List<MktPromoter> children = promoterMapper.selectList(new LambdaQueryWrapper<MktPromoter>().eq(MktPromoter::getParentId, pid).orderByDesc(MktPromoter::getId));
        Set<Long> loginable = promoterService.loginableIds(children);
        return Result.ok(children
                .stream().map(t -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", t.getId()); m.put("name", t.getName()); m.put("avatar", t.getAvatar());
                    m.put("positionCode", t.getPositionCode()); m.put("status", t.getStatus()); m.put("joinTime", t.getCreateTime());
                    m.put("loginable", loginable.contains(t.getId()));
                    return m;
                }).collect(Collectors.toList()));
    }

    @Operation(summary = "新增团队成员:登录成员必须填手机号(微信授权后自动绑定);不登录成员不填手机号")
    @PostMapping("/team/member")
    public Result<Map<String, Object>> addTeamMember(@RequestBody Map<String, String> body) {
        MktPromoter member = promoterService.addTeamMember(MpAuthService.currentPromoterId(), body.get("name"), body.get("phone"), body.get("positionCode"));
        Map<String, Object> m = new HashMap<>();
        m.put("id", member.getId()); m.put("name", member.getName()); m.put("positionCode", member.getPositionCode());
        m.put("loginable", !member.getPhone().startsWith("p:"));
        return Result.ok(m);
    }

    @Operation(summary = "给还没有手机号的团队成员补录手机号（补录后才能登录）")
    @PutMapping("/team/member/{id}/phone")
    public Result<Void> setMemberPhone(@PathVariable Long id, @RequestBody Map<String, String> body) {
        promoterService.setMemberPhone(MpAuthService.currentPromoterId(), id, body.get("phone"));
        return Result.ok();
    }

    @Operation(summary = "团队分配:本团队各岗位默认份额/下限/当前覆盖(仅顶格岗位可见)") @GetMapping("/team/allocation")
    public Result<Map<String, Object>> allocation() {
        requireTop();
        return Result.ok(Map.of("enabled", false, "positions", List.of(),
                "message", "已改为按客户设置每单金额，请进入客户定价"));
    }

    @Operation(summary = "旧团队比例配置已停用") @PutMapping("/team/allocation")
    public Result<Void> setAllocation(@RequestBody Map<String, Integer> body) {
        requireTop();
        throw new BizException("岗位固定比例已停用，请按客户设置每单金额");
    }

    @Operation(summary = "邀请海报数据:邀请码 + 小程序路径(码图由前端用 wx 接口生成)") @GetMapping("/poster")
    public Result<Map<String, Object>> poster() {
        MktPromoter p = current();
        Map<String, Object> m = new HashMap<>();
        m.put("inviteCode", p.getInviteCode()); m.put("name", p.getName());
        m.put("path", "pages/login/index?invite=" + p.getInviteCode());
        return Result.ok(m);
    }

    /** 我的合同佣金:本人佣金流水 → 出库单 → 服务合同,按合同分组(只回本人的数据)。 */
    @Operation(summary = "我的合同佣金(按合同分组)") @GetMapping("/me/contract-commissions")
    public Result<List<Map<String, Object>>> myContractCommissions() {
        Long pid = MpAuthService.currentPromoterId();
        List<MktPromoterCommission> rows = commissionMapper.selectList(new LambdaQueryWrapper<MktPromoterCommission>()
                .eq(MktPromoterCommission::getPromoterId, pid).isNotNull(MktPromoterCommission::getReferralOrderId)
                .ne(MktPromoterCommission::getStatus, MktCommissionService.C_VOID));
        if (rows.isEmpty()) return Result.ok(List.of());
        List<Long> orderIds = rows.stream().map(MktPromoterCommission::getReferralOrderId).distinct().collect(Collectors.toList());
        // 只统计出库单类(其 sourceId 指向服务合同)
        Map<Long, MktReferralOrder> orders = orderMapper.selectBatchIds(orderIds).stream()
                .filter(o -> Integer.valueOf(MktCommissionService.SOURCE_OUTBOUND).equals(o.getSourceType())
                        && o.getSourceId() != null)
                .collect(Collectors.toMap(MktReferralOrder::getId, o -> o));
        if (orders.isEmpty()) return Result.ok(List.of());
        List<Long> contractIds = orders.values().stream().map(MktReferralOrder::getSourceId).distinct().collect(Collectors.toList());
        Map<Long, com.zhyq.park.marketing.entity.MktServiceContract> contracts = contractMapper.selectBatchIds(contractIds)
                .stream().collect(Collectors.toMap(c -> c.getId(), c -> c));
        Set<Long> customerIds = contracts.values().stream().map(c -> c.getCustomerId())
                .filter(cid -> cid != null).collect(Collectors.toSet());
        Map<Long, String> customerNames = customerIds.isEmpty() ? Map.of()
                : customerMapper.selectBatchIds(customerIds).stream().collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));
        Map<Long, List<MktPromoterCommission>> byContract = new HashMap<>();
        for (MktPromoterCommission c : rows) {
            MktReferralOrder o = orders.get(c.getReferralOrderId());
            if (o == null) continue;
            byContract.computeIfAbsent(o.getSourceId(), k -> new java.util.ArrayList<>()).add(c);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, List<MktPromoterCommission>> e : byContract.entrySet()) {
            com.zhyq.park.marketing.entity.MktServiceContract c = contracts.get(e.getKey());
            if (c == null) continue;
            BigDecimal total = BigDecimal.ZERO, frozen = BigDecimal.ZERO, settleable = BigDecimal.ZERO, settled = BigDecimal.ZERO;
            int orderCount = (int) e.getValue().stream().map(MktPromoterCommission::getReferralOrderId).distinct().count();
            for (MktPromoterCommission r : e.getValue()) {
                BigDecimal amt = r.getAmount() == null ? BigDecimal.ZERO : r.getAmount();
                total = total.add(amt);
                if (r.getStatus() == MktCommissionService.C_FROZEN) frozen = frozen.add(amt);
                else if (r.getStatus() == MktCommissionService.C_SETTLEABLE) settleable = settleable.add(amt);
                else if (r.getStatus() == MktCommissionService.C_SETTLED || r.getStatus() == MktCommissionService.C_WITHDRAWN)
                    settled = settled.add(amt);
            }
            Map<String, Object> m = new HashMap<>();
            m.put("contractId", c.getId());
            m.put("contractNo", c.getContractNo());
            m.put("customerName", customerNames.get(c.getCustomerId()));
            m.put("orderCount", orderCount);
            m.put("total", total); m.put("frozen", frozen); m.put("settleable", settleable); m.put("settled", settled);
            m.put("lastTime", e.getValue().stream().map(MktPromoterCommission::getCreateTime)
                    .filter(t -> t != null).max(LocalDateTime::compareTo).map(String::valueOf).orElse(null));
            result.add(m);
        }
        result.sort((a, b) -> String.valueOf(b.get("lastTime")).compareTo(String.valueOf(a.get("lastTime"))));
        return Result.ok(result);
    }

    // ---------------- 内部 ----------------

    private MktPromoter current() {
        MktPromoter p = promoterMapper.selectById(MpAuthService.currentPromoterId());
        if (p == null) throw new BizException(401, "账号不存在");
        return p;
    }

    private MktPromoter requireTop() {
        MktPromoter p = current();
        if (!TOP_CODE.equals(p.getPositionCode()) || !Integer.valueOf(1).equals(p.getStatus())) throw new BizException("只有顶格岗位可以做团队分配");
        return p;
    }

    static Map<String, Object> profile(MktPromoter p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId()); m.put("name", p.getName()); m.put("avatar", p.getAvatar());
        m.put("phone", !MktSelfProfileService.isMobilePhone(p.getPhone()) ? null : p.getPhone().substring(0, 3) + "****" + p.getPhone().substring(7));
        m.put("inviteCode", p.getInviteCode()); m.put("positionCode", p.getPositionCode()); m.put("status", p.getStatus());
        m.put("hasParent", p.getParentId() != null); m.put("inviteDeadline", p.getInviteDeadline());
        m.put("idVerified", p.getIdVerified()); m.put("agreed", p.getAgreedAt() != null);
        return m;
    }
}
