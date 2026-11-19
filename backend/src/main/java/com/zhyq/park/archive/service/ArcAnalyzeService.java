package com.zhyq.park.archive.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.archive.entity.ArcCategory;
import com.zhyq.park.archive.entity.ArcDoc;
import com.zhyq.park.archive.entity.ArcRule;
import com.zhyq.park.archive.mapper.ArcCategoryMapper;
import com.zhyq.park.archive.mapper.ArcDocMapper;
import com.zhyq.park.archive.mapper.ArcRuleMapper;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** 资料自动归类:读取已上传文件 → 提取正文 → 按规则给出名称/分类/密级建议 */
@Service
@RequiredArgsConstructor
public class ArcAnalyzeService {

    public static final String BIZ_TYPE = "archive_doc";
    public static final List<String> DOC_TYPES = List.of("制度规程", "合同协议", "证照证明", "台账清单",
        "记录表单", "通知账单", "方案报告", "图纸图片", "票据凭证", "函件申请", "其他");
    private static final int SUMMARY_LEN = 400;

    private final ArcCategoryMapper categoryMapper;
    private final ArcRuleMapper ruleMapper;
    private final ArcDocMapper docMapper;
    private final SysFileMapper fileMapper;
    private final FileStorageService storage;

    /** 分析结果:建议值 + 文件信息 + 查重提示 */
    public record Analysis(Long fileId, String originalName, String ext, Long fileSize, String fileHash,
                           String name, String categoryCode, String categoryName, String docType, Integer secretLevel,
                           LocalDate docDate, String period, String versionLabel, Integer confidence,
                           List<String> reasons, boolean nameJunk, String summary, Duplicate duplicate) {}

    public record Duplicate(Long id, String archiveNo, String name) {}

    public List<ArcClassifier.Cat> categories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<ArcCategory>().eq(ArcCategory::getEnabled, 1)).stream()
            .map(c -> new ArcClassifier.Cat(c.getCode(), c.getName(), c.getDefaultSecret() == null ? 2 : c.getDefaultSecret()))
            .toList();
    }

    public List<ArcClassifier.Rule> rules() {
        return ruleMapper.selectList(new LambdaQueryWrapper<ArcRule>().eq(ArcRule::getEnabled, 1)).stream()
            .map(r -> new ArcClassifier.Rule(r.getKeyword(), r.getScope() == null ? 1 : r.getScope(), r.getCategoryCode(),
                r.getDocType(), r.getSecretLevel(), r.getWeight() == null ? 6 : r.getWeight()))
            .toList();
    }

    /** 仅凭文件名/路径试算(规则测试用,不读文件) */
    public ArcClassifier.Suggestion suggest(String fileName, String sourcePath, String text, LocalDate fallback) {
        return ArcClassifier.classify(new ArcClassifier.Input(fileName, sourcePath, text, fallback), categories(), rules());
    }

    public Analysis analyze(Long fileId, String sourcePath, Long lastModifiedMillis) {
        SysFile file = fileId == null ? null : fileMapper.selectById(fileId);
        if (file == null) throw new BizException("文件不存在,请重新上传");
        assertUsable(file);
        Path path = storage.resolveExisting(file.getStorePath());
        String text = ArcTextExtractor.extract(path, file.getExt());
        String hash;
        try {
            hash = DigestUtil.sha256Hex(path.toFile());
        } catch (Exception e) {
            hash = null;
        }
        LocalDate fallback = lastModifiedMillis == null || lastModifiedMillis <= 0 ? LocalDate.now()
            : LocalDate.ofInstant(Instant.ofEpochMilli(lastModifiedMillis), ZoneId.systemDefault());
        List<ArcClassifier.Cat> cats = categories();
        ArcClassifier.Suggestion s = ArcClassifier.classify(
            new ArcClassifier.Input(file.getOriginalName(), sourcePath, text, fallback), cats, rules());
        String catName = cats.stream().filter(c -> c.code().equals(s.categoryCode())).map(ArcClassifier.Cat::name).findFirst().orElse("");
        Duplicate dup = null;
        if (hash != null) {
            ArcDoc same = docMapper.selectOne(new LambdaQueryWrapper<ArcDoc>().eq(ArcDoc::getFileHash, hash).last("LIMIT 1"));
            if (same != null) dup = new Duplicate(same.getId(), same.getArchiveNo(), same.getName());
        }
        String summary = text.length() > SUMMARY_LEN ? text.substring(0, SUMMARY_LEN) : text;
        return new Analysis(file.getId(), file.getOriginalName(), file.getExt(), file.getFileSize(), hash,
            s.name(), s.categoryCode(), catName, s.docType(), s.secretLevel(), s.docDate(), s.period(), s.versionLabel(),
            s.confidence(), s.reasons(), s.nameJunk(), summary, dup);
    }

    /** 文件必须是当前用户自己上传、尚未关联到别的业务的(或本模块的)附件 */
    public void assertUsable(SysFile file) {
        if (file.getBizId() != null) throw new BizException("该文件已关联到其他资料");
        if (file.getBizType() != null && !file.getBizType().isBlank() && !BIZ_TYPE.equals(file.getBizType())) {
            throw new BizException("该文件不是资料管理上传的文件");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_admin".equals(a.getAuthority()));
        if (!admin && !MyMetaObjectHandler.currentOperator().equals(file.getCreateBy())) {
            throw new BizException(403, "只能处理自己上传的文件");
        }
    }
}
