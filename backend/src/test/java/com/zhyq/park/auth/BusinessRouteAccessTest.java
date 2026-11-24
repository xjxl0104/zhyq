package com.zhyq.park.auth;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessRouteAccessTest {
    @Test void quotationRoutesUseTheMatchingBusinessPermissions() {
        String card="/crm/marketing/quote/warehouses/2/rate-card";
        String comparison="/crm/marketing/quote/customers/3/comparison";
        var warehouse=Set.of("crm:marketing:warehouse:query");
        assertThat(BusinessRouteAccess.allowed(card,"GET",warehouse)).isTrue();
        assertThat(BusinessRouteAccess.allowed(card,"PUT",warehouse)).isFalse();
        assertThat(BusinessRouteAccess.allowed(comparison,"GET",warehouse)).isFalse();
        assertThat(BusinessRouteAccess.allowed(comparison,"GET",Set.of("crm:marketing:customer:query"))).isTrue();
    }
    private final Set<String> repair = Set.of("MENU:/property/workorder", "property:workorder:query",
            "property:workorder:edit");

    @Test void repairStaffCanUseWorkOrdersAndRequiredLocationLists() {
        assertThat(BusinessRouteAccess.allowed("/property/workorder/page", "GET", repair)).isTrue();
        assertThat(BusinessRouteAccess.allowed("/property/workorder", "POST", repair)).isTrue();
        assertThat(BusinessRouteAccess.allowed("/building/project/list", "GET", repair)).isTrue();
        assertThat(BusinessRouteAccess.allowed("/building/building/list", "GET", repair)).isTrue();
        assertThat(BusinessRouteAccess.allowed("/building/floor/list", "GET", repair)).isTrue();
        assertThat(BusinessRouteAccess.allowed("/building/floor/12/plan", "GET", repair)).isTrue();
        assertThat(BusinessRouteAccess.allowed("/pur/supplier/options", "GET", repair)).isTrue();
    }

    @Test void repairStaffCannotReadOrChangeOtherBusinessAreas() {
        for (String path : new String[]{"/dashboard/summary", "/crm/customer/page", "/finance/bill/page",
                "/system/user/page", "/building/building/12", "/pur/supplier/page"}) {
            assertThat(BusinessRouteAccess.allowed(path, "GET", repair)).as(path).isFalse();
        }
        assertThat(BusinessRouteAccess.allowed("/system/role", "DELETE", repair)).isFalse();
        assertThat(BusinessRouteAccess.allowed("/building/floor/12/plan", "PUT", repair)).isFalse();
    }

    @Test void activeRoleGrantsAndAdminRemainScoped() {
        assertThat(BusinessRouteAccess.allowed("/property/workorder/page", "GET", Set.of())).isFalse();
        assertThat(BusinessRouteAccess.allowed("/crm/customer/page", "GET", Set.of("crm:customer:query"))).isTrue();
        assertThat(BusinessRouteAccess.allowed("/system/user/page", "GET", Set.of("ROLE_admin"))).isTrue();
        assertThat(BusinessRouteAccess.allowed("/crm/customer/page", "GET", Set.of("ROLE_MP"))).isFalse();
        assertThat(BusinessRouteAccess.allowed("/mp/v1/me", "GET", Set.of("ROLE_MP"))).isTrue();
    }

    @Test void financeOverviewRetainsTheExistingReportApiBoundary() {
        String path = "/finance/report/overview";
        assertThat(BusinessRouteAccess.allowed(path, "GET", Set.of("finance:report:query"))).isTrue();
        assertThat(BusinessRouteAccess.allowed(path, "GET", Set.of("MENU:/finance/report"))).isTrue();
        assertThat(BusinessRouteAccess.allowed(path, "GET", Set.of("finance:bill:query"))).isFalse();
        assertThat(BusinessRouteAccess.allowed(path, "GET", Set.of("MENU:/finance/overview"))).isFalse();
        assertThat(BusinessRouteAccess.allowed(path, "GET", Set.of())).isFalse();
    }

    @Test void contractCollectionSharesTheReportApiBoundary() {
        String path = "/finance/report/contract-collection";
        assertThat(BusinessRouteAccess.allowed(path, "GET", Set.of("finance:report:query"))).isTrue();
        assertThat(BusinessRouteAccess.allowed(path, "GET", Set.of("MENU:/finance/report"))).isTrue();
        assertThat(BusinessRouteAccess.allowed(path, "GET", Set.of("finance:bill:query"))).isFalse();
        assertThat(BusinessRouteAccess.allowed(path, "GET", Set.of())).isFalse();
    }

    @Test void crossDepartmentApproversReachWithdrawalTaskEndpointsWhileSensitivePayoutApisStayGuarded() {
        Set<String> oaApprover = Set.of("MENU:/oa/approval");
        assertThat(BusinessRouteAccess.allowed("/crm/marketing/withdrawal/18", "GET", oaApprover)).isTrue();
        assertThat(BusinessRouteAccess.allowed("/crm/marketing/withdrawal/18/approve", "POST", oaApprover)).isTrue();
        assertThat(BusinessRouteAccess.allowed("/crm/marketing/withdrawal/18/reject", "POST", oaApprover)).isTrue();
        // 收款明文账号解密与代申请仍受营销财务权限点严格保护
        assertThat(BusinessRouteAccess.allowed("/crm/marketing/withdrawal/18/pay-account", "GET", oaApprover)).isFalse();
        assertThat(BusinessRouteAccess.allowed("/crm/marketing/withdrawal/manual", "POST", oaApprover)).isFalse();
        // 小程序令牌在入口即被隔离，不能借此进入后台提现接口
        assertThat(BusinessRouteAccess.allowed("/crm/marketing/withdrawal/18", "GET", Set.of("ROLE_MP"))).isFalse();
    }

    @Test void leaseFeeEndpointsAllowRentPropertyAndSummaryMenus() {
        assertThat(BusinessRouteAccess.allowed("/energy/lease-fee/overview", "GET", Set.of("MENU:/energy/rent"))).isTrue();
        assertThat(BusinessRouteAccess.allowed("/energy/lease-fee/open", "POST", Set.of("MENU:/energy/property"))).isTrue();
        assertThat(BusinessRouteAccess.allowed("/energy/lease-fee/1/notices", "GET", Set.of("MENU:/energy/summary"))).isTrue();
        assertThat(BusinessRouteAccess.allowed("/energy/lease-fee/overview", "GET", Set.of("MENU:/energy/electric"))).isFalse();
        assertThat(BusinessRouteAccess.allowed("/energy/lease-fee/overview", "GET", Set.of())).isFalse();
    }
}
