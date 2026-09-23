package com.zhyq.park.marketing.service;

import cn.hutool.core.util.IdcardUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** Manual review of payout details; this is not an external identity verification. */
@Service
@RequiredArgsConstructor
public class MktPromoterAccountService {
    private final MktPromoterMapper promoters;
    private final MktPromoterAccountMapper accounts;
    private final MktWithdrawalMapper withdrawals;
    private final FieldEncryptionService encryption;
    private final MktAuditService audit;
    private final MktPartnerNoticeService notices;

    public MktPromoterAccount find(Long pid) {
        return accounts.selectOne(new LambdaQueryWrapper<MktPromoterAccount>().eq(MktPromoterAccount::getPromoterId, pid));
    }

    @Transactional
    public void submit(Long pid, Map<String, String> body) {
        MktPromoter p = promoters.selectForUpdate(pid);
        if (p == null || !Objects.equals(p.getStatus(), 1)) throw new BizException("伙伴状态异常，无法提交收款资料");
        String realName = value(body, "realName", 80);
        String idNo = value(body, "idNo", 18).toUpperCase(Locale.ROOT);
        String accountNo = value(body, "accountNo", 100);
        String bankName = body.get("bankName") == null ? "" : body.get("bankName").trim();
        if (!IdcardUtil.isValidCard18(idNo)) throw new BizException("请填写有效的18位身份证号码");
        int type;
        try { type = Integer.parseInt(body.getOrDefault("accountType", "2")); }
        catch (NumberFormatException e) { throw new BizException("请选择收款方式"); }
        if (type < 1 || type > 3) throw new BizException("收款方式无效");
        if (type == 2 && (bankName.isBlank() || bankName.length() > 120 || !accountNo.matches("[0-9]{12,30}")))
            throw new BizException("请填写12至30位银行卡号和开户行");
        if (withdrawals.selectCount(new LambdaQueryWrapper<MktWithdrawal>().eq(MktWithdrawal::getPromoterId, pid)
                .in(MktWithdrawal::getStatus, 1, 2)) > 0) throw new BizException("有处理中提现，请完成或撤回后再修改收款资料");
        MktPromoterAccount a = find(pid);
        if (a == null) { a = new MktPromoterAccount(); a.setPromoterId(pid); a.setProjectId(p.getProjectId()); }
        a.setRealName(realName); a.setIdNoEnc(encryption.encrypt(idNo)); a.setAccountType(type);
        a.setAccountNoEnc(encryption.encrypt(accountNo)); a.setAccountTail(accountNo.substring(Math.max(0, accountNo.length()-4)));
        a.setBankName(bankName); a.setReviewStatus(0);
        if (a.getId() == null) accounts.insert(a);
        else {
            int changed = accounts.update(null, new LambdaUpdateWrapper<MktPromoterAccount>()
                    .eq(MktPromoterAccount::getId, a.getId()).eq(MktPromoterAccount::getVersion, a.getVersion())
                    .set(MktPromoterAccount::getRealName, a.getRealName()).set(MktPromoterAccount::getIdNoEnc, a.getIdNoEnc())
                    .set(MktPromoterAccount::getAccountType, type).set(MktPromoterAccount::getAccountNoEnc, a.getAccountNoEnc())
                    .set(MktPromoterAccount::getAccountTail, a.getAccountTail()).set(MktPromoterAccount::getBankName, bankName)
                    .set(MktPromoterAccount::getReviewStatus, 0).set(MktPromoterAccount::getReviewReason, null)
                    .set(MktPromoterAccount::getReviewedBy, null).set(MktPromoterAccount::getVerifiedAt, null)
                    .setSql("version=version+1"));
            if (changed != 1) throw new BizException("资料已变化，请刷新后重新提交");
        }
        promoters.update(null, new LambdaUpdateWrapper<MktPromoter>().eq(MktPromoter::getId, pid).set(MktPromoter::getIdVerified, 0));
        audit.log("promoter.account.submit", "promoter", pid, "收款资料待人工审核");
    }

    @Transactional
    public void review(Long pid, Integer version, boolean pass, String reason, String operator) {
        if (promoters.selectForUpdate(pid) == null) throw new BizException("伙伴不存在");
        if (version == null) throw new BizException("请重新打开资料后审核");
        if (reason == null || reason.isBlank() || reason.length() > 500) throw new BizException("请填写审核依据（最多500字）");
        MktPromoterAccount a = find(pid);
        if (a == null) throw new BizException("伙伴尚未提交收款资料");
        int changed = accounts.update(null, new LambdaUpdateWrapper<MktPromoterAccount>()
                .eq(MktPromoterAccount::getId, a.getId()).eq(MktPromoterAccount::getVersion, version)
                .eq(MktPromoterAccount::getReviewStatus, 0)
                .set(MktPromoterAccount::getReviewStatus, pass ? 1 : 2).set(MktPromoterAccount::getReviewReason, reason.trim())
                .set(MktPromoterAccount::getReviewedBy, operator).set(MktPromoterAccount::getVerifiedAt, pass ? LocalDateTime.now() : null)
                .setSql("version=version+1"));
        if (changed != 1) throw new BizException("资料已变更或已审核，请重新打开后核对");
        promoters.update(null, new LambdaUpdateWrapper<MktPromoter>().eq(MktPromoter::getId, pid).set(MktPromoter::getIdVerified, pass ? 1 : 0));
        notices.push(pid,"收款资料审核结果", (pass ? "资料已审核通过，可申请提现。" : "资料已退回，请补充后重新提交。") + reason.trim());
        audit.log("promoter.account.review", "promoter", pid, (pass ? "通过：" : "退回：") + reason.trim());
    }

    public Map<String, Object> view(Long pid, boolean reviewDetails) {
        MktPromoterAccount a = find(pid);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("submitted", a != null);
        if (a == null) return out;
        out.put("version", a.getVersion()); out.put("realName", a.getRealName()); out.put("accountType", a.getAccountType());
        out.put("accountTail", a.getAccountTail()); out.put("bankName", a.getBankName());
        out.put("reviewStatus", a.getReviewStatus()); out.put("reviewReason", a.getReviewReason());
        out.put("verifiedAt", a.getVerifiedAt());
        if (reviewDetails) {
            out.put("idNo", encryption.decrypt(a.getIdNoEnc()));
            out.put("accountNo", encryption.decrypt(a.getAccountNoEnc()));
            audit.log("promoter.account.view", "promoter", pid, "审核人员查看收款资料");
        }
        return out;
    }

    private static String value(Map<String, String> body, String key, int max) {
        String v = body.get(key);
        if (v == null || v.isBlank() || v.trim().length() > max) throw new BizException("请完整填写收款资料，内容不能超长");
        return v.trim();
    }
}
