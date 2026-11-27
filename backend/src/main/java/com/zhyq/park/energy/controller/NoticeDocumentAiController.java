package com.zhyq.park.energy.controller;

import com.zhyq.park.common.result.Result;
import com.zhyq.park.energy.service.NoticeDocumentAiService;
import com.zhyq.park.energy.service.NoticeDocumentAiService.VerifyResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "缴费汇总-AI回执与发票自动识别核准")
@RestController
@RequestMapping("/energy/notice-ai")
@RequiredArgsConstructor
public class NoticeDocumentAiController {

    private final NoticeDocumentAiService aiService;

    @Operation(summary = "上传租户缴费回执或发票并由AI自动识别核准")
    @PostMapping(value = "/verify-upload", consumes = "multipart/form-data")
    public Result<VerifyResult> verifyUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("mode") String mode,
            @RequestParam("docType") String docType,
            @RequestParam("periodId") Long periodId,
            @RequestParam(value = "ocrText", required = false) String ocrText) {
        return Result.ok(aiService.verifyAndUpload(file, mode, docType, periodId, ocrText));
    }

    @Operation(summary = "人工核对后手动绑定回执或发票到指定通知单")
    @PostMapping(value = "/manual-bind", consumes = "multipart/form-data")
    public Result<VerifyResult> manualBind(
            @RequestParam("file") MultipartFile file,
            @RequestParam("mode") String mode,
            @RequestParam("docType") String docType,
            @RequestParam("noticeId") Long noticeId) {
        return Result.ok(aiService.manualBind(file, mode, docType, noticeId));
    }
}
