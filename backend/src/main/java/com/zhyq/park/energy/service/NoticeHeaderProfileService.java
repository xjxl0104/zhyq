package com.zhyq.park.energy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.energy.entity.FeeNotice;
import com.zhyq.park.energy.entity.FeePeriod;
import com.zhyq.park.energy.entity.LeaseFeeNotice;
import com.zhyq.park.energy.entity.LeaseFeePeriod;
import com.zhyq.park.energy.entity.NoticeHeaderProfile;
import com.zhyq.park.energy.mapper.FeeNoticeMapper;
import com.zhyq.park.energy.mapper.FeePeriodMapper;
import com.zhyq.park.energy.mapper.LeaseFeeNoticeMapper;
import com.zhyq.park.energy.mapper.LeaseFeePeriodMapper;
import com.zhyq.park.energy.mapper.NoticeHeaderProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NoticeHeaderProfileService {

    public static final String SCOPE_LEASE = "LEASE";
    public static final String SCOPE_UTILITY = "UTILITY";
    public static final String SCOPE_DEPOSIT = "DEPOSIT";

    private final NoticeHeaderProfileMapper profileMapper;
    private final LeaseFeePeriodMapper leasePeriodMapper;
    private final LeaseFeeNoticeMapper leaseNoticeMapper;
    private final FeePeriodMapper feePeriodMapper;
    private final FeeNoticeMapper feeNoticeMapper;
    private final ObjectMapper objectMapper;

    public record SaveRequest(
            String scopeType,
            String tenantName,
            String referencePeriod,
            Long periodId,
            String parkName,
            String noticePrefix,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate issueDate,
            LocalDate dueDate,
            String contactName,
            String contactPhone,
            String paymentAccount,
            String rentPayeeName,
            String rentContactName,
            String rentContactPhone,
            String rentPaymentAccount,
            String propertyPayeeName,
            String propertyContactName,
            String propertyContactPhone,
            String propertyPaymentAccount
    ) {}

    public List<NoticeHeaderProfile> listByScope(String scopeType) {
        String scope = normalizeScope(scopeType);
        return profileMapper.selectList(new LambdaQueryWrapper<NoticeHeaderProfile>()
                .eq(NoticeHeaderProfile::getScopeType, scope)
                .orderByAsc(NoticeHeaderProfile::getTenantName));
    }

    public Map<String, NoticeHeaderProfile> mapByScope(String scopeType) {
        Map<String, NoticeHeaderProfile> map = new LinkedHashMap<>();
        for (NoticeHeaderProfile p : listByScope(scopeType)) {
            if (StringUtils.hasText(p.getTenantName())) {
                map.put(p.getTenantName().trim(), p);
            }
        }
        return map;
    }

    @Transactional(rollbackFor = Exception.class)
    public NoticeHeaderProfile save(SaveRequest req) {
        if (req == null || !StringUtils.hasText(req.tenantName())) {
            throw new BizException("请选择具体的缴费单位");
        }
        String scope = normalizeScope(req.scopeType());
        String tenant = req.tenantName().trim();
        YearMonth refYm = parseYearMonth(req.referencePeriod());

        NoticeHeaderProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<NoticeHeaderProfile>()
                .eq(NoticeHeaderProfile::getScopeType, scope)
                .eq(NoticeHeaderProfile::getTenantName, tenant)
                .last("LIMIT 1"));
        boolean isNew = (profile == null);
        if (isNew) {
            profile = new NoticeHeaderProfile();
            profile.setScopeType(scope);
            profile.setTenantName(tenant);
        }

        profile.setParkName(trimOrNull(req.parkName()));
        profile.setNoticePrefix(trimOrNull(req.noticePrefix()));
        profile.setPeriodStart(req.periodStart());
        profile.setPeriodEnd(req.periodEnd());
        profile.setIssueDate(req.issueDate());
        profile.setDueDate(req.dueDate());
        if (req.issueDate() != null) {
            profile.setIssueDay(req.issueDate().getDayOfMonth());
            if (refYm != null) {
                profile.setIssueMonthOffset((int) ChronoUnit.MONTHS.between(refYm, YearMonth.from(req.issueDate())));
            }
        } else {
            profile.setIssueDay(null);
            profile.setIssueMonthOffset(null);
        }
        if (req.dueDate() != null) {
            profile.setDueDay(req.dueDate().getDayOfMonth());
            if (refYm != null) {
                profile.setDueMonthOffset((int) ChronoUnit.MONTHS.between(refYm, YearMonth.from(req.dueDate())));
            }
        } else {
            profile.setDueDay(null);
            profile.setDueMonthOffset(null);
        }

        profile.setContactName(trimOrNull(req.contactName()));
        profile.setContactPhone(trimOrNull(req.contactPhone()));
        profile.setPaymentAccount(trimOrNull(req.paymentAccount()));
        profile.setRentPayeeName(trimOrNull(req.rentPayeeName()));
        profile.setRentContactName(trimOrNull(req.rentContactName()));
        profile.setRentContactPhone(trimOrNull(req.rentContactPhone()));
        profile.setRentPaymentAccount(trimOrNull(req.rentPaymentAccount()));
        profile.setPropertyPayeeName(trimOrNull(req.propertyPayeeName()));
        profile.setPropertyContactName(trimOrNull(req.propertyContactName()));
        profile.setPropertyContactPhone(trimOrNull(req.propertyContactPhone()));
        profile.setPropertyPaymentAccount(trimOrNull(req.propertyPaymentAccount()));

        if (isNew) {
            profileMapper.insert(profile);
        } else {
            profileMapper.updateById(profile);
        }

        // 若传入当前账期，立即同步更新该账期下已生成的对应单位通知单，所见即所得
        if (req.periodId() != null) {
            syncExistingPeriodNotices(scope, req.periodId(), tenant, profile);
        }
        return profile;
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(String scopeType, String tenantName, Long periodId) {
        if (!StringUtils.hasText(tenantName)) return;
        String scope = normalizeScope(scopeType);
        String tenant = tenantName.trim();
        profileMapper.delete(new LambdaQueryWrapper<NoticeHeaderProfile>()
                .eq(NoticeHeaderProfile::getScopeType, scope)
                .eq(NoticeHeaderProfile::getTenantName, tenant));
        // 若传入当前账期，恢复该单位通知单为通用抬头
        if (periodId != null) {
            syncExistingPeriodNotices(scope, periodId, tenant, null);
        }
    }

    /** 生成租金/物业费通知单时，应用该缴费单位的定制抬头（无定制则保持通用） */
    public void applyToLeaseNotice(LeaseFeePeriod period, LeaseFeeNotice notice, NoticeHeaderProfile profile) {
        if (period == null || notice == null || profile == null) return;
        boolean isRent = "RENT".equalsIgnoreCase(notice.getFeeCategory());
        String payee = isRent ? profile.getRentPayeeName() : profile.getPropertyPayeeName();
        String account = isRent ? profile.getRentPaymentAccount() : profile.getPropertyPaymentAccount();
        String contactName = isRent ? profile.getRentContactName() : profile.getPropertyContactName();
        String contactPhone = isRent ? profile.getRentContactPhone() : profile.getPropertyContactPhone();

        if (StringUtils.hasText(payee)) {
            notice.setPayeeName(payee);
        }
        if (StringUtils.hasText(account)) {
            notice.setPaymentAccount(account);
        }
        if (StringUtils.hasText(profile.getNoticePrefix()) && StringUtils.hasText(notice.getNoticeNo())) {
            String basePrefix = StringUtils.hasText(period.getNoticePrefix()) ? period.getNoticePrefix().trim() : "DIPARK";
            if (notice.getNoticeNo().startsWith(basePrefix + "-")) {
                notice.setNoticeNo(profile.getNoticePrefix().trim() + notice.getNoticeNo().substring(basePrefix.length()));
            }
        }

        YearMonth ym = parseYearMonth(period.getPeriod());
        LocalDate effIssue = resolveDateForPeriod(profile.getIssueDate(), profile.getIssueMonthOffset(), profile.getIssueDay(), ym);
        LocalDate effDue = resolveDateForPeriod(profile.getDueDate(), profile.getDueMonthOffset(), profile.getDueDay(), ym);
        LocalDate effStart = resolvePeriodBound(profile.getPeriodStart(), ym);
        LocalDate effEnd = resolvePeriodBound(profile.getPeriodEnd(), ym);

        Map<String, Object> ov = new LinkedHashMap<>();
        if (StringUtils.hasText(profile.getParkName())) ov.put("parkName", profile.getParkName());
        if (effStart != null) ov.put("periodStart", effStart.toString());
        if (effEnd != null) ov.put("periodEnd", effEnd.toString());
        if (effIssue != null) ov.put("issueDate", effIssue.toString());
        if (effDue != null) ov.put("dueDate", effDue.toString());
        if (StringUtils.hasText(contactName)) ov.put("contactName", contactName);
        if (StringUtils.hasText(contactPhone)) ov.put("contactPhone", contactPhone);

        notice.setHeaderOverrideJson(ov.isEmpty() ? null : toJson(ov));
    }

    /** 生成水电/垃圾费通知单时，应用该缴费单位的定制抬头（无定制则保持通用） */
    public void applyToUtilityNotice(FeePeriod period, FeeNotice notice, NoticeHeaderProfile profile) {
        if (period == null || notice == null || profile == null) return;
        if (StringUtils.hasText(profile.getNoticePrefix()) && StringUtils.hasText(notice.getNoticeNo())) {
            String basePrefix = StringUtils.hasText(period.getNoticePrefix()) ? period.getNoticePrefix().trim() : "DIPARK";
            if (notice.getNoticeNo().startsWith(basePrefix + "-")) {
                notice.setNoticeNo(profile.getNoticePrefix().trim() + notice.getNoticeNo().substring(basePrefix.length()));
            }
        }
        YearMonth ym = parseYearMonth(period.getPeriod());
        LocalDate effIssue = resolveDateForPeriod(profile.getIssueDate(), profile.getIssueMonthOffset(), profile.getIssueDay(), ym);
        LocalDate effDue = resolveDateForPeriod(profile.getDueDate(), profile.getDueMonthOffset(), profile.getDueDay(), ym);
        LocalDate effStart = resolvePeriodBound(profile.getPeriodStart(), ym);
        LocalDate effEnd = resolvePeriodBound(profile.getPeriodEnd(), ym);

        Map<String, Object> ov = new LinkedHashMap<>();
        if (StringUtils.hasText(profile.getParkName())) ov.put("parkName", profile.getParkName());
        if (effStart != null) ov.put("periodStart", effStart.toString());
        if (effEnd != null) ov.put("periodEnd", effEnd.toString());
        if (effIssue != null) ov.put("issueDate", effIssue.toString());
        if (effDue != null) ov.put("dueDate", effDue.toString());
        if (StringUtils.hasText(profile.getContactName())) ov.put("contactName", profile.getContactName());
        if (StringUtils.hasText(profile.getContactPhone())) ov.put("contactPhone", profile.getContactPhone());
        if (StringUtils.hasText(profile.getPaymentAccount())) ov.put("paymentAccount", profile.getPaymentAccount());

        notice.setHeaderOverrideJson(ov.isEmpty() ? null : toJson(ov));
    }

    private void syncExistingPeriodNotices(String scope, Long periodId, String tenant, NoticeHeaderProfile profile) {
        if (SCOPE_LEASE.equals(scope)) {
            LeaseFeePeriod p = leasePeriodMapper.selectById(periodId);
            if (p == null) return;
            List<LeaseFeeNotice> notices = leaseNoticeMapper.selectList(new LambdaQueryWrapper<LeaseFeeNotice>()
                    .eq(LeaseFeeNotice::getLeasePeriodId, periodId)
                    .eq(LeaseFeeNotice::getTenantName, tenant));
            for (LeaseFeeNotice n : notices) {
                if (profile != null) {
                    applyToLeaseNotice(p, n, profile);
                } else {
                    boolean isRent = "RENT".equalsIgnoreCase(n.getFeeCategory());
                    n.setPayeeName(isRent ? p.getRentPayeeName() : p.getPropertyPayeeName());
                    n.setPaymentAccount(isRent ? p.getRentPaymentAccount() : p.getPropertyPaymentAccount());
                    n.setHeaderOverrideJson(null);
                }
                leaseNoticeMapper.updateById(n);
            }
        } else if (SCOPE_UTILITY.equals(scope)) {
            FeePeriod p = feePeriodMapper.selectById(periodId);
            if (p == null) return;
            List<FeeNotice> notices = feeNoticeMapper.selectList(new LambdaQueryWrapper<FeeNotice>()
                    .eq(FeeNotice::getFeePeriodId, periodId)
                    .eq(FeeNotice::getTenantName, tenant));
            for (FeeNotice n : notices) {
                if (profile != null) {
                    applyToUtilityNotice(p, n, profile);
                } else {
                    n.setHeaderOverrideJson(null);
                }
                feeNoticeMapper.updateById(n);
            }
        }
    }

    /**
     * 按账期解析定制日期：
     * - 同月直接返回保存的精确日期；
     * - 跨月按相对月偏移 + 日号自动顺延（例如 2026-11 定制为 15 日，2026-12 自动为 2026-12-15）。
     */
    private LocalDate resolveDateForPeriod(LocalDate savedDate, Integer monthOffset, Integer dayOfMonth, YearMonth targetYm) {
        if (savedDate == null && dayOfMonth == null) return null;
        if (targetYm == null) return savedDate;
        if (savedDate != null && monthOffset == null) {
            if (YearMonth.from(savedDate).equals(targetYm)) return savedDate;
            int d = Math.min(savedDate.getDayOfMonth(), targetYm.lengthOfMonth());
            return targetYm.atDay(d);
        }
        int offset = monthOffset != null ? monthOffset : 0;
        int day = dayOfMonth != null ? dayOfMonth : savedDate.getDayOfMonth();
        YearMonth shifted = targetYm.plusMonths(offset);
        return shifted.atDay(Math.min(Math.max(day, 1), shifted.lengthOfMonth()));
    }

    private LocalDate resolvePeriodBound(LocalDate savedDate, YearMonth targetYm) {
        if (savedDate == null || targetYm == null) return null;
        return YearMonth.from(savedDate).equals(targetYm) ? savedDate : null;
    }

    private YearMonth parseYearMonth(String period) {
        if (!StringUtils.hasText(period)) return null;
        try {
            return YearMonth.parse(period.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeScope(String scopeType) {
        String s = scopeType == null ? "" : scopeType.trim().toUpperCase();
        if (SCOPE_LEASE.equals(s) || SCOPE_UTILITY.equals(s) || SCOPE_DEPOSIT.equals(s)) return s;
        throw new BizException("不支持的通知单抬头范围: " + scopeType);
    }

    private String trimOrNull(String v) {
        return StringUtils.hasText(v) ? v.trim() : null;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return null;
        }
    }
}
