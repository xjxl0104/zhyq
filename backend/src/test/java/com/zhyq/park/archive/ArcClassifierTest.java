package com.zhyq.park.archive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.archive.service.ArcClassifier;
import com.zhyq.park.archive.service.ArcClassifier.Cat;
import com.zhyq.park.archive.service.ArcClassifier.Rule;
import com.zhyq.park.archive.service.ArcClassifier.Suggestion;
import com.zhyq.park.archive.service.ArcTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 自动归类/命名规则的单测;规则取自 gen_seed.py 生成的 taxonomy.json(与上线种子一致) */
class ArcClassifierTest {
    private final List<Cat> cats = new ArrayList<>();
    private final List<Rule> rules = new ArrayList<>();

    ArcClassifierTest() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/archive/taxonomy.json")) {
            JsonNode root = new ObjectMapper().readTree(in);
            for (JsonNode c : root.get("categories")) cats.add(new Cat(c.get("code").asText(), c.get("name").asText(), c.get("secret").asInt()));
            for (JsonNode r : root.get("rules")) {
                rules.add(new Rule(r.get("keyword").asText(), r.get("scope").asInt(),
                    text(r, "category"), text(r, "docType"),
                    r.get("secret").isNull() ? null : r.get("secret").asInt(), r.get("weight").asInt()));
            }
        }
    }

    private static String text(JsonNode n, String f) { return n.get(f).isNull() ? null : n.get(f).asText(); }

    private Suggestion run(String file, String path, String text) {
        return ArcClassifier.classify(new ArcClassifier.Input(file, path, text, LocalDate.of(2026, 10, 1)), cats, rules);
    }

    @Test
    void contractWithVersionAndDate() {
        Suggestion s = run("DIPARK数智云仓产业园物业管理合同补充协议(法务审定版）.docx", "10、物业相关合同/1、物业服务合同（五矿）", "");
        assertEquals("03.01", s.categoryCode());
        assertEquals("合同协议", s.docType());
        assertEquals("法务审定版", s.versionLabel());
        assertEquals("DIPARK数智云仓产业园物业管理合同补充协议-法务审定版", s.name());
    }

    @Test
    void noticeKeepsPeriodAndTenant() {
        Suggestion s = run("1、租户（中印物流）纸质版缴费通知单（租金物业费）.pdf", "3、租金、水电费缴费/1、租金及管理费/1、中印物流/1、2026.10", "");
        assertEquals("05.01", s.categoryCode());
        assertEquals("2026年10月", s.period());
        assertTrue(s.name().startsWith("租户(中印物流)纸质版缴费通知单(租金物业费)"), s.name());
    }

    @Test
    void junkNameUsesFolderAndHighSecretForIdCard() {
        Suggestion s = run("微信图片_20260823130437_54_324.jpg", "3、租金、水电费缴费/2、水电费/6-7月电费汇总/中印（1层C区）/7月/七月底抄表照片", "");
        assertTrue(s.nameJunk());
        assertTrue(s.name().contains("七月底抄表照片"), s.name());
        Suggestion id = run("身份证_杨总.pdf", "", "");
        assertEquals(3, id.secretLevel());
    }

    @Test
    void numberedWechatImageIsJunk() {
        Suggestion s = run("1、微信图片_20260823130437_54_324.jpg", "", "");
        assertTrue(s.nameJunk(), s.name());
        Suggestion c = run("公众责任险保单-昌记-2（正式保单） - 副本(1).pdf", "", "");
        assertEquals("公众责任险保单-昌记-2(正式保单)", c.name());
    }

    @Test
    void contentHelpsWhenNameIsVague() {
        Suggestion s = run("扫描件.pdf", "", "被保险人 广州某公司 保险期间 2026年1月1日 至 2026年12月31日 保险费 保险单");
        assertEquals("03.04", s.categoryCode());
    }

    @Test
    void nothingMatchedFallsToUnsorted() {
        Suggestion s = run("abc.xyz", "", "");
        assertEquals("99.01", s.categoryCode());
        assertEquals(0, s.confidence());
    }

    @Test
    @EnabledIfSystemProperty(named = "arc.preview.dir", matches = ".+")
    void previewFolder() throws Exception {
        Path root = Paths.get(System.getProperty("arc.preview.dir"));
        Path out = Paths.get(System.getProperty("arc.preview.out", "arc-preview.tsv"));
        List<String> lines = new ArrayList<>();
        try (Stream<Path> w = Files.walk(root)) {
            for (Path p : (Iterable<Path>) w.filter(Files::isRegularFile).sorted()::iterator) {
                String rel = root.relativize(p).toString().replace('\\', '/');
                String fn = p.getFileName().toString();
                String ext = fn.contains(".") ? fn.substring(fn.lastIndexOf('.') + 1).toLowerCase() : "";
                String dir = rel.contains("/") ? rel.substring(0, rel.lastIndexOf('/')) : "";
                String text = ArcTextExtractor.extract(p, ext);
                Suggestion s = ArcClassifier.classify(new ArcClassifier.Input(fn, dir, text,
                    LocalDate.ofInstant(Files.getLastModifiedTime(p).toInstant(), java.time.ZoneId.systemDefault())), cats, rules);
                lines.add(String.join("\t", rel, s.name(), s.categoryCode(), s.docType(), String.valueOf(s.secretLevel()),
                    String.valueOf(s.confidence()), String.valueOf(s.docDate()), String.valueOf(text.length()), String.join("；", s.reasons()), String.valueOf(s.period()), String.valueOf(s.versionLabel())));
            }
        }
        Files.write(out, lines);
    }
}
