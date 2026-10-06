package com.zhyq.park.auth;

import java.util.Set;

/**
 * Business-area boundary for controllers that predate method-level RBAC.
 * MENU authorities come from currently active database grants on every request.
 * Existing @PreAuthorize checks still apply after this coarse route check.
 */
public final class BusinessRouteAccess {
    private BusinessRouteAccess() {}

    public static boolean allowed(String rawPath, String method, Set<String> grants) {
        if (grants.contains("ROLE_admin")) return true;
        String path = rawPath.replaceAll("/+$", "");
        if (grants.contains("ROLE_MP")) return path.startsWith("/mp/v1/");
        if (grants.contains("ROLE_WH")) return path.startsWith("/wh/v1/");
        if (path.startsWith("/auth/") || path.startsWith("/file/")
                || path.startsWith("/mp/v1/") || path.startsWith("/wh/v1/")
                || path.startsWith("/open/v1/")) return true;
        if ((path.equals("/suggestion") && "POST".equals(method))
                || path.equals("/building/project/list") || path.equals("/suggestion/mine")
                || path.startsWith("/suggestion/mine/")) return true;
        if (path.equals("/pur/supplier/options")
                || path.equals("/property/responsible-unit/options")) {
            if (workOrder(grants)) return true;
        }
        if ("GET".equals(method) && (path.equals("/building/building/list")
                || path.equals("/building/floor/list")) && workOrder(grants)) return true;
        if (path.startsWith("/building/floor/") && path.endsWith("/plan")
                && "GET".equals(method) && workOrder(grants)) return true;

        String[] parts = path.split("/");
        if (parts.length < 2) return false;
        String area = parts[1];
        String resource = parts.length > 2 ? parts[2] : "";
        if ("dashboard".equals(area))
            return anyMenu(grants, "/dashboard", "/overview", "/data/center");
        if ("contract".equals(area))
            return anyMenu(grants, "/contract/list", "/contract/archive", "/contract/setting")
                    || hasPrefix(grants, "contract:");
        if ("budget".equals(area))
            return hasPrefix(grants, "budget:") || hasMenu(grants, "/budget/annual")
                    || hasMenu(grants, "/budget/monthly") || hasMenu(grants, "/budget/flow");
        if ("file".equals(area)) return true;
        String permPrefix = area + ":" + resource + ":";
        if ("crm".equals(area) && "marketing".equals(resource) && parts.length > 3)
            permPrefix = "crm:marketing:" + parts[3] + ":";
        if (hasPrefix(grants, permPrefix)) return true;

        String page = "/" + area + (resource.isEmpty() ? "" : "/" + resource);
        if (hasMenu(grants, page)) return true;
        if (parts.length > 3 && hasMenu(grants, page + "/" + parts[3])) return true;
        return switch (area + "/" + resource) {
            case "building/floor" -> hasMenu(grants, "/building/building")
                    || hasPrefix(grants, "building:floorPlan:");
            case "building/room" -> hasMenu(grants, "/building/room");
            case "tenant/info" -> hasMenu(grants, "/tenant/list") || hasPrefix(grants, "tenant:");
            case "finance/payment" -> hasMenu(grants, "/finance/cashier")
                    || hasPrefix(grants, "finance:payment:");
            case "finance/receivable" -> hasMenu(grants, "/finance/receivable-register")
                    || hasPrefix(grants, "receivable:");
            case "property/meeting" -> hasMenu(grants, "/property/meeting");
            case "property/check" -> anyMenu(grants, "/property/check-clean", "/property/check-green",
                    "/property/check-quality") || hasPrefix(grants, "property:check:");
            case "property/feedback" -> hasMenu(grants, "/property/complaint")
                    || hasPrefix(grants, "property:feedback:");
            case "energy/stats-api" -> hasMenu(grants, "/energy/stats");
            case "energy/reading" -> hasMenu(grants, "/energy/meter");
            case "service/product" -> hasMenu(grants, "/service/mall");
            case "space/", "space/room" -> hasMenu(grants, "/building/room") || hasPrefix(grants, "building:room:");
            case "app/", "app/vending", "vending/", "vending/machine" -> hasMenu(grants, "/app/center") || hasPrefix(grants, "vending:");
            case "pur/plan", "pur/request" -> anyMenu(grants, "/budget/plan-year", "/budget/plan-month")
                    || hasPrefix(grants, "pur:plan:") || hasPrefix(grants, "pur:request:");
            case "pur/supplier" -> hasMenu(grants, "/property/responsible-unit")
                    || hasPrefix(grants, "pur:supplier:") || hasPrefix(grants, "property:unit:");
            case "pur/supplier-contract" -> hasMenu(grants, "/property/responsible-unit")
                    || hasPrefix(grants, "pur:supplierContract:");
            case "workflow/", "workflow/definition" -> hasMenu(grants, "/budget/flow") || hasPrefix(grants, "workflow:");
            case "suggestion/manage" -> hasMenu(grants, "/suggestion/manage")
                    || grants.contains("suggestion:manage");
            case "todo/", "todo/page" -> hasMenu(grants, "/dashboard") || hasMenu(grants, "/oa/task");
            default -> false;
        };
    }

    private static boolean workOrder(Set<String> grants) {
        return hasMenu(grants, "/property/workorder") || hasPrefix(grants, "property:workorder:");
    }
    private static boolean hasMenu(Set<String> grants, String path) {
        return grants.contains("MENU:" + path);
    }
    private static boolean anyMenu(Set<String> grants, String... paths) {
        for (String path : paths) if (hasMenu(grants, path)) return true;
        return false;
    }
    private static boolean hasPrefix(Set<String> grants, String prefix) {
        return grants.stream().anyMatch(grant -> grant.startsWith(prefix));
    }
}
