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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 内部专属公海池权限隔离、广东定向省钱锁、三道防撞洗数、500m GPS 拍照打卡延保 15 天与云仓 5/10 配额回归测试。
 */
class MktPublicSeaServiceAndAccessTest {

    private int lockedCallCount = 0;

    @Test
    void externalPromoterRoleMpIsStrictlyDeniedFromPublicSeaRoutes() {
        assertThat(BusinessRouteAccess.allowed("/crm/marketing/public-sea/page", "GET", Set.of("ROLE_MP"))).isFalse();
        assertThat(BusinessRouteAccess.allowed("/crm/marketing/public-sea/1/claim", "POST", Set.of("ROLE_MP"))).isFalse();
        assertThat(BusinessRouteAccess.allowed("/crm/marketing/public-sea/1/visit-checkin", "POST", Set.of("ROLE_MP"))).isFalse();
        assertThat(BusinessRouteAccess.allowed("/wh/v1/public-sea", "GET", Set.of("ROLE_MP"))).isFalse();

        assertThat(BusinessRouteAccess.allowed("/crm/marketing/public-sea/page", "GET",
                Set.of("crm:marketing:customer:query"))).isTrue();
        assertThat(BusinessRouteAccess.allowed("/crm/marketing/public-sea/wash-rows", "POST",
                Set.of("crm:marketing:public-sea:edit"))).isTrue();
        assertThat(BusinessRouteAccess.allowed("/open/v1/ad-leads/feiyu", "POST", Set.of())).isTrue();
    }

    @Test
    void threeGateWashAndGuangdongLockBlocksOutProvincePrivateLockWarehouseHmacAndAgencyDupPhones() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BizSettings bizSettings = mock(BizSettings.class);
        CustomerMapper customers = mock(CustomerMapper.class);
        MktPublicSeaMetaMapper seaMetas = mock(MktPublicSeaMetaMapper.class);
        MktPublicSeaWashBatchMapper batches = mock(MktPublicSeaWashBatchMapper.class);
        MktCompetitionProtectionService protection = mock(MktCompetitionProtectionService.class);
        MktAuditService audit = mock(MktAuditService.class);

        when(bizSettings.raw("marketing", "sea_region_lock_province")).thenReturn("广东省");
        when(jdbc.queryForList(contains("FROM biz_project"), eq(Long.class))).thenReturn(List.of(1L));
        when(jdbc.queryForList(contains("SELECT tenant_id"), eq(Long.class), anyLong())).thenReturn(List.of(1L));
        when(jdbc.queryForList(contains("FROM crm_ext_company_cache"), any(), any())).thenReturn(List.of());
        when(jdbc.queryForObject(contains("COUNT(DISTINCT name)"), eq(Integer.class), any(), any(), any())).thenReturn(0);

        // 第1条：撞云仓 HMAC 竞争保护白名单
        doThrow(new BizException(403, "受保护")).when(protection).assertUnprotected(eq(1L), eq("广州星逸老客服饰有限公司"), any());

        // 第2条：撞外部伙伴 90 天在锁私海客
        Customer lockedPrivate = new Customer();
        lockedPrivate.setId(88L);
        lockedPrivate.setName("深圳伙伴在锁美妆公司");
        lockedPrivate.setReferrerId(501L);
        lockedCallCount = 0;
        when(customers.selectOne(any())).thenAnswer(inv -> lockedCallCount++ == 0 ? lockedPrivate : null);

        // 插入干净广东新客户时回填自增主键
        doAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(999L);
            return 1;
        }).when(customers).insert(any(Customer.class));

        MktExtDataGateway extGateway = new MktExtDataGateway(jdbc, bizSettings, new ObjectMapper());
        MktPublicSeaWashService wash = new MktPublicSeaWashService(
                jdbc, new ObjectMapper(), customers, seaMetas, batches, protection, extGateway, audit);

        List<Map<String, Object>> input = List.of(
                // 外省主体（浙江杭州）-> 广东定向省钱锁直接跳过，免扣外部 API 费
                Map.of("name", "杭州四季青电商有限公司", "shopName", "四季风尚店", "region", "浙江省杭州市"),
                Map.of("name", "广州星逸老客服饰有限公司", "phone", "13800001111", "category", "女装服饰"),
                Map.of("name", "深圳伙伴在锁美妆公司", "phone", "13800002222", "category", "美妆个护"),
                // 同一代账财务号挂 4 家不同企业 -> 触发代账重号剔除
                Map.of("name", "广州代账甲公司", "phone", "13999990000"),
                Map.of("name", "广州代账乙公司", "phone", "13999990000"),
                Map.of("name", "广州代账丙公司", "phone", "13999990000"),
                Map.of("name", "广州代账丁公司", "phone", "13999990000"),
                // 干净广东高痛点新店 -> 正常入池且评定 S 级急迫 + GZ 战区
                Map.of("name", "广州白云净增美妆有限公司", "shopName", "悦颜洗护旗舰店", "phone", "13766668888",
                        "category", "个人洗护", "estDailyOrders", 1200, "logisticsScore", "4.42",
                        "address", "广东省广州市白云区嘉禾望岗美妆电商大厦8楼")
        );

        Map<String, Object> res = wash.washJsonRows(1L, "CHANMAMA", "gd-top500.xlsx", input, false);
        assertThat(res.get("totalRows")).isEqualTo(8);
        assertThat(res.get("skippedOutProvince")).isEqualTo(1);
        assertThat(res.get("blockedWhHmac")).isEqualTo(1);
        assertThat(res.get("blockedPrivateLock")).isEqualTo(1);
        assertThat(res.get("blockedDupAgency")).isEqualTo(4);
        assertThat(res.get("insertedRows")).isEqualTo(1);
        verify(customers, times(1)).insert(argThat(c -> c.getReferrerId() == null && "广州白云净增美妆有限公司".equals(c.getName())));
        verify(seaMetas, times(1)).insert(argThat(m -> "GZ".equals(m.getZoneCode()) && "嘉禾望岗美妆电商大厦".equals(m.getBuildingName())));
    }

    @Test
    void gpsPhotoCheckinEnforces500mGuardAndGrants15DayVisitProtection() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BizSettings settings = mock(BizSettings.class);
        CustomerMapper customers = mock(CustomerMapper.class);
        MktPublicSeaMetaMapper seaMetas = mock(MktPublicSeaMetaMapper.class);
        MktWarehouseMapper warehouses = mock(MktWarehouseMapper.class);
        WhWorkspaceService whWorkspace = mock(WhWorkspaceService.class);
        MktExtDataGateway extGateway = mock(MktExtDataGateway.class);
        MktAuditService audit = mock(MktAuditService.class);

        when(settings.getInt(eq("marketing"), eq("sea_checkin_max_meters"), anyInt())).thenReturn(500);
        when(settings.getInt(eq("marketing"), eq("sea_visit_protect_days"), anyInt())).thenReturn(15);

        MktPublicSeaMeta meta = new MktPublicSeaMeta();
        meta.setCustomerId(2001L);
        meta.setTenantId(1L);
        meta.setProjectId(1L);
        meta.setLng(new BigDecimal("113.285610"));
        meta.setLat(new BigDecimal("23.224810"));
        when(seaMetas.selectById(2001L)).thenReturn(meta);

        MktPublicSeaService service = new MktPublicSeaService(
                jdbc, settings, customers, seaMetas, warehouses, whWorkspace, extGateway, audit);

        // 距离约 1.4 公里 (>500m) -> 严厉拦截虚拟异地打卡
        assertThatThrownBy(() -> service.visitCheckin(
                2001L, new BigDecimal("113.298000"), new BigDecimal("23.230000"),
                "/uploads/door-photo.jpg", "现场面见老板谈妥试发"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("超过允许阈值 500 米");

        // 距离约 68 米 (<=500m) + 实拍照片 + 纪要 -> 打卡通过并延长保护期 15 天
        Map<String, Object> ok = service.visitCheckin(
                2001L, new BigDecimal("113.286100"), new BigDecimal("23.225220"),
                "/uploads/door-photo.jpg", "现场面见老板盘点日均1800单下周试发");
        assertThat((Integer) ok.get("distanceMeters")).isLessThanOrEqualTo(500);
        assertThat(ok.get("visitProtectDays")).isEqualTo(15);
        verify(jdbc).update(contains("INSERT INTO crm_public_sea_checkin"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void warehouseClaimEnforcesDaily5AndActive10QuotaAndSlaReclaimsOverdue72h() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BizSettings settings = mock(BizSettings.class);
        CustomerMapper customers = mock(CustomerMapper.class);
        MktPublicSeaMetaMapper seaMetas = mock(MktPublicSeaMetaMapper.class);
        MktWarehouseMapper warehouses = mock(MktWarehouseMapper.class);
        WhWorkspaceService whWorkspace = mock(WhWorkspaceService.class);
        MktExtDataGateway extGateway = mock(MktExtDataGateway.class);
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
                jdbc, settings, customers, seaMetas, warehouses, whWorkspace, extGateway, audit);

        assertThatThrownBy(() -> service.claimByWarehouse(901L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("5 户");

        // 模拟 72h 超时未首触达且不在 15 天地推保护期内 -> 自动弹回公海
        when(jdbc.queryForList(contains("sla_deadline_at <="), eq(Long.class), any(), any())).thenReturn(List.of(905L));
        when(seaMetas.update(isNull(), any())).thenReturn(1);
        int reclaimed = service.expireOverdueClaims(LocalDateTime.now());
        assertThat(reclaimed).isEqualTo(1);
        verify(customers).update(isNull(), any());
    }
}
