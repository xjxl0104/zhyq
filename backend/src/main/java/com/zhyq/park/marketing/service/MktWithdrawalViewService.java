package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.workflow.service.WorkflowAccessService;
import com.zhyq.park.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** PC 与移动端共用的提现核对视图；不返回身份证、完整卡号或密文。 */
@Service
@RequiredArgsConstructor
public class MktWithdrawalViewService {
    private final MktPromoterMapper promoters;
    private final MktPromoterAccountMapper accounts;
    private final MktWithdrawalMapper withdrawals;
    private final MktWithdrawalService service;
    private final WorkflowService workflows;
    private final WorkflowAccessService access;
    private final MktWithdrawalApprovalIdentity identity;
    private final BizSettings settings;
    private final JdbcTemplate jdbc;

    public boolean has(String permission) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getAuthorities().stream().anyMatch(a -> permission.equals(a.getAuthority()));
    }

    public void requireConsoleRead() {
        if (!has("crm:marketing:withdrawal:query") && !identity.isConfiguredConsoleApprover())
            throw new BizException(403, "无权查看提现记录");
    }

    public List<Map<String, Object>> options(String keyword) {
        String term = keyword == null ? "" : keyword.trim();
        var query = new LambdaQueryWrapper<MktPromoter>();
        if (!term.isBlank()) query.and(q -> q.like(MktPromoter::getName, term)
                .or().like(MktPromoter::getPhone, term).or().like(MktPromoter::getInviteCode, term));
        return promoters.selectList(query.orderByDesc(MktPromoter::getId).last("limit 20")).stream().map(this::promoter).toList();
    }

    private Map<String, Object> promoter(MktPromoter p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId()); m.put("name", p.getName()); m.put("phone", p.jsonPhone());
        m.put("positionCode", p.getPositionCode()); m.put("inviteCode", p.getInviteCode());
        m.put("status", p.getStatus()); m.put("idVerified", p.getIdVerified());
        return m;
    }

    public Map<String, Object> manualContext(Long promoterId) {
        MktPromoter p = promoters.selectById(promoterId);
        if (p == null) throw new BizException("伙伴不存在");
        MktPromoterAccount account = accounts.selectOne(new LambdaQueryWrapper<MktPromoterAccount>().eq(MktPromoterAccount::getPromoterId, promoterId));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("promoter", promoter(p));
        Map<String, Object> a = new LinkedHashMap<>();
        if (account != null) {
            a.put("name", account.getRealName()); a.put("accountType", account.getAccountType());
            a.put("accountTail", account.getAccountTail()); a.put("bankName", account.getBankName());
            a.put("reviewStatus", account.getReviewStatus()); a.put("verifiedAt", account.getVerifiedAt());
            a.put("reviewReason", account.getReviewReason());
        }
        m.put("account", a);
        BigDecimal balance = service.balance(promoterId);
        BigDecimal cashable = balance.max(BigDecimal.ZERO).setScale(2, RoundingMode.DOWN);
        m.put("availableBalance", cashable); m.put("fractionalBalance", balance.max(BigDecimal.ZERO).subtract(cashable));
        var pending = jdbc.queryForMap("SELECT COUNT(*) AS pendingCount,COALESCE(SUM(amount),0) AS pendingAmount FROM crm_withdrawal WHERE promoter_id=? AND status IN (1,2) AND deleted=0", promoterId);
        m.putAll(pending);
        m.put("frozenAmount", jdbc.queryForObject("SELECT COALESCE(SUM(amount-COALESCE(withdrawn_amount,0)),0) FROM crm_promoter_commission WHERE promoter_id=? AND status=1 AND deleted=0", BigDecimal.class, promoterId));
        Map<String, Object> rules = service.withdrawalRules();
        m.put("withdrawalRules", rules);
        m.put("taxMode", settings.getInt("marketing", "tax_mode", 1));
        m.put("taxRate", settings.getInt("marketing", "tax_mode", 1) == 1 ? settings.getDecimal("marketing", "tax_rate", new BigDecimal("0.20")) : BigDecimal.ZERO);
        var approval = configuredFlow(); m.put("approval", approval);
        List<String> reasons = new ArrayList<>();
        if (((Number) pending.get("pendingCount")).longValue() > 0) reasons.add("已有处理中提现申请，请先核对原申请，勿重复提交");
        if (!Integer.valueOf(1).equals(p.getStatus())) reasons.add("伙伴状态异常");
        if (!Integer.valueOf(1).equals(p.getIdVerified()) || account == null || !Integer.valueOf(1).equals(account.getReviewStatus())
                || account.getVerifiedAt() == null || account.getAccountNoEnc() == null || account.getAccountNoEnc().isBlank()) reasons.add("收款资料尚未审核通过，请先完善并审核收款资料");
        if (!Boolean.TRUE.equals(rules.get("applicationOpen"))) reasons.add(String.valueOf(rules.get("applicationTime")));
        if (cashable.compareTo(MktWithdrawalService.MIN_WITHDRAWAL) < 0) reasons.add("可提现余额不足 0.01 元");
        if (approval == null) reasons.add("提现审批流程未配置或指定审批人不可用");
        m.put("canApply", reasons.isEmpty()); m.put("blockedReason", String.join("；", reasons));
        return m;
    }

    public Map<String, Object> configuredFlow() {
        var rows = jdbc.queryForList("SELECT n.name AS nodeName,n.approver_value AS assignee,u.nickname AS assigneeName,n.approver_type AS approverType,u.id AS activeUserId "
                + "FROM wf_definition d JOIN wf_node n ON n.definition_id=d.id AND n.deleted=0 "
                + "LEFT JOIN sys_user u ON n.approver_type='user' AND u.username=n.approver_value AND u.deleted=0 AND u.status=1 "
                + "WHERE d.deleted=0 AND d.status=1 AND d.biz_type='withdrawal' "
                + "AND d.id=(SELECT MAX(id) FROM wf_definition WHERE deleted=0 AND status=1 AND biz_type='withdrawal') "
                + "ORDER BY n.seq LIMIT 1");
        if (rows.isEmpty()) return null;
        var first = new LinkedHashMap<>(rows.get(0));
        String type = String.valueOf(first.remove("approverType"));
        Object activeUser = first.remove("activeUserId");
        if ("user".equals(type)) return activeUser == null ? null : first;
        if (!"role".equals(type)) return null;
        Integer assigned = jdbc.queryForObject("SELECT COUNT(*) FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id "
                + "JOIN sys_user u ON u.id=ur.user_id AND u.deleted=0 AND u.status=1 WHERE r.code=? AND r.deleted=0 AND r.status=1",Integer.class,first.get("assignee"));
        return assigned != null && assigned > 0 ? first : null;
    }

    public boolean canAct(MktWithdrawal w) {
        if (!Integer.valueOf(MktWithdrawalService.WS_PENDING).equals(w.getStatus())) return false;
        try { var task = workflows.currentPendingTask(MktWithdrawalService.WORKFLOW_BIZ_TYPE, w.getId()); return task != null && access.canAct(task); }
        catch (BizException invalid) { return false; }
    }

    public Map<String, Object> one(Long id, Long ownPromoterId) {
        MktWithdrawal w = withdrawals.selectById(id);
        if (w == null) throw new BizException("提现单不存在");
        if (ownPromoterId != null) {
            if (!ownPromoterId.equals(w.getPromoterId())) throw new BizException(403, "只能查看自己的提现记录");
        } else if (!has("crm:marketing:withdrawal:query") && !identity.isConfiguredConsoleApprover() && !canAct(w)) {
            throw new BizException(403, "无权查看此提现记录");
        }
        return describe(w, true);
    }

    public Map<String, Object> describe(MktWithdrawal w, boolean includeHistory) {
        MktPromoter p = promoters.selectById(w.getPromoterId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",w.getId()); m.put("withdrawalNo",w.getWithdrawalNo()); m.put("promoterId",w.getPromoterId());
        m.put("promoterName",p == null ? null : p.getName()); m.put("promoterPhone",p == null ? null : p.jsonPhone());
        m.put("positionCode",p == null ? null : p.getPositionCode()); m.put("inviteCode",p == null ? null : p.getInviteCode());
        m.put("amount",w.getAmount()); m.put("taxMode",w.getTaxMode()); m.put("taxAmount",w.getTaxAmount()); m.put("netAmount",w.getNetAmount());
        m.put("status",w.getStatus()); m.put("accountName",w.getAccountName()); m.put("accountType",w.getAccountType());
        m.put("accountTail",w.getAccountTail()); m.put("bankName",w.getBankName()); m.put("accountVerifiedAt",w.getAccountVerifiedAt());
        m.put("createTime",w.getCreateTime()); m.put("auditBy",w.getAuditBy()); m.put("auditAt",w.getAuditAt());
        m.put("payBy",w.getPayBy()); m.put("payAt",w.getPayAt()); m.put("payNo",w.getPayNo()); m.put("payMethod",w.getPayMethod());
        m.put("payProof",w.getPayProof()); m.put("rejectReason",w.getRejectReason()); m.put("commissionAllocations",w.getCommissionAllocations());
        m.put("title",(p == null ? "伙伴#"+w.getPromoterId() : p.getName())+" 提现 "+w.getAmount()+" 元（"+w.getWithdrawalNo()+"）");
        var history = history(w.getId());
        Map<String,Object> approval = history.isEmpty() ? null : new LinkedHashMap<>(history.get(history.size()-1));
        if (approval != null) approval.put("status", approval.get("instanceStatus"));
        m.put("approval",approval);
        if (includeHistory) m.put("history",history);
        boolean act = canAct(w); m.put("canApprove",act); m.put("canReject",act);
        m.put("canPay",Integer.valueOf(MktWithdrawalService.WS_APPROVED).equals(w.getStatus()) && has("crm:marketing:withdrawal:pay"));
        return m;
    }

    private List<Map<String,Object>> history(Long id) {
        return jdbc.queryForList("SELECT t.id AS taskId,n.name AS nodeName,t.assignee,COALESCE(u.nickname,t.assignee) AS assigneeName,"
                + "t.status,t.opinion,t.act_time AS actTime,i.status AS instanceStatus FROM wf_instance i "
                + "JOIN wf_task t ON t.instance_id=i.id AND t.deleted=0 LEFT JOIN wf_node n ON n.id=t.node_id "
                + "LEFT JOIN sys_user u ON n.approver_type='user' AND u.username=t.assignee AND u.deleted=0 "
                + "WHERE i.deleted=0 AND i.biz_type='withdrawal' AND i.biz_id=? ORDER BY i.id,t.seq,t.id", id);
    }
}
