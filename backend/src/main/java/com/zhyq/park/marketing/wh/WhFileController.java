package com.zhyq.park.marketing.wh;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.file.service.FileStorageService;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.service.MktWarehouseFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/wh/v1/files")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WH')")
public class WhFileController {
    private final MktWarehouseMapper warehouses;
    private final MktWarehouseFileService documents;
    private final SysFileMapper files;
    private final FileStorageService storage;

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(documents.upload(file, WhAuthContext.requireWarehouse(warehouses).getId()));
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        Long wid = WhAuthContext.requireWarehouse(warehouses).getId();
        return Result.ok(files.selectList(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getBizType, MktWarehouseFileService.BIZ_TYPE).eq(SysFile::getBizId, wid)
                .orderByDesc(SysFile::getId).last("LIMIT 100")).stream().map(MktWarehouseFileService::view).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        SysFile f = documents.requireOwned(id, WhAuthContext.requireWarehouse(warehouses).getId());
        MediaType type;
        try { type = MediaType.parseMediaType(f.getContentType()); }
        catch (Exception e) { type = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff").contentType(type)
                .body(new FileSystemResource(storage.resolveExisting(f.getStorePath())));
    }
}
