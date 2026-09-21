package com.zhyq.park.marketing.engine;

import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPositionMapper;
import com.zhyq.park.marketing.mapper.MktPositionOverrideMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LadderResolverChainTest {

    @Mock BizSettings settings;
    @Mock MktPositionMapper positionMapper;
    @Mock MktPositionOverrideMapper overrideMapper;
    @Mock MktPromoterMapper promoterMapper;

    @Test
    void walksSixIntermediateLevelsToTopLevelParent() {
        LadderResolver resolver = new LadderResolver(settings, positionMapper, overrideMapper, promoterMapper);
        MktPromoter seller = promoter(1L, "P1", 2L);
        for (long id = 2; id <= 7; id++) {
            MktPromoter parent = promoter(id, "P1", id == 7 ? 8L : id + 1);
            when(promoterMapper.selectById(id)).thenReturn(parent);
        }
        MktPromoter top = promoter(8L, "P4", null);
        when(promoterMapper.selectById(8L)).thenReturn(top);

        List<ChainNode> chain = resolver.chainOf(seller);

        assertThat(chain).hasSize(8);
        assertThat(chain).extracting(ChainNode::promoterId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        assertThat(chain.get(7).positionCode()).isEqualTo("P4");
    }

    private static MktPromoter promoter(Long id, String code, Long parentId) {
        MktPromoter p = new MktPromoter();
        p.setId(id); p.setPositionCode(code); p.setParentId(parentId); p.setStatus(1); p.setIsInternal(0);
        return p;
    }
}
