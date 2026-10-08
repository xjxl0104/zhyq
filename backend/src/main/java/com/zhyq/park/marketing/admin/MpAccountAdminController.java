package com.zhyq.park.marketing.admin;

import com.zhyq.park.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** All methods also check live super-admin grants in the service. */
@RestController
@RequestMapping("/mp/v1/admin/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MP')")
public class MpAccountAdminController {
    private final MktAccountAdminService service;

    @GetMapping
    public Result<Map<String, Object>> page(@RequestParam(defaultValue="mp") String type,
            @RequestParam(required=false) String keyword, @RequestParam(defaultValue="1") int pageNo,
            @RequestParam(defaultValue="20") int pageSize) {
        return Result.ok(service.page(type, keyword, pageNo, pageSize));
    }
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody MktAccountAdminService.CreateRequest body) {
        return Result.ok(service.create(body));
    }
    @PutMapping("/mp/{id}/invite-code")
    public Result<Void> invite(@PathVariable Long id, @RequestBody MktAccountAdminService.InviteRequest body) {
        service.changeInvite(id, body); return Result.ok();
    }
    @PutMapping("/{type}/{id}/login-status")
    public Result<Void> status(@PathVariable String type, @PathVariable Long id, @RequestBody MktAccountAdminService.StatusRequest body) {
        service.setDisabled(type, id, body); return Result.ok();
    }
    @DeleteMapping("/{type}/{id}")
    public Result<Void> delete(@PathVariable String type, @PathVariable Long id, @RequestBody MktAccountAdminService.DeleteRequest body) {
        service.delete(type, id, body.reason()); return Result.ok();
    }
}
