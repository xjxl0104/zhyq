package com.zhyq.park.file.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.file.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @Operation(summary = "删除附件")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        SysFile f = fileMapper.selectById(id);
        if (f != null) {
            fileMapper.deleteById(id);          // 逻辑删除
            storageService.deletePhysical(f.getStorePath());
        }
        return Result.ok();
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
