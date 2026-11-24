package com.zhyq.park.energy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.energy.entity.LeaseFeeNotice;
import com.zhyq.park.energy.entity.LeaseFeeNoticeItem;
import com.zhyq.park.energy.mapper.LeaseFeeNoticeItemMapper;
import com.zhyq.park.energy.mapper.LeaseFeeNoticeMapper;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaseFeePaymentService {
    public static final String BIZ_TYPE = "lease_fee_notice_payment";

    private final JdbcTemplate jdbc;
    private final LeaseFeeNoticeMapper notices;
    private final LeaseFeeNoticeItemMapper items;
    private final SysFileMapper files;
    private final FileStorageService storage;

    public void lockPeriod(Long periodId) {
        if (periodId == null || periodId <= 0 || jdbc.queryForList(
                "SELECT id FROM eng_lease_fee_period WHERE id=? AND deleted=0 FOR UPDATE", Long.class, periodId).isEmpty()) {
            throw new BizException(404, "租金/物业费账期不存在，请刷新后重试");
        }
    }

    private LeaseFeeNotice lockNotice(Long noticeId) {
        if (noticeId == null || noticeId <= 0) throw new BizException(400, "请选择有效的缴费通知单");
        LeaseFeeNotice before = notices.selectById(noticeId);
        if (before == null) throw new BizException(404, "通知单已不存在或已重新生成，请刷新后重试");
        lockPeriod(before.getLeasePeriodId());
        LeaseFeeNotice current = notices.lockActive(noticeId);
        if (current == null || !Objects.equals(current.getLeasePeriodId(), before.getLeasePeriodId())) {
            throw new BizException(409, "通知单已重新生成，请刷新后重新选择");
        }
        return current;
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaseFeeNotice updateStatus(Long noticeId, Integer status) {
        if (status == null || (status != 0 && status != 1)) throw new BizException(400, "缴费状态只能选择未缴或已缴");
        LeaseFeeNotice current = lockNotice(noticeId);
        notices.update(null, new LambdaUpdateWrapper<LeaseFeeNotice>()
                .eq(LeaseFeeNotice::getId, noticeId).set(LeaseFeeNotice::getPaymentStatus, status));
        current.setPaymentStatus(status);
        current.setItems(items.selectList(new LambdaQueryWrapper<LeaseFeeNoticeItem>()
                .eq(LeaseFeeNoticeItem::getNoticeId, noticeId).orderByAsc(LeaseFeeNoticeItem::getSort)));
        return current;
    }

    @Transactional(rollbackFor = Exception.class)
    public SysFile upload(MultipartFile file, Long noticeId) {
        LeaseFeeNotice current = lockNotice(noticeId);
        FileStorageService.StoredResult stored = storage.store(file);
        boolean sync = TransactionSynchronizationManager.isSynchronizationActive();
        if (sync) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) storage.deletePhysical(stored.storePath());
                }
            });
        }
        SysFile result = new SysFile();
        result.setBizType(BIZ_TYPE);
        result.setBizId(current.getId());
        result.setTenantId(current.getTenantId());
        result.setOriginalName(stored.originalName());
        result.setStorePath(stored.storePath());
        result.setUrl(stored.url());
        result.setFileSize(stored.size());
        result.setContentType(stored.contentType());
        result.setExt(stored.ext());
        try {
            files.insert(result);
        } catch (RuntimeException e) {
            if (!sync) storage.deletePhysical(stored.storePath());
            throw e;
        }
        return result;
    }

    public Map<String, List<Long>> prepareRegeneration(List<LeaseFeeNotice> oldNotices, List<LeaseFeeNotice> replacements) {
        Set<String> newKeys = replacements.stream().map(LeaseFeeCalculator::noticeKey).collect(Collectors.toSet());
        Set<Long> evidenceIds = paymentFiles(oldNotices).stream().map(SysFile::getBizId).collect(Collectors.toSet());
        Map<String, List<Long>> sources = new LinkedHashMap<>();
        for (LeaseFeeNotice old : oldNotices) {
            String key = LeaseFeeCalculator.noticeKey(old);
            if (!newKeys.contains(key) && (Integer.valueOf(1).equals(old.getPaymentStatus()) || evidenceIds.contains(old.getId()))) {
                throw new BizException(409, "租户「" + old.getTenantName() + "」已有缴费记录或附件，本次生成将移除该通知单；请先核对并处理缴费标记和附件");
            }
            if (old.getId() != null) {
                sources.computeIfAbsent(key, ignored -> new ArrayList<>()).add(old.getId());
            }
        }
        return sources;
    }

    public void moveAttachments(Map<String, List<Long>> sources, LeaseFeeNotice replacement) {
        List<Long> oldIds = sources.get(LeaseFeeCalculator.noticeKey(replacement));
        if (oldIds == null || oldIds.isEmpty()) return;
        files.update(null, new LambdaUpdateWrapper<SysFile>().eq(SysFile::getBizType, BIZ_TYPE)
                .in(SysFile::getBizId, oldIds).set(SysFile::getBizId, replacement.getId()));
    }

    public void assertCanDelete(List<LeaseFeeNotice> targets) {
        if (targets.stream().anyMatch(n -> Integer.valueOf(1).equals(n.getPaymentStatus())) || !paymentFiles(targets).isEmpty()) {
            throw new BizException(409, "通知单已有缴费标记或缴费附件，请先将标记改为未缴并处理附件后再删除");
        }
    }

    private List<SysFile> paymentFiles(List<LeaseFeeNotice> list) {
        List<Long> ids = list.stream().map(LeaseFeeNotice::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return List.of();
        return files.selectList(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getBizType, BIZ_TYPE).in(SysFile::getBizId, ids));
    }

    static LeaseFeeNotice normalizeStatus(LeaseFeeNotice n) {
        if (n.getPaymentStatus() == null) n.setPaymentStatus(0);
        return n;
    }
}
