package com.zhyq.park.marketing.wh;

import com.zhyq.park.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/wh/v1/staff")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WH_OWNER')")
public class WhStaffController {
    private final WhWorkspaceService workspace;
    @GetMapping public Result<List<Map<String,Object>>> list() { return Result.ok(workspace.staff()); }
    @PostMapping public Result<Map<String,Object>> create(@RequestBody Map<String,Object> body) { return Result.ok(workspace.saveStaff(null,body)); }
    @PutMapping("/{id}") public Result<Map<String,Object>> update(@PathVariable Long id,@RequestBody Map<String,Object> body) { return Result.ok(workspace.saveStaff(id,body)); }
}
