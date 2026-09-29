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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final MpAuthService authService;
    private final FieldEncryptionService encryption;
    private final MktAuditService auditService;
    private final com.zhyq.park.common.setting.BizSettings bizSettings;
    private final MktSelfProfileService profiles;

    @Operation(summary = "我的资料") @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        MktPromoter promoter = current();
        Map<String, Object> result = profile(promoter);
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
        for (MktPromoterCommission c : rows) {
            total = total.add(c.getAmount());
            if (c.getStatus() == MktCommissionService.C_FROZEN) frozen = frozen.add(c.getAmount());
            if ((c.getStatus() == MktCommissionService.C_SETTLED && c.getSign() == 1)
                    || (c.getStatus() == MktCommissionService.C_SETTLEABLE && c.getSign() == -1)) settleable = settleable.add(c.getAmount());
            if (c.getCreateTime() != null && c.getCreateTime().isAfter(monthStart)) month = month.add(c.getAmount());
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
        return Result.ok(promoterMapper.selectList(new LambdaQueryWrapper<MktPromoter>().eq(MktPromoter::getParentId, pid).orderByDesc(MktPromoter::getId))
                .stream().map(t -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", t.getId()); m.put("name", t.getName()); m.put("avatar", t.getAvatar());
                    m.put("positionCode", t.getPositionCode()); m.put("status", t.getStatus()); m.put("joinTime", t.getCreateTime());
                    return m;
                }).collect(Collectors.toList()));
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
