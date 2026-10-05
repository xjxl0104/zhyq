package com.zhyq.park.auth;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessRouteAccessTest {
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
}
