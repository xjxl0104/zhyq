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

    @Operation(summary = "我的资料") @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(profile(current()));
    }

    @Operation(summary = "改昵称/头像") @PutMapping("/me")
    public Result<Void> updateMe(@RequestBody Map<String, String> body) {
        promoterMapper.update(null, new LambdaUpdateWrapper<MktPromoter>()
                .eq(MktPromoter::getId, MpAuthService.currentPromoterId())
                .set(StringUtils.hasText(body.get("name")), MktPromoter::getName, body.get("name"))
                .set(StringUtils.hasText(body.get("avatar")), MktPromoter::getAvatar, body.get("avatar")));
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

    @Operation(summary = "实名 + 收款账户(身份证/账号加密存,只回尾号)") @PutMapping("/me/account")
    public Result<Void> account(@RequestBody Map<String, String> body) {
        Long pid = MpAuthService.currentPromoterId();
        String idNo = body.get("idNo");
        String accountNo = body.get("accountNo");
        if (!StringUtils.hasText(body.get("realName")) || !StringUtils.hasText(idNo) || !StringUtils.hasText(accountNo)) {
            throw new BizException("姓名、身份证、收款账号必填");
        }
        MktPromoterAccount a = accountMapper.selectOne(new LambdaQueryWrapper<MktPromoterAccount>().eq(MktPromoterAccount::getPromoterId, pid));
        boolean isNew = a == null;
        if (isNew) { a = new MktPromoterAccount(); a.setPromoterId(pid); }
        a.setRealName(body.get("realName"));
        a.setIdNoEnc(encryption.encrypt(idNo));
        a.setAccountType(Integer.valueOf(body.getOrDefault("accountType", "2")));
        a.setAccountNoEnc(encryption.encrypt(accountNo));
        a.setAccountTail(accountNo.length() > 4 ? accountNo.substring(accountNo.length() - 4) : accountNo);
        a.setBankName(body.get("bankName"));
        a.setVerifiedAt(LocalDateTime.now());
        if (isNew) accountMapper.insert(a); else accountMapper.updateById(a);
        promoterMapper.update(null, new LambdaUpdateWrapper<MktPromoter>().eq(MktPromoter::getId, pid).set(MktPromoter::getIdVerified, 1));
        auditService.log("promoter.account", "promoter", pid, "小程序实名/账户");
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
        m.put("total", total); m.put("withdrawable", settleable); m.put("frozen", frozen); m.put("month", month);
        m.put("recent", rows.stream().sorted((a, b) -> b.getId().compareTo(a.getId())).limit(10)
                .map(c -> Map.of("amount", c.getAmount(), "status", c.getStatus(), "time", String.valueOf(c.getCreateTime()))).collect(Collectors.toList()));
        return Result.ok(m);
    }

    @Operation(summary = "我的岗位与晋升进度") @GetMapping("/position")
    public Result<Map<String, Object>> position() {
        MktPromoter p = current();
        List<MktPosition> ladder = positionMapper.selectList(new LambdaQueryWrapper<MktPosition>().eq(MktPosition::getStatus, 1).orderByAsc(MktPosition::getSort));
        MktPosition cur = ladder.stream().filter(x -> x.getCode().equals(p.getPositionCode())).findFirst().orElse(null);
        MktPosition next = cur == null ? null : ladder.stream().filter(x -> x.getSort() > cur.getSort()).findFirst().orElse(null);
        List<MktReferralOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getPromoterId, p.getId()).eq(MktReferralOrder::getStatus, MktCommissionService.ORDER_CONFIRMED)
                .ge(MktReferralOrder::getEventTime, LocalDateTime.now().minusMonths(12)));
        BigDecimal amount = orders.stream().map(MktReferralOrder::getBaseAmount).filter(a -> a != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> m = new HashMap<>();
        m.put("code", p.getPositionCode()); m.put("name", cur == null ? p.getPositionCode() : cur.getName());
        m.put("sharePct", cur == null ? null : cur.getSharePct()); m.put("since", p.getPositionSince());
        m.put("amount12m", amount); m.put("orders12m", orders.size());
        if (next != null) {
            m.put("next", Map.of("code", next.getCode(), "name", next.getName(), "needAmount", next.getPromoteAmount(), "needOrders", next.getPromoteOrders()));
        }
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
        MktPromoter p = requireTop();
        List<MktPosition> ladder = positionMapper.selectList(new LambdaQueryWrapper<MktPosition>().eq(MktPosition::getStatus, 1).orderByAsc(MktPosition::getSort));
        Map<String, Integer> ov = overrideMapper.selectList(new LambdaQueryWrapper<MktPositionOverride>().eq(MktPositionOverride::getOwnerPromoterId, p.getId()))
                .stream().collect(Collectors.toMap(MktPositionOverride::getPositionCode, MktPositionOverride::getSharePct));
        Map<String, Object> m = new HashMap<>();
        m.put("enabled", bizSettings.getBoolean(MODULE, "override_enabled", true));
        m.put("positions", ladder.stream().filter(x -> !TOP_CODE.equals(x.getCode())).map(x -> Map.of(
                "code", x.getCode(), "name", x.getName(), "defaultPct", x.getSharePct(), "minPct", x.getShareMinPct(),
                "currentPct", ov.getOrDefault(x.getCode(), x.getSharePct()))).collect(Collectors.toList()));
        return Result.ok(m);
    }

    @Operation(summary = "团队分配:下调某岗位份额(min ≤ 值 ≤ 默认,仍递增)") @PutMapping("/team/allocation")
    public Result<Void> setAllocation(@RequestBody Map<String, Integer> body) {
        if (!bizSettings.getBoolean(MODULE, "override_enabled", true)) throw new BizException("园区已关闭团队分配功能");
        MktPromoter p = requireTop();
        List<MktPosition> ladder = positionMapper.selectList(new LambdaQueryWrapper<MktPosition>().eq(MktPosition::getStatus, 1).orderByAsc(MktPosition::getSort));
        int prev = 0;
        for (MktPosition x : ladder) {
            Integer v = body.get(x.getCode());
            int val = v == null || TOP_CODE.equals(x.getCode()) ? x.getSharePct() : v;
            if (val < x.getShareMinPct() || val > x.getSharePct()) throw new BizException(x.getName() + " 份额须在 " + x.getShareMinPct() + "–" + x.getSharePct() + " 之间");
            if (val <= prev) throw new BizException(x.getName() + " 份额必须高于下一级");
            prev = val;
        }
        for (MktPosition x : ladder) {
            Integer v = body.get(x.getCode());
            if (v == null || TOP_CODE.equals(x.getCode())) continue;
            MktPositionOverride o = overrideMapper.selectOne(new LambdaQueryWrapper<MktPositionOverride>()
                    .eq(MktPositionOverride::getOwnerPromoterId, p.getId()).eq(MktPositionOverride::getPositionCode, x.getCode()));
            if (o == null) { o = new MktPositionOverride(); o.setOwnerPromoterId(p.getId()); o.setPositionCode(x.getCode()); o.setSharePct(v); overrideMapper.insert(o); }
            else { o.setSharePct(v); overrideMapper.updateById(o); }
        }
        auditService.log("position.override", "promoter", p.getId(), "钻石合伙人团队分配", null, body);
        return Result.ok();
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
        if (!TOP_CODE.equals(p.getPositionCode())) throw new BizException("只有顶格岗位可以做团队分配");
        return p;
    }

    static Map<String, Object> profile(MktPromoter p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId()); m.put("name", p.getName()); m.put("avatar", p.getAvatar());
        m.put("phone", p.getPhone() == null || p.getPhone().length() < 11 ? p.getPhone() : p.getPhone().substring(0, 3) + "****" + p.getPhone().substring(7));
        m.put("inviteCode", p.getInviteCode()); m.put("positionCode", p.getPositionCode()); m.put("status", p.getStatus());
        m.put("hasParent", p.getParentId() != null); m.put("inviteDeadline", p.getInviteDeadline());
        m.put("idVerified", p.getIdVerified()); m.put("agreed", p.getAgreedAt() != null);
        return m;
    }
}
