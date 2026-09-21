package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktPositionHistory;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktPromoterCommission;
import com.zhyq.park.marketing.entity.MktReferralOrder;
import com.zhyq.park.marketing.entity.MktWithdrawal;
import com.zhyq.park.marketing.mapper.MktPositionHistoryMapper;
import com.zhyq.park.marketing.mapper.MktPromoterCommissionMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktReferralOrderMapper;
import com.zhyq.park.marketing.mapper.MktWithdrawalMapper;
import com.zhyq.park.marketing.service.MktPositionReviewService;
import com.zhyq.park.marketing.service.MktPromoterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "全民营销-伙伴管理")
@RestController
@RequestMapping("/crm/marketing/promoter")
@RequiredArgsConstructor
public class MktPromoterController {

    private final MktPromoterMapper promoterMapper;
    private final MktPromoterService promoterService;
    private final MktPositionReviewService reviewService;
    private final MktPositionHistoryMapper historyMapper;
    private final MktPromoterCommissionMapper commissionMapper;
    private final MktReferralOrderMapper orderMapper;
    private final MktWithdrawalMapper withdrawalMapper;
    private final CustomerMapper customerMapper;

    @Operation(summary = "分页")
    @PreAuthorize("hasAuthority('crm:marketing:promoter:query')")
    @GetMapping("/page")
    public Result<PageResult<PromoterVO>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                @RequestParam(defaultValue = "10") int pageSize,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String positionCode,
                                                @RequestParam(required = false) Integer status,
                                                @RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<MktPromoter> qw = new LambdaQueryWrapper<MktPromoter>()
                .and(StringUtils.hasText(keyword), w -> w.like(MktPromoter::getName, keyword)
                        .or().like(MktPromoter::getPhone, keyword).or().eq(MktPromoter::getInviteCode, keyword))
                .eq(StringUtils.hasText(positionCode), MktPromoter::getPositionCode, positionCode)
                .eq(status != null, MktPromoter::getStatus, status)
                .eq(projectId != null, MktPromoter::getProjectId, projectId)
                .orderByDesc(MktPromoter::getId);
        IPage<MktPromoter> p = promoterMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords().stream().map(PromoterVO::of).collect(java.util.stream.Collectors.toList())));
    }

    @Operation(summary = "详情")
    @PreAuthorize("hasAuthority('crm:marketing:promoter:query')")
    @GetMapping("/{id}")
    public Result<PromoterVO> get(@PathVariable Long id) {
        MktPromoter p = promoterMapper.selectById(id);
        if (p == null) throw new BizException("伙伴不存在");
        return Result.ok(PromoterVO.of(p));
    }

    @Operation(summary = "直属团队(一层)")
    @PreAuthorize("hasAuthority('crm:marketing:promoter:query')")
    @GetMapping("/{id}/team")
    public Result<List<PromoterVO>> team(@PathVariable Long id) {
        return Result.ok(promoterMapper.selectList(new LambdaQueryWrapper<MktPromoter>()
                .eq(MktPromoter::getParentId, id).orderByDesc(MktPromoter::getId)).stream().map(PromoterVO::of).collect(java.util.stream.Collectors.toList()));
    }

    /** 伙伴对外视图:剔除 openid/unionid/registerIp/deviceId 等登录与设备身份(最低角色可批量拉,不能外泄 PII)。 */
    public static record PromoterVO(Long id, String name, String phone, String avatar, String inviteCode, Long parentId,
                                    String path, String positionCode, LocalDateTime positionSince, Integer status,
                                    Integer isInternal, String agreementVersion, LocalDateTime agreedAt, Integer idVerified,
                                    LocalDateTime bindTime, LocalDateTime inviteDeadline, LocalDateTime lastLogin,
                                    String source, String remark, Long projectId) {
        static PromoterVO of(MktPromoter p) {
            return new PromoterVO(p.getId(), p.getName(), p.getPhone(), p.getAvatar(), p.getInviteCode(), p.getParentId(),
                    p.getPath(), p.getPositionCode(), p.getPositionSince(), p.getStatus(), p.getIsInternal(),
                    p.getAgreementVersion(), p.getAgreedAt(), p.getIdVerified(), p.getBindTime(), p.getInviteDeadline(),
                    p.getLastLogin(), p.getSource(), p.getRemark(), p.getProjectId());
        }
    }

    @Operation(summary = "推荐的客户")
    @PreAuthorize("hasAuthority('crm:marketing:promoter:query')")
    @GetMapping("/{id}/customers")
    public Result<PageResult<Customer>> customers(@PathVariable Long id, @RequestParam(defaultValue = "1") int pageNo,
                                                  @RequestParam(defaultValue = "10") int pageSize) {
        IPage<Customer> p = customerMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<Customer>().eq(Customer::getReferrerId, id).orderByDesc(Customer::getId));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "成交的计佣订单")
    @PreAuthorize("hasAuthority('crm:marketing:promoter:query')")
    @GetMapping("/{id}/orders")
    public Result<PageResult<MktReferralOrder>> orders(@PathVariable Long id, @RequestParam(defaultValue = "1") int pageNo,
                                                       @RequestParam(defaultValue = "10") int pageSize) {
        IPage<MktReferralOrder> p = orderMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<MktReferralOrder>().eq(MktReferralOrder::getPromoterId, id).orderByDesc(MktReferralOrder::getId));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "佣金流水(作为收款人)")
    @PreAuthorize("hasAuthority('crm:marketing:promoter:query')")
    @GetMapping("/{id}/commissions")
    public Result<PageResult<MktPromoterCommission>> commissions(@PathVariable Long id, @RequestParam(defaultValue = "1") int pageNo,
                                                                 @RequestParam(defaultValue = "10") int pageSize) {
        IPage<MktPromoterCommission> p = commissionMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<MktPromoterCommission>().eq(MktPromoterCommission::getPromoterId, id).orderByDesc(MktPromoterCommission::getId));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "提现单")
    @PreAuthorize("hasAuthority('crm:marketing:promoter:query')")
    @GetMapping("/{id}/withdrawals")
    public Result<PageResult<MktWithdrawal>> withdrawals(@PathVariable Long id, @RequestParam(defaultValue = "1") int pageNo,
                                                         @RequestParam(defaultValue = "10") int pageSize) {
        IPage<MktWithdrawal> p = withdrawalMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<MktWithdrawal>().eq(MktWithdrawal::getPromoterId, id).orderByDesc(MktWithdrawal::getId));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "岗位变更史")
    @PreAuthorize("hasAuthority('crm:marketing:promoter:query')")
    @GetMapping("/{id}/history")
    public Result<List<MktPositionHistory>> history(@PathVariable Long id) {
        return Result.ok(historyMapper.selectList(new LambdaQueryWrapper<MktPositionHistory>()
                .eq(MktPositionHistory::getPromoterId, id).orderByDesc(MktPositionHistory::getId)));
    }

    @Data
    public static class ManualReq {
        private String name;
        private String phone;
        private String parentInviteCode;
        private String positionCode;
        private String remark;
        private Long projectId;
    }

    @Operation(summary = "后台手工录入伙伴(阶段 A 无小程序时用)")
    @PreAuthorize("hasAuthority('crm:marketing:promoter:edit')")
    @PostMapping("/manual")
    public Result<MktPromoter> manual(@RequestBody ManualReq req) {
        MktPromoter p = new MktPromoter();
        p.setName(req.getName());
        p.setPhone(req.getPhone());
        p.setPositionCode(req.getPositionCode());
        p.setRemark(req.getRemark());
        p.setProjectId(req.getProjectId());
        return Result.ok(promoterService.register(p, req.getParentInviteCode(), "admin"));
    }

    @Operation(summary = "审核(待审核 → 正常 / 拒绝)")
    @PreAuthorize("hasAuthority('crm:marketing:promoter:audit')")
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean pass = Boolean.TRUE.equals(body.get("pass"));
        promoterService.audit(id, pass, (String) body.get("reason"));
        return Result.ok();
    }

    @Operation(summary = "手动调岗(写原因,90 天内不自动降级)")
    @PreAuthorize("hasAuthority('crm:marketing:promoter:edit')")
    @PostMapping("/{id}/position")
    public Result<Void> position(@PathVariable Long id, @RequestBody Map<String, String> body) {
        reviewService.changeManually(id, body.get("code"), body.get("reason"));
        return Result.ok();
    }

    @Operation(summary = "改上级(整棵子树 path 一起改)")
    @PreAuthorize("hasAuthority('crm:marketing:promoter:edit')")
    @PostMapping("/{id}/parent")
    public Result<Void> parent(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object pid = body.get("parentId");
        Long parentId = pid == null ? null : Long.valueOf(pid.toString());
        promoterService.changeParent(id, parentId, (String) body.get("reason"));
        return Result.ok();
    }

    @Operation(summary = "冻结")
    @PreAuthorize("hasAuthority('crm:marketing:promoter:edit')")
    @PostMapping("/{id}/freeze")
    public Result<Void> freeze(@PathVariable Long id, @RequestBody Map<String, String> body) {
        promoterService.freeze(id, body.get("reason"));
        return Result.ok();
    }

    @Operation(summary = "解冻")
    @PreAuthorize("hasAuthority('crm:marketing:promoter:edit')")
    @PostMapping("/{id}/unfreeze")
    public Result<Void> unfreeze(@PathVariable Long id) {
        promoterService.unfreeze(id);
        return Result.ok();
    }

    @Operation(summary = "退出(份额园区留存)")
    @PreAuthorize("hasAuthority('crm:marketing:promoter:edit')")
    @PostMapping("/{id}/exit")
    public Result<Void> exit(@PathVariable Long id, @RequestBody Map<String, String> body) {
        promoterService.exit(id, body.get("reason"));
        return Result.ok();
    }

    @Operation(summary = "重置邀请码")
    @PreAuthorize("hasAuthority('crm:marketing:promoter:edit')")
    @PostMapping("/{id}/reset-invite")
    public Result<String> resetInvite(@PathVariable Long id) {
        return Result.ok(promoterService.resetInvite(id));
    }
}
