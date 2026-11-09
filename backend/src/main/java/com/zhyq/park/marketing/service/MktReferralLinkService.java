package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MktReferralLinkService {
    private final JdbcTemplate jdbc;
    private final MktPromoterMapper promoters;
    private final MktWarehouseMapper warehouses;
    private final MktReferralService referrals;
    private final ObjectMapper json;

    @Transactional
    public Map<String,Object> link(Long partnerId) {
        active(promoters.selectForUpdate(partnerId));
        List<String> existing = jdbc.query("SELECT token FROM mkt_customer_referral_link WHERE promoter_id = ?",
                (rs, n) -> rs.getString(1), partnerId);
        String token;
        if (existing.isEmpty()) {
            token = UUID.randomUUID().toString().replace("-", "");
            jdbc.update("INSERT INTO mkt_customer_referral_link(token,promoter_id) VALUES (?,?)", token, partnerId);
        } else token = existing.get(0);
        return Map.of("token", token, "path", "pages/customer-intake/index?token=" + token);
    }

    public Map<String,Object> form(String token) {
        MktPromoter partner = active(promoters.selectById(partnerId(token)));
        Long tenantId = partner.getTenantId() == null ? com.zhyq.park.common.config.MyMetaObjectHandler.DEFAULT_TENANT_ID : partner.getTenantId();
        var query = new LambdaQueryWrapper<MktWarehouse>().eq(MktWarehouse::getTenantId, tenantId)
                .eq(MktWarehouse::getJoinStatus, MktWarehouseOnboardingService.JS_ONLINE)
                .and(q -> q.in(MktWarehouse::getOrderMode, "yunshan", "manual").or().eq(MktWarehouse::getErpStatus, MktWarehouseOnboardingService.ERP_LIVE));
        if (partner.getProjectId() != null) query.and(q -> q.eq(MktWarehouse::getProjectId, partner.getProjectId()).or().isNull(MktWarehouse::getProjectId));
        List<Map<String,Object>> options = warehouses.selectList(query).stream()
                .map(w -> Map.<String,Object>of("id", w.getId(), "name", w.getName())).toList();
        return Map.of("partnerName", partner.getName() == null ? "园区伙伴" : partner.getName(), "warehouses", options);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String,Object> submit(String token, Map<String,Object> body) {
        Long partnerId = partnerId(token);
        // Same partner-row lock as direct referral: protects quota and concurrent double taps.
        active(promoters.selectForUpdate(partnerId));
        String requestId = MktReferralRequirements.text(body.get("submissionId"), 64, "提交编号");
        if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{16,64}")) throw new BizException("请重新打开填报页面后提交");
        if (!Boolean.TRUE.equals(body.get("agreed"))) throw new BizException("请先确认信息提交授权");
        String hash = hash(body);
        List<Map<String,Object>> receipts = jdbc.queryForList(
                "SELECT request_hash,result_json FROM mkt_customer_referral_submission WHERE token=? AND submission_id=?", token, requestId);
        if (!receipts.isEmpty()) {
            var saved = receipts.get(0);
            if (!hash.equals(saved.get("request_hash"))) throw new BizException("该提交已处理，请勿重复修改提交");
            try { return json.readValue(saved.get("result_json").toString(), new TypeReference<Map<String,Object>>() {}); }
            catch (Exception e) { throw new BizException("提交结果暂不可读取，请联系运营"); }
        }
        Map<String,Object> result = referrals.create(partnerId, body);
        try {
            jdbc.update("INSERT INTO mkt_customer_referral_submission(token,submission_id,request_hash,result_json) VALUES (?,?,?,?)",
                    token, requestId, hash, json.writeValueAsString(result));
        } catch (org.springframework.dao.DataAccessException e) { throw e; }
        catch (Exception e) { throw new BizException("提交结果保存失败，请重试"); }
        return result;
    }

    private Long partnerId(String token) {
        if (token == null || !token.matches("[a-f0-9]{32}")) throw unavailable();
        List<Long> ids = jdbc.query("SELECT promoter_id FROM mkt_customer_referral_link WHERE token=?",
                (rs,n) -> rs.getLong(1), token);
        if (ids.isEmpty()) throw unavailable();
        return ids.get(0);
    }
    private MktPromoter active(MktPromoter p) {
        if (p == null || !Integer.valueOf(1).equals(p.getStatus())) throw unavailable();
        return p;
    }
    private static BizException unavailable() { return new BizException(404, "此客户填报入口暂不可用，请联系分享人"); }
    private String hash(Map<String,Object> body) {
        try {
            byte[] bytes = json.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true).writeValueAsBytes(body);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) { throw new BizException("填报内容格式不正确"); }
    }
}
