package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.marketing.entity.MktNotice;
import com.zhyq.park.marketing.mapper.MktNoticeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 云仓端通知:写/读都按 warehouse_id 隔离。
 * <p>推送失败不能把业务事务一起回滚(与 {@link MktAuditService} 同样只打日志),否则结算确认会因为通知写不进去而失败。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MktNoticeService {

    private final MktNoticeMapper noticeMapper;

    /** 给某个云仓推一条通知。调用方在业务事务里调用,失败只记日志。 */
    public void push(Long warehouseId, String type, String title, String content, String bizType, Long bizId) {
        if (warehouseId == null) return;
        try {
            MktNotice n = new MktNotice();
            n.setWarehouseId(warehouseId);
            n.setType(type);
            n.setTitle(title);
            n.setContent(content);
            n.setBizType(bizType);
            n.setBizId(bizId);
            noticeMapper.insert(n);
        } catch (Exception e) {
            log.warn("[mkt] 写通知失败 wh={} type={}: {}", warehouseId, type, e.getMessage());
        }
    }

    /** 某云仓通知分页(强 warehouse 条件,按未读在前、id 倒序)。 */
    public IPage<MktNotice> page(Long warehouseId, int pageNo, int pageSize) {
        return noticeMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<MktNotice>()
                        .eq(MktNotice::getWarehouseId, warehouseId)
                        .orderByDesc(MktNotice::getId));
    }

    /** 未读数。 */
    public long unread(Long warehouseId) {
        return noticeMapper.selectCount(new LambdaQueryWrapper<MktNotice>()
                .eq(MktNotice::getWarehouseId, warehouseId).isNull(MktNotice::getReadAt));
    }

    /** 标记已读:条件更新带 warehouse_id,越权标记返回 0 行。 */
    public boolean markRead(Long id, Long warehouseId) {
        int n = noticeMapper.update(null, new LambdaUpdateWrapper<MktNotice>()
                .eq(MktNotice::getId, id)
                .eq(MktNotice::getWarehouseId, warehouseId)
                .isNull(MktNotice::getReadAt)
                .set(MktNotice::getReadAt, LocalDateTime.now()));
        return n > 0;
    }
}
