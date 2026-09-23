package com.zhyq.park.marketing.wh;

import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseOnboardingMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktWarehouseOnboardingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhOnboardingControllerTest {
    @Mock MktWarehouseMapper warehouses;
    @Mock MktWarehouseOnboardingMapper steps;
    @Mock MktWarehouseOnboardingService onboarding;
    @Mock MktAuditService audit;
    @Mock com.zhyq.park.marketing.service.MktWarehouseFileService files;

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @BeforeAll static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MktWarehouse.class);
    }

    @Test void saveApplyDerivesWarehouseFromTokenAndIgnoresBodyId() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("wh:11", null, List.of()));
        MktWarehouse current = new MktWarehouse(); current.setId(11L); current.setJoinStatus(1); current.setProjectId(7L);
        MktWarehouse body = new MktWarehouse(); body.setId(99L); body.setName("更新后"); body.setRegion("杭州"); body.setPhone("13800000000"); body.setContact("联系人"); body.setAddress("杭州园区");
        when(warehouses.selectById(11L)).thenReturn(current);
        when(warehouses.update(any(), any())).thenReturn(1);
        WhOnboardingController controller = new WhOnboardingController(warehouses, steps, onboarding, audit, files, new com.fasterxml.jackson.databind.ObjectMapper());
        MktWarehouse result = controller.saveApply(body).getData();
        assertThat(result.getId()).isEqualTo(11L);
        assertThat(result.getName()).isEqualTo("更新后");
        verify(warehouses, never()).selectById(99L);
        verify(warehouses).update(any(), any());
    }
    @Test void approvedWarehouseCannotReplaceProfileEvenWithForgedDraftState() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("wh:11", null, List.of()));
        MktWarehouse current = new MktWarehouse(); current.setId(11L); current.setJoinStatus(5);
        MktWarehouse body = new MktWarehouse(); body.setId(99L); body.setJoinStatus(1); body.setErpStatus(2);
        body.setName("替换主体"); body.setContact("联系人"); body.setPhone("13800000000"); body.setRegion("杭州"); body.setAddress("杭州园区");
        when(warehouses.selectById(11L)).thenReturn(current);
        WhOnboardingController controller = new WhOnboardingController(warehouses, steps, onboarding, audit, files, new com.fasterxml.jackson.databind.ObjectMapper());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.saveApply(body)).isInstanceOf(com.zhyq.park.common.exception.BizException.class);
        verifyNoInteractions(onboarding, audit);
        verify(warehouses, never()).selectById(99L);
    }
}
