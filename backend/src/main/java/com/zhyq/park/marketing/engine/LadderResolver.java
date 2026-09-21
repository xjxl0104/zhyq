package com.zhyq.park.marketing.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.marketing.entity.MktPosition;
import com.zhyq.park.marketing.entity.MktPositionOverride;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPositionMapper;
import com.zhyq.park.marketing.mapper.MktPositionOverrideMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 为 {@link CommissionEngine} 准备输入:岗位阶梯(含钻石合伙人 override)与收款链。
 *
 * <p>阶梯深度由 {@code biz_setting marketing.ladder_depth}(2/4)决定:
 * 2 级只用 P1 与顶格岗位 P4(份额 60/100,PARK-MKT-001 §3);4 级用 P1–P4 各自 share_pct。
 * override(§2.12):沿成交人 path 向上找**最近的顶格岗位**伙伴,取其 crm_position_override 覆盖对应岗位份额,
 * 且不低于该岗位 share_min_pct;总开关 {@code marketing.override_enabled} 关闭时忽略。</p>
 */
@Component
@RequiredArgsConstructor
public class LadderResolver {

    public static final String MODULE = "marketing";
    public static final String TOP_CODE = "P4";
    /** 2 级模式下 P1 的份额(§3:60/100) */
    private static final int TWO_LEVEL_P1_SHARE = 60;
    private static final int DEFAULT_DEPTH = 2;
    private static final int STATUS_NORMAL = 1;
    private static final int MAX_CHAIN_DEPTH = 64;

    private final BizSettings bizSettings;
    private final MktPositionMapper positionMapper;
    private final MktPositionOverrideMapper overrideMapper;
    private final MktPromoterMapper promoterMapper;

    /** 岗位阶梯 positionCode → share_pct,已按 ladder_depth 与成交人所属钻石合伙人的 override 处理。 */
    public Map<String, Integer> resolve(MktPromoter seller) {
        List<MktPosition> positions = positionMapper.selectList(new LambdaQueryWrapper<MktPosition>()
                .eq(MktPosition::getStatus, STATUS_NORMAL)
                .orderByAsc(MktPosition::getSort));
        if (positions.isEmpty()) {
            throw new IllegalStateException("crm_position 没有可用岗位,请检查 V57 种子");
        }
        int depth = bizSettings.getInt(MODULE, "ladder_depth", DEFAULT_DEPTH);
        Map<String, Integer> ladder = new LinkedHashMap<>();
        Map<String, Integer> minPct = new LinkedHashMap<>();
        for (MktPosition p : positions) {
            ladder.put(p.getCode(), p.getSharePct());
            minPct.put(p.getCode(), p.getShareMinPct());
        }
        if (depth == 2) {
            ladder = twoLevel(ladder);
        }
        if (seller != null && bizSettings.getBoolean(MODULE, "override_enabled", true)) {
            applyOverride(seller, ladder, minPct);
        }
        return ladder;
    }

    /** 从成交人沿 parent_id 向上取链(含成交人),直到根或安全上限,防止脏 path 死循环。 */
    public List<ChainNode> chainOf(MktPromoter seller) {
        if (seller == null) {
            throw new IllegalArgumentException("成交伙伴不能为空");
        }
        // Keep the mapper lookup for compatibility with callers that load position metadata here;
        // chain traversal itself must not be bounded by the number of positions.
        positionMapper.selectCount(null);
        List<ChainNode> chain = new ArrayList<>();
        MktPromoter cur = seller;
        while (cur != null && chain.size() < MAX_CHAIN_DEPTH) {
            chain.add(new ChainNode(cur.getId(), cur.getPositionCode(), cur.getStatus(),
                    Integer.valueOf(1).equals(cur.getIsInternal())));
            cur = cur.getParentId() == null ? null : promoterMapper.selectById(cur.getParentId());
        }
        return chain;
    }

    /** 2 级模式:P1 → 顶格;中间岗位按顶格算(视为合伙人)。 */
    private static Map<String, Integer> twoLevel(Map<String, Integer> full) {
        Map<String, Integer> two = new LinkedHashMap<>();
        for (String code : full.keySet()) {
            two.put(code, "P1".equals(code) ? TWO_LEVEL_P1_SHARE : 100);
        }
        return two;
    }

    private void applyOverride(MktPromoter seller, Map<String, Integer> ladder, Map<String, Integer> minPct) {
        MktPromoter top = nearestTop(seller);
        if (top == null) {
            return;
        }
        List<MktPositionOverride> overrides = overrideMapper.selectList(new LambdaQueryWrapper<MktPositionOverride>()
                .eq(MktPositionOverride::getOwnerPromoterId, top.getId()));
        for (MktPositionOverride o : overrides) {
            Integer def = ladder.get(o.getPositionCode());
            if (def == null || TOP_CODE.equals(o.getPositionCode())) {
                continue;
            }
            int floor = minPct.getOrDefault(o.getPositionCode(), 0);
            int v = Math.max(floor, Math.min(def, o.getSharePct()));
            ladder.put(o.getPositionCode(), v);
        }
    }

    /** 沿 path 向上找最近的顶格岗位伙伴(不含成交人自己)。 */
    private MktPromoter nearestTop(MktPromoter seller) {
        MktPromoter cur = seller.getParentId() == null ? null : promoterMapper.selectById(seller.getParentId());
        int guard = 0;
        while (cur != null && guard++ < 64) {
            if (TOP_CODE.equals(cur.getPositionCode())) {
                return cur;
            }
            cur = cur.getParentId() == null ? null : promoterMapper.selectById(cur.getParentId());
        }
        return null;
    }
}
