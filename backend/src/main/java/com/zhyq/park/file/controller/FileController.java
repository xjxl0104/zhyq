package com.zhyq.park.file.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.file.FileAccessRule;
import com.zhyq.park.file.FileAttachRule;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.file.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Tag(name = "文件上传")
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService storageService;
    private final SysFileMapper fileMapper;

    @Operation(summary = "上传单文件")
    @PostMapping("/upload")
    public Result<SysFile> upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam(required = false) String bizType,
                                  @RequestParam(required = false) Long bizId) {
        return Result.ok(save(file, bizType, bizId));
    }

    @Operation(summary = "批量上传")
    @PostMapping("/upload-batch")
    public Result<List<SysFile>> uploadBatch(@RequestParam("files") MultipartFile[] files,
                                             @RequestParam(required = false) String bizType,
                                             @RequestParam(required = false) Long bizId) {
        List<SysFile> list = new ArrayList<>();
        for (MultipartFile f : files) {
            list.add(save(f, bizType, bizId));
        }
        return Result.ok(list);
    }

    @Operation(summary = "查业务对象附件列表")
    @GetMapping("/list")
    public Result<List<SysFile>> list(@RequestParam String bizType,
                                      @RequestParam Long bizId) {
        return Result.ok(fileMapper.selectList(new LambdaQueryWrapper<SysFile>()
            .eq(SysFile::getBizType, bizType)
            .eq(SysFile::getBizId, bizId)
            .orderByDesc(SysFile::getId)));
    }

    @Operation(summary = "删除附件(仅上传者本人或 admin)")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        SysFile f = fileMapper.selectById(id);
        if (f != null) {
            if (!FileAccessRule.canDelete(f, MyMetaObjectHandler.currentOperator(), isAdmin())) {
                throw new BizException(403, "仅上传者本人或管理员可删除该附件");
            }
            fileMapper.deleteById(id);          // 逻辑删除
            storageService.deletePhysical(f.getStorePath());
        }
        return Result.ok();
    }

    @Operation(summary = "鉴权下载附件(替代匿名静态 /uploads)")
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        SysFile f = fileMapper.selectById(id);
        if (f == null) {
            throw new BizException("附件不存在");
        }
        Resource resource = new FileSystemResource(storageService.resolveExisting(f.getStorePath()));
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(f.getContentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(f.getOriginalName() == null ? "file" : f.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(mediaType)
                .body(resource);
    }

    private static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_admin".equals(a.getAuthority()));
    }

    @Operation(summary = "批量关联附件到业务对象(先传后回填:仅回填 bizId 为空的记录)")
    @PostMapping("/attach")
    public Result<Integer> attach(@RequestBody AttachRequest req) {
        if (req == null || req.getBizType() == null || req.getBizType().isBlank()
                || req.getBizId() == null || req.getFileIds() == null || req.getFileIds().isEmpty()) {
            return Result.ok(0);
        }
        int attached = 0;
        for (Long fileId : req.getFileIds()) {
            SysFile f = fileMapper.selectById(fileId);
            if (f == null || !FileAttachRule.canAttach(f)) {
                continue;                    // 不存在或已关联 → 跳过,防越权覆盖
            }
            f.setBizType(req.getBizType());
            f.setBizId(req.getBizId());
            fileMapper.updateById(f);
            attached++;
        }
        return Result.ok(attached);
    }

    /** 关联请求体 */
    public static class AttachRequest {
        private String bizType;
        private Long bizId;
        private List<Long> fileIds;

        public String getBizType() { return bizType; }
        public void setBizType(String bizType) { this.bizType = bizType; }
        public Long getBizId() { return bizId; }
        public void setBizId(Long bizId) { this.bizId = bizId; }
        public List<Long> getFileIds() { return fileIds; }
        public void setFileIds(List<Long> fileIds) { this.fileIds = fileIds; }
    }

    private SysFile save(MultipartFile file, String bizType, Long bizId) {
        FileStorageService.StoredResult r = storageService.store(file);
        SysFile sf = new SysFile();
        sf.setBizType(bizType);
        sf.setBizId(bizId);
        sf.setOriginalName(r.originalName());
        sf.setStorePath(r.storePath());
        sf.setUrl(r.url());
        sf.setFileSize(r.size());
        sf.setContentType(r.contentType());
        sf.setExt(r.ext());
        fileMapper.insert(sf);
        return sf;
    }
}
