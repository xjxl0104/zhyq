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
 * 内部公海：权限隔离、先免费后付费的洗数顺序、广东省钱锁、大厦名提取、500m 照片打卡防作弊/防抢单、云仓配额。
 */
class MktPublicSeaServiceAndAccessTest {

    private int selectOneCalls = 0;

    @org.junit.jupiter.api.BeforeAll
    static void initMybatisLambdaCache() {
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        for (Class<?> type : List.of(MktPublicSeaMeta.class, Customer.class)) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, type);
        }
    }

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
    }

    @Test
    void zoneDefaultsToUnknownAndBuildingNameStripsAdminPrefixes() {
        assertThat(MktExtDataGateway.classifyZone("某某服饰有限公司", null)).isEqualTo("UNKNOWN");
        assertThat(MktExtDataGateway.classifyZone("浙江省杭州市", null)).isEqualTo("OUT_GD");
        assertThat(MktExtDataGateway.classifyZone("某公司", "91330100MA2XXXXX")).isEqualTo("OUT_GD");
        assertThat(MktExtDataGateway.classifyZone("某公司", "91440100MA5XXXXX")).isEqualTo("OTHER_GD");
        assertThat(MktExtDataGateway.classifyZone("广州市白云区", null)).isEqualTo("GZ");

        assertThat(MktExtDataGateway.extractBuildingName("广东省广州市白云区嘉禾望岗美妆电商大厦8楼")).isEqualTo("嘉禾望岗美妆电商大厦");
        assertThat(MktExtDataGateway.extractBuildingName("广州市番禺区南村万博直播电商大厦802室")).isEqualTo("南村万博直播电商大厦");
        assertThat(MktExtDataGateway.extractBuildingName("东莞市虎门镇博涌大莹服装电商城4楼")).isEqualTo("博涌大莹服装电商城");
        assertThat(MktExtDataGateway.extractBuildingName("广州市番禺区南村镇兴南大道368号万博电商大厦")).isEqualTo("万博电商大厦");
    }

    @Test
    void washRunsFreeGatesBeforeAnyPaidLookupAndSkipsPaidForUnknownRegion() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BizSettings bizSettings = mock(BizSettings.class);
        CustomerMapper customers = mock(CustomerMapper.class);
        MktPublicSeaMetaMapper seaMetas = mock(MktPublicSeaMetaMapper.class);
        MktPublicSeaWashBatchMapper batches = mock(MktPublicSeaWashBatchMapper.class);
        MktCompetitionProtectionService protection = mock(MktCompetitionProtectionService.class);
        MktConnectorHubService hub = mock(MktConnectorHubService.class);
        MktAuditService audit = mock(MktAuditService.class);

        when(bizSettings.raw("marketing", "sea_region_lock_province")).thenReturn("广东省");
        when(jdbc.queryForList(contains("SELECT tenant_id"), eq(Long.class), anyLong())).thenReturn(List.of(1L));
        when(jdbc.queryForList(contains("FROM crm_ext_company_cache"), eq(1L), anyString())).thenReturn(List.of());
        when(jdbc.queryForObject(contains("COUNT(DISTINCT name)"), eq(Integer.class), any(), any(), any())).thenReturn(0);
        when(hub.acquireActiveCredentials(anyLong(), anyString(), anyBoolean())).thenReturn(Optional.empty());

        doThrow(new BizException(403, "受保护")).when(protection).assertUnprotected(eq(1L), eq("广州星逸老客服饰有限公司"), any());
        Customer lockedPrivate = new Customer();
        lockedPrivate.setId(88L);
        lockedPrivate.setReferrerId(501L);
        selectOneCalls = 0;
        when(customers.selectOne(any())).thenAnswer(inv -> selectOneCalls++ == 0 ? lockedPrivate : null);
        doAnswer(inv -> { Customer c = inv.getArgument(0); c.setId(900L + selectOneCalls); return 1; })
                .when(customers).insert(any(Customer.class));

        MktExtDataGateway gateway = new MktExtDataGateway(jdbc, bizSettings, new ObjectMapper(), hub);
        MktPublicSeaWashService wash = new MktPublicSeaWashService(
                jdbc, new ObjectMapper(), customers, seaMetas, batches, protection, gateway, audit,
                mock(org.springframework.transaction.PlatformTransactionManager.class));

        List<Map<String, Object>> input = List.of(
                Map.of("name", "杭州四季青电商有限公司", "shopName", "四季风尚店", "region", "浙江省杭州市"),
                Map.of("name", "广州星逸老客服饰有限公司", "phone", "13800001111"),
                Map.of("name", "深圳伙伴在锁美妆公司", "phone", "13800002222"),
                Map.of("name", "广州代账甲公司", "phone", "13999990000"),
                Map.of("name", "广州代账乙公司", "phone", "13999990000"),
                Map.of("name", "广州代账丙公司", "phone", "13999990000"),
                Map.of("name", "广州代账丁公司", "phone", "13999990000"),
                Map.of("name", "广州白云净增美妆有限公司", "shopName", "悦颜洗护旗舰店", "phone", "13766668888",
                        "category", "个人洗护", "estDailyOrders", 1200, "logisticsScore", "4.42",
                        "address", "广东省广州市白云区嘉禾望岗美妆电商大厦8楼"),
                Map.of("name", "无地址某某服饰有限公司", "phone", "13711112222")
        );

        Map<String, Object> res = wash.washJsonRows(1L, "CHANMAMA", "gd-top500.xlsx", input, false);
        assertThat(res.get("totalRows")).isEqualTo(9);
        assertThat(res.get("skippedOutProvince")).isEqualTo(1);
        assertThat(res.get("blockedWhHmac")).isEqualTo(1);
        assertThat(res.get("blockedPrivateLock")).isEqualTo(1);
        assertThat(res.get("blockedDupAgency")).isEqualTo(4);
        assertThat(res.get("insertedRows")).isEqualTo(2);

        // 只有广州那一行会尝试付费源（企查查/天眼查/百元包各 1 次，均未开通）；被拦截行与地区不明行 0 次
        verify(hub, times(3)).acquireActiveCredentials(eq(1L), anyString(), eq(false));
        verify(seaMetas).insert(org.mockito.ArgumentMatchers.<MktPublicSeaMeta>argThat(
                m -> "GZ".equals(m.getZoneCode()) && "嘉禾望岗美妆电商大厦".equals(m.getBuildingName())));
        verify(seaMetas).insert(org.mockito.ArgumentMatchers.<MktPublicSeaMeta>argThat(
                m -> "UNKNOWN".equals(m.getZoneCode()) && m.getProvince() == null));
    }

    private MktPublicSeaService seaService(JdbcTemplate jdbc, MktPublicSeaMetaMapper seaMetas, BizSettings settings) {
        return new MktPublicSeaService(jdbc, settings, mock(CustomerMapper.class), seaMetas,
                mock(MktWarehouseMapper.class), mock(WhWorkspaceService.class), mock(MktExtDataGateway.class), mock(MktAuditService.class));
    }

    private static MktPublicSeaMeta meta(String status, String holder, boolean located) {
        MktPublicSeaMeta m = new MktPublicSeaMeta();
        m.setCustomerId(2001L);
        m.setTenantId(1L);
        m.setProjectId(1L);
        m.setClaimStatus(status);
        m.setClaimHolderName(holder);
        if (located) {
            m.setLng(new BigDecimal("113.285610"));
            m.setLat(new BigDecimal("23.224810"));
        }
        return m;
    }

    @Test
    void checkinRejectsUnlocatedCustomersOthersClaimsWarehouseClaimsAndFakePhotos() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BizSettings settings = mock(BizSettings.class);
        when(settings.getInt(eq("marketing"), anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(2));
        MktPublicSeaMetaMapper seaMetas = mock(MktPublicSeaMetaMapper.class);
        MktPublicSeaService service = seaService(jdbc, seaMetas, settings);
        BigDecimal lng = new BigDecimal("113.286100");
        BigDecimal lat = new BigDecimal("23.225220");

        when(seaMetas.selectById(2001L)).thenReturn(meta("UNCLAIMED", null, false));
        assertThatThrownBy(() -> service.visitCheckin(2001L, lng, lat, "file:77", "现场面见老板核实单量"))
                .isInstanceOf(BizException.class).hasMessageContaining("没有落图坐标");

        when(seaMetas.selectById(2001L)).thenReturn(meta("CLAIMED_ADMIN", "张专员", true));
        assertThatThrownBy(() -> service.visitCheckin(2001L, lng, lat, "file:77", "现场面见老板核实单量"))
                .isInstanceOf(BizException.class).hasMessageContaining("张专员");

        when(seaMetas.selectById(2001L)).thenReturn(meta("CLAIMED_WH", "某云仓", true));
        assertThatThrownBy(() -> service.visitCheckin(2001L, lng, lat, "file:77", "现场面见老板核实单量"))
                .isInstanceOf(BizException.class).hasMessageContaining("加盟云仓");

        when(seaMetas.selectById(2001L)).thenReturn(meta("UNCLAIMED", null, true));
        assertThatThrownBy(() -> service.visitCheckin(2001L, lng, lat, "/uploads/checkin/door-live.jpg", "现场面见老板核实单量"))
                .isInstanceOf(BizException.class).hasMessageContaining("拍照上传");

        when(jdbc.queryForList(contains("FROM sys_file"), eq(77L))).thenReturn(List.of(
                Map.of("create_by", "别人", "create_time", LocalDateTime.now(), "content_type", "image/jpeg")));
        assertThatThrownBy(() -> service.visitCheckin(2001L, lng, lat, "file:77", "现场面见老板核实单量"))
                .isInstanceOf(BizException.class).hasMessageContaining("本人");

        when(jdbc.queryForList(contains("FROM sys_file"), eq(77L))).thenReturn(List.of(
                Map.of("create_by", "system", "create_time", LocalDateTime.now().minusHours(2), "content_type", "image/jpeg")));
        assertThatThrownBy(() -> service.visitCheckin(2001L, lng, lat, "file:77", "现场面见老板核实单量"))
                .isInstanceOf(BizException.class).hasMessageContaining("重新拍照");
    }

    @Test
    void checkinEnforces500mAndGrants15DaysWithFreshOwnPhoto() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BizSettings settings = mock(BizSettings.class);
        when(settings.getInt(eq("marketing"), anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(2));
        MktPublicSeaMetaMapper seaMetas = mock(MktPublicSeaMetaMapper.class);
        when(seaMetas.selectById(2001L)).thenReturn(meta("UNCLAIMED", null, true));
        when(seaMetas.update(isNull(), any())).thenReturn(1);
        when(jdbc.queryForList(contains("FROM sys_file"), eq(77L))).thenReturn(List.of(
                Map.of("create_by", "system", "create_time", LocalDateTime.now().minusMinutes(3), "content_type", "image/jpeg")));
        MktPublicSeaService service = seaService(jdbc, seaMetas, settings);

        assertThatThrownBy(() -> service.visitCheckin(2001L, new BigDecimal("113.298000"), new BigDecimal("23.230000"),
                "file:77", "现场面见老板核实单量"))
                .isInstanceOf(BizException.class).hasMessageContaining("超过允许阈值 500 米");

        Map<String, Object> ok = service.visitCheckin(2001L, new BigDecimal("113.286100"), new BigDecimal("23.225220"),
                "file:77", "现场面见老板盘点日均1800单");
        assertThat((Integer) ok.get("distanceMeters")).isLessThanOrEqualTo(500);
        assertThat(ok.get("visitProtectDays")).isEqualTo(15);
        verify(jdbc).update(contains("INSERT INTO crm_public_sea_checkin"),
                any(), any(), any(), any(), any(), any(), any(), eq("file:77"), any(), any());
    }

    private MktPublicSeaWashService washWith(JdbcTemplate jdbc, CustomerMapper customers, MktPublicSeaMetaMapper seaMetas,
                                             MktPublicSeaWashBatchMapper batches, MktExtDataGateway gateway) {
        return new MktPublicSeaWashService(jdbc, new ObjectMapper(), customers, seaMetas, batches,
                mock(MktCompetitionProtectionService.class), gateway, mock(MktAuditService.class),
                mock(org.springframework.transaction.PlatformTransactionManager.class));
    }

    @Test
    void dryRunPreviewNeverCallsPaidSourcesAndLargeImportsGoAsync() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BizSettings bizSettings = mock(BizSettings.class);
        MktConnectorHubService hub = mock(MktConnectorHubService.class);
        MktPublicSeaWashBatchMapper batches = mock(MktPublicSeaWashBatchMapper.class);
        when(jdbc.queryForList(contains("SELECT tenant_id"), eq(Long.class), anyLong())).thenReturn(List.of(1L));
        when(jdbc.queryForList(contains("FROM crm_ext_company_cache"), eq(1L), anyString())).thenReturn(List.of());
        when(jdbc.queryForObject(contains("COUNT(DISTINCT name)"), eq(Integer.class), any(), any(), any())).thenReturn(0);
        MktExtDataGateway gateway = new MktExtDataGateway(jdbc, bizSettings, new ObjectMapper(), hub);
        MktPublicSeaWashService wash = washWith(jdbc, mock(CustomerMapper.class), mock(MktPublicSeaMetaMapper.class), batches, gateway);

        Map<String, Object> preview = wash.washJsonRows(1L, "CHANMAMA", "p.xlsx", List.of(
                Map.of("name", "广州某美妆有限公司", "phone", "13700000001", "address", "广州市白云区")), true);
        assertThat(preview.get("insertedRows")).isEqualTo(1);
        verifyNoInteractions(hub);

        List<Map<String, Object>> many = new java.util.ArrayList<>();
        for (int i = 0; i < MktPublicSeaWashService.ASYNC_THRESHOLD + 1; i++) {
            many.add(Map.of("name", "广州批量公司" + i, "phone", "1370000" + String.format("%04d", i)));
        }
        Map<String, Object> async = wash.washJsonRows(1L, "CHANMAMA", "big.xlsx", many, false);
        assertThat(async.get("async")).isEqualTo(true);
        assertThat(async.get("status")).isEqualTo("RUNNING");
        verify(batches).insert(org.mockito.ArgumentMatchers.<com.zhyq.park.marketing.entity.MktPublicSeaWashBatch>argThat(
                b -> "RUNNING".equals(b.getStatus()) && b.getTotalRows() == many.size()));
    }

    @Test
    void asyncRunnerContinuesAfterRowErrorAndMarksBatchDone() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BizSettings bizSettings = mock(BizSettings.class);
        CustomerMapper customers = mock(CustomerMapper.class);
        when(jdbc.queryForList(contains("SELECT tenant_id"), eq(Long.class), anyLong())).thenReturn(List.of(1L));
        when(jdbc.queryForList(contains("FROM crm_ext_company_cache"), eq(1L), anyString())).thenReturn(List.of());
        when(jdbc.queryForObject(contains("COUNT(DISTINCT name)"), eq(Integer.class), any(), any(), any())).thenReturn(0);
        doThrow(new RuntimeException("写库失败")).doAnswer(inv -> { ((Customer) inv.getArgument(0)).setId(7L); return 1; })
                .when(customers).insert(any(Customer.class));
        MktExtDataGateway gateway = new MktExtDataGateway(jdbc, bizSettings, new ObjectMapper(), mock(MktConnectorHubService.class));
        MktPublicSeaWashService wash = washWith(jdbc, customers, mock(MktPublicSeaMetaMapper.class), mock(MktPublicSeaWashBatchMapper.class), gateway);

        wash.runAsyncWash(99L, "WASH-T", 1L, "CHANMAMA", List.of(
                new MktPublicSeaWashService.RawLeadRow("广州甲公司", null, "抖音", null, null, "13700000011", null, null, null, null, "广州", null, null, null, null),
                new MktPublicSeaWashService.RawLeadRow("广州乙公司", null, "抖音", null, null, "13700000012", null, null, null, null, "广州", null, null, null, null)));
        // 第 1 行写库失败被记为错误行，第 2 行照常入库，最终批次状态 DONE
        verify(jdbc).update(contains("UPDATE crm_public_sea_wash_batch"),
                eq(2), eq(1), eq(0), eq(0), eq(0), eq(0), eq(0), eq(0), any(), eq("DONE"), isNull(), eq("DONE"), eq(99L));
    }

    @Test
    void alignedCreditCodeMatchesExistingCustomerInsteadOfCreatingDuplicate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CustomerMapper customers = mock(CustomerMapper.class);
        MktPublicSeaMetaMapper seaMetas = mock(MktPublicSeaMetaMapper.class);
        MktExtDataGateway gateway = mock(MktExtDataGateway.class);
        when(jdbc.queryForList(contains("SELECT tenant_id"), eq(Long.class), anyLong())).thenReturn(List.of(1L));
        when(jdbc.queryForObject(contains("COUNT(DISTINCT name)"), eq(Integer.class), any(), any(), any())).thenReturn(0);
        when(gateway.alignCompany(anyLong(), anyString(), anyString(), any())).thenReturn(new MktExtDataGateway.CompanyProfile(
                "API_ALIGNED", "广州衣恋风尚服饰电子商务有限公司", "91440113MA5D88912X", "林晓峰", "200万", 14,
                "13826098821", "广东省", "广州市", "番禺区", "广州市番禺区南村万博直播电商大厦802室", 0, List.of()));
        when(jdbc.queryForList(contains("credit_code = ?"), eq(Long.class), eq(1L), eq("91440113MA5D88912X"))).thenReturn(List.of(555L));
        Customer existing = new Customer();
        existing.setId(555L);
        when(customers.selectById(555L)).thenReturn(existing);

        MktPublicSeaWashService wash = washWith(jdbc, customers, seaMetas, mock(MktPublicSeaWashBatchMapper.class), gateway);
        Map<String, Object> res = wash.lookupShopAndIngest(1L, "衣恋广派女装旗舰店", "抖音", "女装服饰", false);
        assertThat(res.get("insertedRows")).isEqualTo(1);
        assertThat(res.get("lastCustomerId")).isEqualTo(555L);
        verify(customers, never()).insert(any(Customer.class));
    }

    @Test
    void specialistCannotClaimBeyondActiveCap() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BizSettings settings = mock(BizSettings.class);
        when(settings.getInt(eq("marketing"), anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(2));
        when(jdbc.queryForObject(contains("claim_holder_name = ?"), eq(Integer.class), eq("system"))).thenReturn(30);
        MktPublicSeaService service = seaService(jdbc, mock(MktPublicSeaMetaMapper.class), settings);
        assertThatThrownBy(() -> service.claimByAdmin(901L))
                .isInstanceOf(BizException.class).hasMessageContaining("30 户");
    }

    @Test
    void warehouseClaimEnforcesDaily5AndActive10QuotaAndSlaReclaimsOverdue72h() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BizSettings settings = mock(BizSettings.class);
        CustomerMapper customers = mock(CustomerMapper.class);
        MktPublicSeaMetaMapper seaMetas = mock(MktPublicSeaMetaMapper.class);
        MktWarehouseMapper warehouses = mock(MktWarehouseMapper.class);
        WhWorkspaceService whWorkspace = mock(WhWorkspaceService.class);
        when(settings.getInt(eq("marketing"), eq("sea_sla_hours"), anyInt())).thenReturn(72);
        when(settings.getInt(eq("marketing"), eq("sea_wh_daily_cap"), anyInt())).thenReturn(5);
        when(settings.getInt(eq("marketing"), eq("sea_wh_active_cap"), anyInt())).thenReturn(10);

        MktWarehouse wh = new MktWarehouse();
        wh.setId(10L); wh.setTenantId(1L); wh.setProjectId(1L); wh.setName("星逸一号仓"); wh.setJoinStatus(5);
        when(whWorkspace.owner()).thenReturn(new WhIdentityService.Identity(wh, null));
        when(warehouses.selectForUpdate(10L)).thenReturn(wh);
        when(jdbc.queryForObject(contains("claimed_at >="), eq(Integer.class), eq(10L), any())).thenReturn(5);

        MktPublicSeaService service = new MktPublicSeaService(
                jdbc, settings, customers, seaMetas, warehouses, whWorkspace, mock(MktExtDataGateway.class), mock(MktAuditService.class));
        assertThatThrownBy(() -> service.claimByWarehouse(901L))
                .isInstanceOf(BizException.class).hasMessageContaining("5 户");

        when(jdbc.queryForList(contains("sla_deadline_at <="), eq(Long.class), any(), any())).thenReturn(List.of(905L));
        when(seaMetas.update(isNull(), any())).thenReturn(1);
        assertThat(service.expireOverdueClaims(LocalDateTime.now())).isEqualTo(1);
        verify(customers).update(isNull(), any());
    }
}
