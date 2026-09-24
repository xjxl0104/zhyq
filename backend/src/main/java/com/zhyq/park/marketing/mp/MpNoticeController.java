package com.zhyq.park.marketing.mp;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.service.MktPartnerNoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/mp/v1/notice")
@PreAuthorize("hasRole('MP')")
@RequiredArgsConstructor
public class MpNoticeController {
    private final MktPartnerNoticeService notices;
    @PostMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id) { notices.read(id,MpAuthService.currentPromoterId()); return Result.ok(); }
}
