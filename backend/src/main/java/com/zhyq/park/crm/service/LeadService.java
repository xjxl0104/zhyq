package com.zhyq.park.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Lead;
import com.zhyq.park.crm.mapper.LeadMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 线索服务:客户编号生成 + 白名单更新。
 *
 * <p>编号 KH-0001 按库中最大号递增,不用随机数 —— 登记表是人工按顺序看的,跳号会让人以为丢了记录。
 * 唯一键 uk_lead_no 兜底并发。</p>
 *
 * <p>update 走白名单:编号、跟进统计(lastFollowDate/followCount)、审计字段一律不接受入参,
 * 前者是服务端发号,后者由跟进记录回写。口径对齐 PurPlanController.update。</p>
 */
@Service
@RequiredArgsConstructor
public class LeadService {

    /** 状态:登记表 6 态 */
    public static final int ST_PENDING = 1;   // 待跟进
    public static final int ST_FOLLOWING = 2; // 跟进中
    public static final int ST_VISITED = 3;   // 已约看仓/已对接
    public static final int ST_QUOTED = 4;    // 已报价/洽谈中
    public static final int ST_SIGNED = 5;    // 已签约/已成交
    public static final int ST_LOST = 6;      // 已流失/暂缓

    private static final String NO_PREFIX = "KH-";

    private final LeadMapper leadMapper;
    private final com.zhyq.park.marketing.mapper.MktPromoterMapper promoterMapper;

    @Transactional(rollbackFor = Exception.class)
    public Long create(Lead lead) {
        if (lead.getReferrerId() != null) {
            var promoter = promoterMapper.selectForUpdate(lead.getReferrerId());
            if (promoter == null || !Integer.valueOf(1).equals(promoter.getStatus()))
                throw new BizException("推荐伙伴不存在或状态异常");
        }
        lead.setId(null);
        lead.setLeadNo(nextLeadNo());
        // 跟进统计归服务端,由跟进记录回写
        lead.setFollowCount(0);
        lead.setLastFollowDate(null);
        if (lead.getStatus() == null) {
            lead.setStatus(ST_PENDING);
        }
        validate(lead);
        leadMapper.insert(lead);
        return lead.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Lead lead) {
        if (lead.getId() == null) {
            throw new BizException("缺少线索ID");
        }
        validate(lead);
        int updated = leadMapper.update(null, new LambdaUpdateWrapper<Lead>()
                .eq(Lead::getId, lead.getId())
                .set(Lead::getRegisterDate, lead.getRegisterDate())
                .set(Lead::getSource, lead.getSource())
                .set(Lead::getContact, lead.getContact())
                .set(Lead::getPhone, lead.getPhone())
                .set(Lead::getCompany, lead.getCompany())
                .set(Lead::getCustomerType, lead.getCustomerType())
                .set(Lead::getCoopMode, lead.getCoopMode())
                .set(Lead::getDemandArea, lead.getDemandArea())
                .set(Lead::getGoodsType, lead.getGoodsType())
                .set(Lead::getOrderVolume, lead.getOrderVolume())
                .set(Lead::getCoopPeriod, lead.getCoopPeriod())
                .set(Lead::getBudgetPrice, lead.getBudgetPrice())
                .set(Lead::getIntentPark, lead.getIntentPark())
                .set(Lead::getRegion, lead.getRegion())
                .set(Lead::getGrade, lead.getGrade())
                .set(Lead::getOwnerName, lead.getOwnerName())
                .set(Lead::getStatus, lead.getStatus())
                .set(Lead::getNextFollow, lead.getNextFollow())
                .set(Lead::getRemark, lead.getRemark()));
        if (updated == 0) {
            throw new BizException("线索不存在或已删除");
        }
    }

    /** 下一个客户编号:KH- + 4 位顺序号,库中最大号 +1(含已删除的行,避免重发撞唯一键)。 */
    String nextLeadNo() {
        return NO_PREFIX + String.format("%04d", leadMapper.maxNoSeq(NO_PREFIX) + 1);
    }

    /** 从 KH-0007 取出 7;取不出按 0 计,让新号从 0001 开始。 */
    static int parseSeq(String leadNo) {
        if (leadNo == null || !leadNo.startsWith(NO_PREFIX)) {
            return 0;
        }
        try {
            return Integer.parseInt(leadNo.substring(NO_PREFIX.length()).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void validate(Lead lead) {
        if (lead.getContact() == null || lead.getContact().isBlank()) {
            throw new BizException("客户姓名不能为空");
        }
        Integer st = lead.getStatus();
        if (st != null && (st < ST_PENDING || st > ST_LOST)) {
            throw new BizException("线索状态不合法");
        }
    }
}
