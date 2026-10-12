package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktCompetitionProtection;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktCompetitionProtectionMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MktCompetitionProtectionServiceTest {
    MktCompetitionProtectionMapper entries;
    MktWarehouseMapper warehouses;
    MktAuditService audit;
    FieldEncryptionService encryption;
    MktCompetitionProtectionService service;
    @BeforeAll static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MktCompetitionProtection.class);
    }
    @BeforeEach void setup() {
        entries = mock(MktCompetitionProtectionMapper.class);
        warehouses = mock(MktWarehouseMapper.class);
        audit = mock(MktAuditService.class);
        encryption = new FieldEncryptionService(Base64.getEncoder().encodeToString(new byte[32]));
        service = new MktCompetitionProtectionService(entries, warehouses, encryption, audit);
        MktWarehouse warehouse = new MktWarehouse(); warehouse.setId(7L); warehouse.setTenantId(2L); warehouse.setJoinStatus(5);
        lenient().when(warehouses.selectById(7L)).thenReturn(warehouse);
        lenient().when(warehouses.selectForUpdate(7L)).thenReturn(warehouse);
    }
    @Test void normalizesWidthSpaceAndCaseWithoutFuzzyMatching() {
        assertThat(MktCompetitionProtectionService.normalize(" ＡＢＣ　品牌 ")).isEqualTo("abc品牌");
        assertThat(MktCompetitionProtectionService.normalize("ABC品牌有限公司")).isNotEqualTo("abc品牌");
    }
    @Test void storesCiphertextAndKeyedFingerprintAndAuditHasNoName() {
        when(entries.selectCount(any())).thenReturn(0L);
        service.add(7L, "秘密品牌");
        var captor = ArgumentCaptor.forClass(MktCompetitionProtection.class);
        verify(entries).insert(captor.capture());
        var row = captor.getValue();
        assertThat(row.getWarehouseId()).isEqualTo(7L);
        assertThat(row.getTenantId()).isEqualTo(2L);
        assertThat(row.getNameEnc()).doesNotContain("秘密品牌");
        assertThat(encryption.decrypt(row.getNameEnc())).isEqualTo("秘密品牌");
        assertThat(row.getNameFingerprint()).isEqualTo(encryption.businessNameFingerprint("秘密品牌"));
        verify(audit).log(eq("protection.add"), anyString(), isNull(), eq("新增保护名称（内容保密）"));
    }
    @Test void matchBlocksWithoutRevealingOwner() {
        when(entries.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.assertUnprotected(2L, "秘密品牌"))
                .isInstanceOf(BizException.class).hasMessage("该客户暂不可进入营销推荐流程，请联系园区运营核验");
    }
    @Test void deletionAlwaysScopesOwnerAndTenant() {
        when(entries.delete(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> wrapper = invocation.getArgument(0);
            assertThat(wrapper.getSqlSegment()).contains("warehouse_id", "tenant_id", "id");
            return 0;
        });
        assertThatThrownBy(() -> service.remove(7L, 999L)).isInstanceOf(BizException.class);
        verifyNoInteractions(audit);
    }
    @Test void missingKeyNeverFallsBackToPlaintext() {
        service = new MktCompetitionProtectionService(entries, warehouses, new FieldEncryptionService(""), audit);
        assertThatThrownBy(() -> service.add(7L, "秘密品牌")).isInstanceOf(BizException.class);
        verify(entries, never()).insert(any(MktCompetitionProtection.class));
    }
}
