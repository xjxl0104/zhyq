package com.zhyq.park.oa.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.oa.entity.OaSeal;
import com.zhyq.park.oa.entity.SealPlacement;
import com.zhyq.park.oa.mapper.OaSealMapper;
import com.zhyq.park.oa.mapper.SealPlacementMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;

@Tag(name = "办公管理-印章管理")
@RestController
@RequestMapping("/oa/seal")
@RequiredArgsConstructor
public class OaSealController {

    private static final String DEFAULT_SEAL_RESOURCE = "seals/dipark-property-seal.png";
    private final OaSealMapper sealMapper;
    private final SealPlacementMapper placementMapper;

    @Operation(summary = "查询电子印章列表")
    @GetMapping("/list")
    public Result<List<OaSeal>> list(@RequestParam(required = false) Boolean onlyEnabled) {
        ensureDefaultSealSeeded();
        LambdaQueryWrapper<OaSeal> qw = new LambdaQueryWrapper<>();
        qw.eq(Boolean.TRUE.equals(onlyEnabled), OaSeal::getStatus, 1)
          .orderByDesc(OaSeal::getIsDefault)
          .orderByAsc(OaSeal::getId);
        return Result.ok(sealMapper.selectList(qw));
    }

    @Operation(summary = "新增电子印章")
    @PostMapping
    public Result<OaSeal> add(@RequestBody OaSeal seal) {
        validateSeal(seal);
        if (seal.getDefaultWidthMm() == null || seal.getDefaultWidthMm() < 15) {
            seal.setDefaultWidthMm(42);
        }
        if (seal.getStatus() == null) {
            seal.setStatus(1);
        }
        if (seal.getIsDefault() == null) {
            seal.setIsDefault(0);
        }
        if (seal.getIsDefault() == 1) {
            clearDefaultFlag();
        }
        sealMapper.insert(seal);
        return Result.ok(seal);
    }

    @Operation(summary = "修改电子印章")
    @PutMapping
    public Result<OaSeal> update(@RequestBody OaSeal seal) {
        if (seal.getId() == null) {
            throw new BizException("印章ID不能为空");
        }
        OaSeal existing = sealMapper.selectById(seal.getId());
        if (existing == null) {
            throw new BizException("印章不存在");
        }
        if (!StringUtils.hasText(seal.getImageData())) {
            seal.setImageData(existing.getImageData());
        }
        validateSeal(seal);
        if (seal.getDefaultWidthMm() == null || seal.getDefaultWidthMm() < 15) {
            seal.setDefaultWidthMm(existing.getDefaultWidthMm() != null ? existing.getDefaultWidthMm() : 42);
        }
        if (Integer.valueOf(1).equals(seal.getIsDefault())) {
            clearDefaultFlag();
        }
        sealMapper.updateById(seal);
        return Result.ok(sealMapper.selectById(seal.getId()));
    }

    @Operation(summary = "设为默认印章")
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        OaSeal seal = sealMapper.selectById(id);
        if (seal == null) {
            throw new BizException("印章不存在");
        }
        clearDefaultFlag();
        seal.setIsDefault(1);
        seal.setStatus(1);
        sealMapper.updateById(seal);
        return Result.ok();
    }

    @Operation(summary = "删除电子印章")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sealMapper.deleteById(id);
        LambdaUpdateWrapper<SealPlacement> uw = new LambdaUpdateWrapper<>();
        uw.eq(SealPlacement::getSealId, id)
          .set(SealPlacement::getSealId, null)
          .set(SealPlacement::getEnabled, 0);
        placementMapper.update(null, uw);
        return Result.ok();
    }

    @Operation(summary = "获取模板电子印章排版配置")
    @GetMapping("/placement/{templateKey}")
    public Result<SealPlacement> getPlacement(@PathVariable String templateKey) {
        ensureDefaultSealSeeded();
        SealPlacement p = placementMapper.selectOne(new LambdaQueryWrapper<SealPlacement>()
                .eq(SealPlacement::getTemplateKey, templateKey)
                .last("LIMIT 1"));
        if (p == null) {
            p = defaultPlacement(templateKey);
        }
        hydratePlacement(p);
        return Result.ok(p);
    }

    @Operation(summary = "保存模板电子印章排版配置")
    @PutMapping("/placement/{templateKey}")
    public Result<SealPlacement> savePlacement(@PathVariable String templateKey,
                                               @RequestBody SealPlacement req) {
        if (!StringUtils.hasText(templateKey)) {
            throw new BizException("模板标识不能为空");
        }
        SealPlacement existing = placementMapper.selectOne(new LambdaQueryWrapper<SealPlacement>()
                .eq(SealPlacement::getTemplateKey, templateKey)
                .last("LIMIT 1"));
        SealPlacement target = existing != null ? existing : defaultPlacement(templateKey);
        target.setTemplateKey(templateKey);
        target.setSealId(req.getSealId());
        target.setEnabled(req.getEnabled() != null && req.getEnabled() == 1 && req.getSealId() != null ? 1 : 0);
        target.setPosX(clamp(req.getPosX(), BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("74.00")));
        target.setPosY(clamp(req.getPosY(), BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("76.00")));
        target.setScale(clamp(req.getScale(), new BigDecimal("0.40"), new BigDecimal("2.50"), new BigDecimal("1.00")));
        target.setRotation(clamp(req.getRotation(), new BigDecimal("-45.00"), new BigDecimal("45.00"), BigDecimal.ZERO));
        target.setOpacity(clamp(req.getOpacity(), new BigDecimal("0.20"), BigDecimal.ONE, new BigDecimal("0.92")));

        if (existing == null) {
            placementMapper.insert(target);
        } else {
            placementMapper.updateById(target);
        }
        hydratePlacement(target);
        return Result.ok(target);
    }

    private void validateSeal(OaSeal seal) {
        if (!StringUtils.hasText(seal.getName())) {
            throw new BizException("印章名称不能为空");
        }
        if (!StringUtils.hasText(seal.getImageData()) || !seal.getImageData().startsWith("data:image/")) {
            throw new BizException("请上传有效的透明底印章图片");
        }
        if (seal.getImageData().length() > 4 * 1024 * 1024) {
            throw new BizException("印章图片过大，请控制在 3MB 以内");
        }
    }

    private void clearDefaultFlag() {
        LambdaUpdateWrapper<OaSeal> uw = new LambdaUpdateWrapper<>();
        uw.eq(OaSeal::getIsDefault, 1).set(OaSeal::getIsDefault, 0);
        sealMapper.update(null, uw);
    }

    private SealPlacement defaultPlacement(String templateKey) {
        SealPlacement p = new SealPlacement();
        p.setTemplateKey(templateKey);
        p.setEnabled(0);
        p.setPosX(new BigDecimal("74.00"));
        p.setPosY(new BigDecimal("76.00"));
        p.setScale(new BigDecimal("1.00"));
        p.setRotation(BigDecimal.ZERO);
        p.setOpacity(new BigDecimal("0.92"));
        return p;
    }

    private void hydratePlacement(SealPlacement p) {
        if (p == null || p.getSealId() == null) return;
        OaSeal seal = sealMapper.selectById(p.getSealId());
        if (seal != null && Integer.valueOf(1).equals(seal.getStatus())) {
            p.setSealName(seal.getName());
            p.setSealImageData(seal.getImageData());
            p.setDefaultWidthMm(seal.getDefaultWidthMm());
        } else {
            p.setEnabled(0);
        }
    }

    private static BigDecimal clamp(BigDecimal val, BigDecimal min, BigDecimal max, BigDecimal fallback) {
        if (val == null) return fallback;
        if (val.compareTo(min) < 0) return min;
        if (val.compareTo(max) > 0) return max;
        return val;
    }

    private synchronized void ensureDefaultSealSeeded() {
        Long count = sealMapper.selectCount(new LambdaQueryWrapper<>());
        if (count != null && count > 0) {
            return;
        }
        try {
            ClassPathResource res = new ClassPathResource(DEFAULT_SEAL_RESOURCE);
            if (!res.exists()) return;
            try (InputStream in = res.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                OaSeal seal = new OaSeal();
                seal.setName("DIPARK数智云仓产业园物业管理服务部章");
                seal.setCategory("物业专用章");
                seal.setOrgName("DIPARK数智云仓产业园物业管理服务部");
                seal.setImageData("data:image/png;base64," + Base64.getEncoder().encodeToString(bytes));
                seal.setDefaultWidthMm(44);
                seal.setIsDefault(1);
                seal.setStatus(1);
                seal.setRemark("系统预置透明底电子印章，可用于缴费通知单、装修报批及动火作业审批表");
                sealMapper.insert(seal);
            }
        } catch (Exception ignored) {
            // Classpath resource optional in stripped test environments
        }
    }
}
