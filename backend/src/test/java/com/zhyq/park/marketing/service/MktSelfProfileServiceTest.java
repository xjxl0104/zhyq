package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktCredential;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseContact;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.system.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MktSelfProfileServiceTest {
    @Mock MktPromoterMapper promoters;
    @Mock MktWarehouseMapper warehouses;
    @Mock MktWarehouseContactMapper contacts;
    @Mock MktCredentialMapper credentials;
    @Mock MktPromoterService promoterService;
    @Mock MktAuditService audit;
    private MktSelfProfileService service;

    @BeforeAll static void metadata() {
        for (Class<?> type : new Class<?>[]{MktPromoter.class, MktWarehouse.class, MktWarehouseContact.class, MktCredential.class})
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), type);
    }
    @BeforeEach void setup() { service = new MktSelfProfileService(promoters, warehouses, contacts, credentials, promoterService, audit); }

    @Test void missingProfileIsReadableAndCanBeSavedWithoutSubmittingOrChangingState() {
        MktWarehouse warehouse = warehouse(2); warehouse.setName("待完善云仓");
        when(warehouses.selectById(41L)).thenReturn(warehouse);
        when(credentials.selectOne(any())).thenReturn(credential());
        Map<String, Object> empty = service.warehouseProfile(41L);
        assertThat(empty).containsEntry("warehouseName", null).containsEntry("phone", null)
                .containsEntry("profileComplete", false).containsEntry("phoneEditable", true);
        when(warehouses.selectOne(any())).thenReturn(warehouse);
        when(warehouses.update(isNull(), any())).thenAnswer(invocation -> {
            LambdaUpdateWrapper<MktWarehouse> update = invocation.getArgument(1); update.getSqlSegment();
            assertThat(update.getSqlSet()).doesNotContain("join_status", "erp_status", "contact_openid", "project_id");
            assertThat(update.getParamNameValuePairs().values()).contains(41L).doesNotContain(99L, "wh:99");
            return 1;
        });
        Map<String, Object> saved = service.updateWarehouse(41L, Map.of("name", "李四", "warehouseId", "99", "joinStatus", "5"));
        assertThat(saved).containsEntry("name", "李四").containsEntry("joinStatus", 2).containsEntry("profileComplete", false);
        verify(credentials, never()).update(any(), any());
    }

    @Test void delayedWarehousePhoneIsMarkedUnverifiedAndOnlyUpdatesItsOwnProfile() {
        MktWarehouse warehouse = warehouse(2);
        when(warehouses.selectOne(any())).thenReturn(warehouse);
        when(credentials.selectOne(any())).thenReturn(credential());
        when(credentials.update(isNull(), any())).thenAnswer(invocation -> {
            LambdaUpdateWrapper<MktCredential> update = invocation.getArgument(1); update.getSqlSegment();
            assertThat(update.getSqlSet()).contains("registration_phone").doesNotContain("identity_id");
            assertThat(update.getParamNameValuePairs().values()).contains("wh", 41L, "13800138000");
            return 1;
        });
        when(warehouses.update(isNull(), any())).thenReturn(1);
        Map<String, Object> saved = service.updateWarehouse(41L, Map.of("warehouseName", "新云仓", "name", "李四", "phone", "13800138000"));
        assertThat(saved).containsEntry("profileComplete", true).containsEntry("joinStatus", 2);
        verify(credentials).update(isNull(), any());
        verify(contacts, never()).insert(any(MktWarehouseContact.class));
    }

    @Test void existingPhoneOrConcurrentCredentialReservationCannotBeClaimed() {
        MktWarehouse warehouse = warehouse(2);
        when(warehouses.selectOne(any())).thenReturn(warehouse);
        when(credentials.selectOne(any())).thenReturn(credential());
        when(promoters.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.updateWarehouse(41L, Map.of("phone", "13800138000")))
                .isInstanceOf(BizException.class).hasMessageContaining("不能凭填写手机号合并身份");
        verify(warehouses, never()).update(any(), any());
        verify(credentials, never()).update(any(), any());
        when(promoters.selectCount(any())).thenReturn(0L);
        when(credentials.update(isNull(), any())).thenThrow(new DuplicateKeyException("duplicate"));
        assertThatThrownBy(() -> service.updateWarehouse(41L, Map.of("phone", "13800138000")))
                .isInstanceOf(BizException.class).hasMessageContaining("已有业务档案");
        verify(warehouses, never()).update(any(), any());
    }

    @Test void bothWechatBindingsAndApprovedWarehouseProfilesStayLocked() {
        MktWarehouse warehouse = warehouse(2); warehouse.setContactOpenid("wx-owner");
        when(warehouses.selectOne(any())).thenReturn(warehouse);
        assertThatThrownBy(() -> service.updateWarehouse(41L, Map.of("phone", "13800138000"))).hasMessageContaining("微信绑定");
        warehouse.setContactOpenid(null);
        when(contacts.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.updateWarehouse(41L, Map.of("phone", "13800138000"))).hasMessageContaining("微信绑定");
        warehouse.setJoinStatus(5);
        assertThatThrownBy(() -> service.updateWarehouse(41L, Map.of("warehouseName", "替换主体"))).hasMessageContaining("资质已审核");
        verify(warehouses, never()).update(any(), any());
        verifyNoInteractions(credentials);
    }

    @Test void partnerCanFillMissingPhoneButCannotReplaceWechatBinding() {
        MktPromoter promoter = new MktPromoter(); promoter.setId(31L); promoter.setStatus(1); promoter.setPhone("p:012345678901234567");
        when(promoters.selectForUpdate(31L)).thenReturn(promoter);
        when(credentials.selectOne(any())).thenReturn(credential());
        when(credentials.update(isNull(), any())).thenReturn(1);
        when(promoters.update(isNull(), any())).thenAnswer(invocation -> {
            LambdaUpdateWrapper<MktPromoter> update = invocation.getArgument(1); update.getSqlSegment();
            assertThat(update.getSqlSet()).doesNotContain("openid", "parent_id", "position_code", "status");
            assertThat(update.getParamNameValuePairs().values()).contains(31L, "13800138000", "小明");
            return 1;
        });
        service.updatePromoter(31L, Map.of("name", "小明", "phone", "13800138000", "parentId", "99"));
        promoter.setOpenid("wx-bound");
        assertThatThrownBy(() -> service.updatePromoter(31L, Map.of("phone", "13800138000"))).hasMessageContaining("微信绑定");
        verify(promoters, times(1)).update(isNull(), any());
    }

    @Test void placeholderPhoneIsUniqueNonMobileAndNeverSerializedAsContactData() throws Exception {
        MktPromoterService registration = new MktPromoterService(promoters, mock(SysUserMapper.class), audit, mock(JdbcTemplate.class));
        when(promoters.insert(any(MktPromoter.class))).thenAnswer(invocation -> { ((MktPromoter) invocation.getArgument(0)).setId(31L); return 1; });
        MktPromoter first = registration.registerWithoutPhone(new MktPromoter(), null);
        MktPromoter second = registration.registerWithoutPhone(new MktPromoter(), null);
        assertThat(first.getPhone()).startsWith("p:").hasSize(20).isNotEqualTo(second.getPhone());
        assertThat(MktSelfProfileService.isMobilePhone(first.getPhone())).isFalse();
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        assertThat(json.readTree(json.writeValueAsString(first)).get("phone").isNull()).isTrue();
        assertThat(first.getIsInternal()).isZero();
    }

    @Test void placeholderWarehouseNameCannotPassQualificationValidation() {
        MktWarehouse warehouse = warehouse(2); warehouse.setName("待完善云仓"); warehouse.setContact("李四");
        warehouse.setPhone("13800138000"); warehouse.setRegion("杭州"); warehouse.setAddress("园区");
        assertThatThrownBy(() -> MktWarehouseOnboardingService.validateProfile(warehouse)).hasMessageContaining("真实云仓名称");
    }

    private static MktWarehouse warehouse(int state) {
        MktWarehouse warehouse = new MktWarehouse(); warehouse.setId(41L); warehouse.setJoinStatus(state); return warehouse;
    }
    private static MktCredential credential() {
        MktCredential credential = new MktCredential(); credential.setId(1L); credential.setStatus(1); return credential;
    }
}
