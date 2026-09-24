package com.zhyq.park.marketing.engine;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.marketing.entity.MktPosition;
import com.zhyq.park.marketing.entity.MktPositionOverride;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPositionMapper;
import com.zhyq.park.marketing.mapper.MktPositionOverrideMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LadderResolverTest {

    @Mock BizSettings bizSettings;
    @Mock MktPositionMapper positionMapper;
    @Mock MktPositionOverrideMapper overrideMapper;
    @Mock MktPromoterMapper promoterMapper;

    LadderResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new LadderResolver(bizSettings, positionMapper, overrideMapper, promoterMapper);
        lenient().when(positionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                pos("P1", 1, 50, 40), pos("P2", 2, 70, 60), pos("P3", 3, 85, 80), pos("P4", 4, 100, 100)));
        lenient().when(bizSettings.getBoolean(eq("marketing"), eq("override_enabled"), any(Boolean.class))).thenReturn(true);
    }

    @Test
    void twoLevelLadderCollapsesToP1AndTop() {
        when(bizSettings.getInt("marketing", "ladder_depth", 2)).thenReturn(2);

        Map<String, Integer> ladder = resolver.resolve(null);

        assertThat(ladder).containsEntry("P1", 60).containsEntry("P2", 100).containsEntry("P4", 100);
    }

    @Test
    void fourLevelLadderUsesConfiguredShares() {
        when(bizSettings.getInt("marketing", "ladder_depth", 2)).thenReturn(4);

        Map<String, Integer> ladder = resolver.resolve(null);

        assertThat(ladder).containsExactly(Map.entry("P1", 50), Map.entry("P2", 70),
                Map.entry("P3", 85), Map.entry("P4", 100));
    }

    @Test
    void overrideFromNearestDiamondLowersShareButNotBelowFloor() {
        when(bizSettings.getInt("marketing", "ladder_depth", 2)).thenReturn(4);
        MktPromoter seller = promoter(1L, "P1", 2L);
        MktPromoter mid = promoter(2L, "P2", 3L);
        MktPromoter diamond = promoter(3L, "P4", null);
        when(promoterMapper.selectById(2L)).thenReturn(mid);
        when(promoterMapper.selectById(3L)).thenReturn(diamond);
        when(overrideMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                override(3L, "P1", 45),   // 合法下调
                override(3L, "P2", 10),   // 低于下限 60 → 钳到 60
                override(3L, "P4", 50))); // 顶格不可改
        Map<String, Integer> ladder = resolver.resolve(seller);

        assertThat(ladder).containsEntry("P1", 45).containsEntry("P2", 60).containsEntry("P4", 100);
    }

    @Test
    void chainWalksParentsUntilRoot() {
        when(positionMapper.selectCount(null)).thenReturn(4L);
        MktPromoter seller = promoter(1L, "P1", 2L);
        MktPromoter parent = promoter(2L, "P3", null);
        parent.setIsInternal(1);
        when(promoterMapper.selectById(2L)).thenReturn(parent);

        List<ChainNode> chain = resolver.chainOf(seller);

        assertThat(chain).extracting(ChainNode::promoterId).containsExactly(1L, 2L);
        assertThat(chain.get(1).internal()).isTrue();
    }

    private static MktPosition pos(String code, int sort, int share, int min) {
        MktPosition p = new MktPosition();
        p.setCode(code); p.setSort(sort); p.setSharePct(share); p.setShareMinPct(min); p.setStatus(1);
        return p;
    }

    private static MktPromoter promoter(Long id, String code, Long parentId) {
        MktPromoter p = new MktPromoter();
        p.setId(id); p.setPositionCode(code); p.setParentId(parentId); p.setStatus(1); p.setIsInternal(0);
        return p;
    }

    private static MktPositionOverride override(Long owner, String code, int share) {
        MktPositionOverride o = new MktPositionOverride();
        o.setOwnerPromoterId(owner); o.setPositionCode(code); o.setSharePct(share);
        return o;
    }
}
