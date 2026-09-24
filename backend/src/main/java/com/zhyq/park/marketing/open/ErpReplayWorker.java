package com.zhyq.park.marketing.open;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.marketing.entity.MktErpEventInbox;
import com.zhyq.park.marketing.entity.MktWarehouseErp;
import com.zhyq.park.marketing.mapper.MktErpEventInboxMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseErpMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ERP 事件重放:把 {@code RECEIVED} 状态的事件真正重新投递进 {@link ErpOrderIngestService}。
 * <p>{@code ErpReliabilityService.retryDue} 只把 RETRY 翻成 RECEIVED,没人消费;本类补上消费端。</p>
 * <p>幂等:重放走 t{@code ingest} 同一条路径,按事件业务键(warehouse+app_id+event_id)查重,
 * 已 PROCESSED 直接返回 —— 重复投递不会产生第二条订单/佣金。这是本功能唯一要守住的失败模式。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpReplayWorker {

    /** 单次重放上限,避免共用服务器上长事务。 */
    static final int REPLAY_BATCH = 50;
    private static final int MAX_ATTEMPTS = 5;

    private final MktErpEventInboxMapper inboxMapper;
    private final MktWarehouseErpMapper erpMapper;
    private final ErpOrderIngestService ingestService;
    private final ObjectMapper objectMapper;

    /** @return 本次处理的条数 */
    public int replayDue() {
        List<MktErpEventInbox> rows = inboxMapper.selectList(new LambdaQueryWrapper<MktErpEventInbox>()
                .eq(MktErpEventInbox::getStatus, "RECEIVED").isNull(MktErpEventInbox::getNextRetryAt)
                .orderByAsc(MktErpEventInbox::getId).last("limit " + REPLAY_BATCH));
        int done = 0;
        for (MktErpEventInbox row : rows) {
            try {
                if (replayOne(row)) done++;
            } catch (Exception e) {
                log.error("[erp] replay inbox {} failed", row.getId(), e);
            }
        }
        return done;
    }

    /** @return 是否真的尝试了重放(false = 找不到凭证/无原文,保持 RECEIVED 等人工处理) */
    private boolean replayOne(MktErpEventInbox row) throws Exception {
        MktWarehouseErp cred = erpMapper.selectOne(new LambdaQueryWrapper<MktWarehouseErp>()
                .eq(MktWarehouseErp::getWarehouseId, row.getWarehouseId())
                .eq(MktWarehouseErp::getAppId, row.getAppId()).last("limit 1"));
        if (cred == null) return false;   // 凭证已重置/停用
        if (row.getPayloadJson() == null) return false;
        JsonNode ev = objectMapper.readTree(row.getPayloadJson());
        // 重放前置 PROCESSING;ingest 按业务键查重,已处理的不会重复入账
        inboxMapper.update(null, new LambdaUpdateWrapper<MktErpEventInbox>()
                .eq(MktErpEventInbox::getId, row.getId()).set(MktErpEventInbox::getStatus, "PROCESSING"));
        ErpOrderIngestService.IngestResult r;
        try {
            r = ingestService.ingest(cred, ev, row.getPayloadDigest());
        } catch (Exception e) {
            markRetry(row, e.getMessage());
            return true;
        }
        if (r != null && r.ok()) {
            // ingest 内部已把 inbox 置 PROCESSED;这里只兜底(未映射等分支也视作处理完成)
            inboxMapper.update(null, new LambdaUpdateWrapper<MktErpEventInbox>()
                    .eq(MktErpEventInbox::getId, row.getId())
                    .set(MktErpEventInbox::getStatus, "PROCESSED")
                    .set(MktErpEventInbox::getProcessedAt, LocalDateTime.now())
                    .set(MktErpEventInbox::getLastError, r.attributed() ? null : "UNMAPPED"));
        } else {
            markRetry(row, r == null ? "重放失败" : r.msg());
        }
        return true;
    }

    private void markRetry(MktErpEventInbox row, String error) {
        int attempts = (row.getAttempts() == null ? 0 : row.getAttempts()) + 1;
        LambdaUpdateWrapper<MktErpEventInbox> w = new LambdaUpdateWrapper<MktErpEventInbox>()
                .eq(MktErpEventInbox::getId, row.getId())
                .set(MktErpEventInbox::getAttempts, attempts)
                .set(MktErpEventInbox::getLastError, error);
        if (attempts >= MAX_ATTEMPTS) {
            w.set(MktErpEventInbox::getStatus, "DEAD").set(MktErpEventInbox::getNextRetryAt, null);
        } else {
            // 退避:5/10/20/40 分钟,回 RETRY 由 retryDue 再翻 RECEIVED
            long backoffMin = 5L << (attempts - 1);
            w.set(MktErpEventInbox::getStatus, "RETRY").set(MktErpEventInbox::getNextRetryAt, LocalDateTime.now().plusMinutes(backoffMin));
        }
        inboxMapper.update(null, w);
    }
}
