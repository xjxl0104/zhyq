package com.zhyq.park.am.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.am.entity.AmAsset;
import com.zhyq.park.am.entity.AmAssetLog;
import com.zhyq.park.am.mapper.AmAssetLogMapper;
import com.zhyq.park.am.mapper.AmAssetMapper;
import com.zhyq.park.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 资产签出/签入状态机 + 盘点。
 * 状态流转一律用「条件 UPDATE 抢状态」保证并发下只有一个请求生效(同 ContractService)。
 * 每次流转写一条 am_asset_log。
 * 财务边界:仅读写 am_asset / am_asset_log,绝不触碰 finance / price 不入账。
 */
@Service
@RequiredArgsConstructor
public class AmAssetService {

    private final AmAssetMapper assetMapper;
    private final AmAssetLogMapper logMapper;

    // 资产状态
    public static final int ST_IN_STOCK = 1;   // 在库
    public static final int ST_CHECKED_OUT = 2; // 领用中
    public static final int ST_REPAIRING = 3;  // 维修
    public static final int ST_SCRAPPED = 4;   // 报废

    // 占位操作人(#7 鉴权后替换为真实登录人)
    private static final String OPERATOR = "system";

    /**
     * 签出:在库(1)→领用中(2),设 holder。校验当前在库。
     */
    @Transactional(rollbackFor = Exception.class)
    public void checkout(Long id, String holder) {
        if (holder == null || holder.isBlank()) {
            throw new BizException("签出必须指定持有人");
        }
        AmAsset asset = requireAsset(id);
        int updated = assetMapper.update(null, new LambdaUpdateWrapper<AmAsset>()
                .eq(AmAsset::getId, id)
                .eq(AmAsset::getStatus, ST_IN_STOCK)
                .set(AmAsset::getStatus, ST_CHECKED_OUT)
                .set(AmAsset::getHolder, holder));
        if (updated == 0) {
            throw new BizException("仅在库资产可签出");
        }
        writeLog(id, "CHECKOUT", holder, asset.getSpaceId(), "签出给 " + holder);
    }

    /**
     * 签入:领用中(2)→在库(1),清 holder。
     */
    @Transactional(rollbackFor = Exception.class)
    public void checkin(Long id) {
        AmAsset asset = requireAsset(id);
        String prevHolder = asset.getHolder();
        int updated = assetMapper.update(null, new LambdaUpdateWrapper<AmAsset>()
                .eq(AmAsset::getId, id)
                .eq(AmAsset::getStatus, ST_CHECKED_OUT)
                .set(AmAsset::getStatus, ST_IN_STOCK)
                .set(AmAsset::getHolder, null));
        if (updated == 0) {
            throw new BizException("仅领用中资产可签入");
        }
        writeLog(id, "CHECKIN", prevHolder, asset.getSpaceId(), "签入归还");
    }

    /**
     * 报废:任意非报废状态→报废(4,终态)。
     */
    @Transactional(rollbackFor = Exception.class)
    public void scrap(Long id) {
        AmAsset asset = requireAsset(id);
        int updated = assetMapper.update(null, new LambdaUpdateWrapper<AmAsset>()
                .eq(AmAsset::getId, id)
                .ne(AmAsset::getStatus, ST_SCRAPPED)
                .set(AmAsset::getStatus, ST_SCRAPPED));
        if (updated == 0) {
            throw new BizException("资产已报废");
        }
        writeLog(id, "SCRAP", asset.getHolder(), asset.getSpaceId(), "报废");
    }

    /**
     * 送修:在库(1)→维修(3)。
     */
    @Transactional(rollbackFor = Exception.class)
    public void repair(Long id) {
        AmAsset asset = requireAsset(id);
        int updated = assetMapper.update(null, new LambdaUpdateWrapper<AmAsset>()
                .eq(AmAsset::getId, id)
                .eq(AmAsset::getStatus, ST_IN_STOCK)
                .set(AmAsset::getStatus, ST_REPAIRING));
        if (updated == 0) {
            throw new BizException("仅在库资产可送修");
        }
        writeLog(id, "REPAIR", asset.getHolder(), asset.getSpaceId(), "送修");
    }

    /**
     * 维修完成:维修(3)→在库(1)。
     */
    @Transactional(rollbackFor = Exception.class)
    public void repairDone(Long id) {
        AmAsset asset = requireAsset(id);
        int updated = assetMapper.update(null, new LambdaUpdateWrapper<AmAsset>()
                .eq(AmAsset::getId, id)
                .eq(AmAsset::getStatus, ST_REPAIRING)
                .set(AmAsset::getStatus, ST_IN_STOCK));
        if (updated == 0) {
            throw new BizException("仅维修中资产可标记维修完成");
        }
        writeLog(id, "REPAIR", asset.getHolder(), asset.getSpaceId(), "维修完成");
    }

    /**
     * 盘点:更新 space_id(移动到的空间),写 INVENTORY 流水,不改 status。
     */
    @Transactional(rollbackFor = Exception.class)
    public void inventory(Long id, Long spaceId, String remark) {
        requireAsset(id);
        int updated = assetMapper.update(null, new LambdaUpdateWrapper<AmAsset>()
                .eq(AmAsset::getId, id)
                .set(AmAsset::getSpaceId, spaceId));
        if (updated == 0) {
            throw new BizException("盘点失败:资产不存在");
        }
        writeLog(id, "INVENTORY", null, spaceId,
                remark == null || remark.isBlank() ? "盘点" : remark);
    }

    private AmAsset requireAsset(Long id) {
        AmAsset asset = assetMapper.selectById(id);
        if (asset == null) {
            throw new BizException("资产不存在");
        }
        return asset;
    }

    private void writeLog(Long assetId, String action, String holder, Long spaceId, String remark) {
        AmAssetLog log = new AmAssetLog();
        log.setAssetId(assetId);
        log.setAction(action);
        log.setOperator(OPERATOR);
        log.setHolder(holder);
        log.setSpaceId(spaceId);
        log.setRemark(remark);
        log.setActTime(LocalDateTime.now());
        logMapper.insert(log);
    }
}
