package com.zhyq.park.marketing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.auth.BusinessRouteAccess;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktPublicSeaMeta;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktPublicSeaMetaMapper;
import com.zhyq.park.marketing.mapper.MktPublicSeaWashBatchMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.wh.WhIdentityService;
import com.zhyq.park.marketing.wh.WhWorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 内部专属公海池权限隔离、三道防撞洗数、72h首触达保活与云仓 5/10 配额锁回归测试。
 */
class MktPublicSeaServiceAndAccessTest {

    @Test
    void externalPromoterRoleMpIsStrictlyDeniedFromPublicSeaRoutes() {
        assertThat(BusinessRouteAccess.allowed("/crm/marketing/public-sea/page", "GET", Set.of("ROLE_MP"))).isFalse();
        assertThat(BusinessRouteAccess.allowed("/crm/marketing/public-sea/1/claim", "POST", Set.of("ROLE_MP"))).isFalse();
        assertThat(BusinessRouteAccess.allowed("/wh/v1/public-sea", "GET", Set.of("ROLE_MP"))).isFalse();

        assertThat(BusinessRouteAccess.allowed("/crm/marketing/public-sea/page", "GET",
                Set.of("crm:marketing:customer:query"))).isTrue();
        assertThat(BusinessRouteAccess.allowed("/crm/marketing/public-sea/wash-rows", "POST",
                Set.of("crm:marketing:public-sea:edit"))).isTrue();
    }

    @Test
    void threeGateWashBlocksPrivateLockWarehouseHmacAndAgencyDupPhones() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CustomerMapper customers = mock(CustomerMapper.class);
        MktPublicSeaMetaMapper seaMetas = mock(MktPublicSeaMetaMapper.class);
        MktPublicSeaWashBatchMapper batches = mock(MktPublicSeaWashBatchMapper.class);
        MktCompetitionProtectionService protection = mock(MktCompetitionProtectionService.class);
        MktAuditService audit = mock(MktAuditService.class);

        when(jdbc.queryForList(contains("FROM biz_project"), eq(Long.class))).thenReturn(List.of(1L));
        when(jdbc.queryForList(contains("SELECT tenant_id"), eq(Long.class), anyLong())).thenReturn(List.of(1L));
        when(jdbc.queryForObject(contains("COUNT(DISTINCT name)"), eq(Integer.class), any(), any(), any())).thenReturn(0);

        // 第1条：撞云仓 HMAC 竞争保护白名单
        doThrow(new BizException(403, "受保护")).when(protection).assertUnprotected(eq(1L), eq("星逸老客服饰有限公司"), any());

        // 第2条：撞外部伙伴 90 天在锁私海客
        Customer lockedPrivate = new Customer();
        lockedPrivate.setId(88L);
        lockedPrivate.setName("伙伴在锁美妆公司");
        lockedPrivate.setReferrerId(501L);
        when(customers.selectOne(any())).thenAnswer(inv -> {
            // 仅第2条命中已有在锁客户，干净新行返回 null
            return lockedCallCount++ == 0 ? lockedPrivate : null;
        });

        // 插入干净新客户时回填自增主键
        doAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(999L);
            return 1;
        }).when(customers).insert(any(Customer.class));

        MktPublicSeaWashService wash = new MktPublicSeaWashService(
                jdbc, new ObjectMapper(), customers, seaMetas, batches, protection, audit);

        List<Map<String, Object>> input = List.of(
                Map.of("name", "星逸老客服饰有限公司", "phone", "13800001111", "category", "女装服饰"),
                Map.of("name", "伙伴在锁美妆公司", "phone", "13800002222", "category", "美妆个护"),
                // 同一代账财务号挂4家不同企业 -> 触发代账重号剔除
                Map.of("name", "代账甲公司", "phone", "13999990000"),
                Map.of("name", "代账乙公司", "phone", "13999990000"),
                Map.of("name", "代账丙公司", "phone", "13999990000"),
                Map.of("name", "代账丁公司", "phone", "13999990000"),
                // 干净高痛点新店 -> 正常入池且评定 S 级急迫
                Map.of("name", "杭州净增女装有限公司", "shopName", "衣恋抖店", "phone", "13766668888",
                        "category", "女装服饰", "estDailyOrders", 1200, "logisticsScore", "4.42")
        );

        Map<String, Object> res = wash.washJsonRows(1L, "CHANMAMA", "test.xlsx", input, false);
        assertThat(res.get("totalRows")).isEqualTo(7);
        assertThat(res.get("blockedWhHmac")).isEqualTo(1);
        assertThat(res.get("blockedPrivateLock")).isEqualTo(1);
        assertThat(res.get("blockedDupAgency")).isEqualTo(4);
        assertThat(res.get("insertedRows")).isEqualTo(1);
        verify(customers, times(1)).insert(argThat(c -> c.getReferrerId() == null && "杭州净增女装有限公司".equals(c.getName())));
    }

    private int lockedCallCount = 0;

    @Test
    void warehouseClaimEnforcesDaily5AndActive10QuotaAndSlaReclaimsOverdue72h() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BizSettings settings = mock(BizSettings.class);
        CustomerMapper customers = mock(CustomerMapper.class);
        MktPublicSeaMetaMapper seaMetas = mock(MktPublicSeaMetaMapper.class);
        MktWarehouseMapper warehouses = mock(MktWarehouseMapper.class);
        WhWorkspaceService whWorkspace = mock(WhWorkspaceService.class);
        MktAuditService audit = mock(MktAuditService.class);

        when(settings.getInt(eq("marketing"), eq("sea_sla_hours"), anyInt())).thenReturn(72);
        when(settings.getInt(eq("marketing"), eq("sea_wh_daily_cap"), anyInt())).thenReturn(5);
        when(settings.getInt(eq("marketing"), eq("sea_wh_active_cap"), anyInt())).thenReturn(10);

        MktWarehouse wh = new MktWarehouse();
        wh.setId(10L); wh.setTenantId(1L); wh.setProjectId(1L); wh.setName("星逸一号仓"); wh.setJoinStatus(5);
        WhIdentityService.WhIdentity whId = mock(WhIdentityService.WhIdentity.class);
        when(whId.warehouse()).thenReturn(wh);
        when(whWorkspace.owner()).thenReturn(whId);
        when(warehouses.selectForUpdate(10L)).thenReturn(wh);

        // 模拟今日已认领满 5 户 -> 阻断第 6 次认领
        when(jdbc.queryForObject(contains("claimed_at >="), eq(Integer.class), eq(10L), any())).thenReturn(5);

        MktPublicSeaService service = new MktPublicSeaService(
                jdbc, settings, customers, seaMetas, warehouses, whWorkspace, audit);

        assertThatThrownBy(() -> service.claimByWarehouse(901L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("5 户");

        // 模拟 72h 超时未首触达自动弹回公海
        when(jdbc.queryForList(contains("sla_deadline_at <="), eq(Long.class), any())).thenReturn(List.of(905L));
        when(seaMetas.update(isNull(), any())).thenReturn(1);
        int reclaimed = service.expireOverdueClaims(LocalDateTime.now());
        assertThat(reclaimed).isEqualTo(1);
        verify(customers).update(isNull(), any());
    }
}
