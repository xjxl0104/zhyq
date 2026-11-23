package com.zhyq.park.energy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.energy.entity.FeeNotice;
import com.zhyq.park.energy.entity.FeeNoticeItem;
import com.zhyq.park.energy.mapper.FeeNoticeItemMapper;
import com.zhyq.park.energy.mapper.FeeNoticeMapper;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Operational payment marks and evidence, independent of bill balances and accounting receipts. */
@Service
@RequiredArgsConstructor
public class FeeNoticePaymentService {
    public static final String BIZ_TYPE = "fee_notice_payment";
    private final JdbcTemplate jdbc;
    private final FeeNoticeMapper notices;
    private final FeeNoticeItemMapper items;
    private final SysFileMapper files;
    private final FileStorageService storage;

    /** Call inside the enclosing write transaction; generation, deletion and uploads use the same lock. */
    public void lockPeriod(Long periodId) {
        if (periodId == null || periodId <= 0 || jdbc.queryForList(
                "SELECT id FROM eng_fee_period WHERE id=? AND deleted=0 FOR UPDATE", Long.class, periodId).isEmpty())
            throw new BizException(404, "缴费账期不存在，请刷新后重试");
    }

    private FeeNotice lockNotice(Long noticeId) {
        if (noticeId == null || noticeId <= 0) throw new BizException(400, "请选择有效的缴费通知单");
        FeeNotice before = notices.selectById(noticeId);
        if (before == null) throw new BizException(404, "通知单已不存在或已重新生成，请刷新后重试");
        lockPeriod(before.getFeePeriodId());
        // A locking current read is required: the initial read may precede a concurrent regeneration.
        FeeNotice current = notices.lockActive(noticeId);
        if (current == null || !Objects.equals(current.getFeePeriodId(), before.getFeePeriodId()))
            throw new BizException(409, "通知单已重新生成，请刷新后重新选择");
        return current;
    }

    @Transactional(rollbackFor = Exception.class)
    public FeeNotice updateStatus(Long noticeId, Integer status) {
        if (status == null || (status != 0 && status != 1)) throw new BizException(400, "缴费状态只能选择未缴或已缴");
        FeeNotice current = lockNotice(noticeId);
        notices.update(null, new LambdaUpdateWrapper<FeeNotice>().eq(FeeNotice::getId, noticeId)
                .set(FeeNotice::getPaymentStatus, status));
        current.setPaymentStatus(status);
        current.setItems(items.selectList(new LambdaQueryWrapper<FeeNoticeItem>()
                .eq(FeeNoticeItem::getNoticeId, noticeId).orderByAsc(FeeNoticeItem::getSort)));
        return current;
    }

    @Transactional(rollbackFor = Exception.class)
    public SysFile upload(MultipartFile file, Long noticeId) {
        FeeNotice current = lockNotice(noticeId);
        FileStorageService.StoredResult stored = storage.store(file);
        boolean synchronizedTransaction = TransactionSynchronizationManager.isSynchronizationActive();
        if (synchronizedTransaction) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) storage.deletePhysical(stored.storePath());
                }
            });
        }
        SysFile result = new SysFile();
        result.setBizType(BIZ_TYPE); result.setBizId(current.getId());
        result.setTenantId(current.getTenantId());
        result.setOriginalName(stored.originalName()); result.setStorePath(stored.storePath());
        result.setUrl(stored.url()); result.setFileSize(stored.size());
        result.setContentType(stored.contentType()); result.setExt(stored.ext());
        try { files.insert(result); }
        catch (RuntimeException e) {
            if (!synchronizedTransaction) storage.deletePhysical(stored.storePath());
            throw e;
        }
        return result;
    }

    /** Validate before any old notice is deleted, then return all source ids for each new household. */
    public Map<String, List<Long>> prepareRegeneration(List<FeeNotice> oldNotices, List<FeeNotice> replacements) {
        Set<String> newKeys = replacements.stream().map(FeeNoticeCalculator::noticeKey).collect(Collectors.toSet());
        Set<Long> evidenceIds = paymentFiles(oldNotices).stream().map(SysFile::getBizId).collect(Collectors.toSet());
        Map<String, List<Long>> sources = new LinkedHashMap<>();
        for (FeeNotice old : oldNotices) {
            String key = FeeNoticeCalculator.noticeKey(old);
            if (!newKeys.contains(key) && (Integer.valueOf(1).equals(old.getPaymentStatus()) || evidenceIds.contains(old.getId())))
                throw new BizException(409, "租户“" + old.getTenantName() + "”已有缴费记录或附件，本次生成将移除该租户；请恢复该租户，或先核对并处理缴费标记和附件");
            if (old.getId() != null) sources.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(old.getId());
        }
        return sources;
    }

    public void moveAttachments(Map<String, List<Long>> sources, FeeNotice replacement) {
        List<Long> oldIds = sources.get(FeeNoticeCalculator.noticeKey(replacement));
        if (oldIds == null || oldIds.isEmpty()) return;
        files.update(null, new LambdaUpdateWrapper<SysFile>().eq(SysFile::getBizType, BIZ_TYPE)
                .in(SysFile::getBizId, oldIds).set(SysFile::getBizId, replacement.getId()));
    }

    public void assertCanDelete(List<FeeNotice> targets) {
        if (targets.stream().anyMatch(n -> Integer.valueOf(1).equals(n.getPaymentStatus())) || !paymentFiles(targets).isEmpty())
            throw new BizException(409, "通知单已有缴费标记或缴费附件，请先核对，将标记改为未缴并处理附件后再删除");
    }

    private List<SysFile> paymentFiles(List<FeeNotice> noticeList) {
        List<Long> ids = noticeList.stream().map(FeeNotice::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return List.of();
        return files.selectList(new LambdaQueryWrapper<SysFile>().eq(SysFile::getBizType, BIZ_TYPE).in(SysFile::getBizId, ids));
    }

    static FeeNotice normalizeStatus(FeeNotice notice) {
        if (notice.getPaymentStatus() == null) notice.setPaymentStatus(0);
        return notice;
    }
}
