package com.zhyq.park.archive.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 资料自动归类:按「关键词规则」给子类、文档类型、密级打分,再用 ArcNamer 生成规范化名称。
 * 规则来自数据库(arc_rule),在系统里可维护;本类是纯函数,不访问数据库,便于单测。
 */
public final class ArcClassifier {
    private ArcClassifier() {}

    public static final String UNSORTED = "99.01";
    /** 命中分数低于此值视为「没把握」,归入待归类 */
    private static final int MIN_SCORE = 6;
    private static final int NAME_FACTOR = 3;

    public record Cat(String code, String name, int defaultSecret) {}

    /** scope:1 文件名与来源路径,2 文件正文 */
    public record Rule(String keyword, int scope, String categoryCode, String docType, Integer secretLevel, int weight) {}

    public record Input(String fileName, String sourcePath, String text, LocalDate fallbackDate) {}

    public record Suggestion(String name, String categoryCode, String docType, int secretLevel,
                             LocalDate docDate, String period, String versionLabel,
                             int confidence, List<String> reasons, boolean nameJunk) {}

    public static Suggestion classify(Input in, List<Cat> cats, List<Rule> rules) {
        String fileName = in.fileName() == null ? "" : in.fileName();
        String ext = extOf(fileName);
        String base = ArcNamer.baseName(fileName);
        String nameText = ArcNamer.norm(base).toLowerCase(Locale.ROOT);
        String pathText = ArcNamer.norm(in.sourcePath()).replace('\\', '/').toLowerCase(Locale.ROOT);
        String content = ArcNamer.norm(in.text()).toLowerCase(Locale.ROOT);

        Map<String, Integer> catScore = new HashMap<>();
        Map<String, Integer> typeScore = new HashMap<>();
        Map<String, List<String>> why = new HashMap<>();
        Map<String, List<Integer>> pathHits = new HashMap<>();
        Integer secretHit = null;

        for (Rule r : rules) {
            String kw = r.keyword() == null ? "" : ArcNamer.norm(r.keyword()).toLowerCase(Locale.ROOT).trim();
            if (kw.isEmpty()) continue;
            int hit = 0;
            String where = null;
            if (r.scope() == 2) {
                int c = count(content, kw);
                if (c > 0) { hit = r.weight() + (c >= 3 ? 1 : 0); where = "正文"; }
            } else if (nameText.contains(kw)) {
                hit = r.weight() * NAME_FACTOR; where = "文件名";
            } else if (pathText.contains(kw)) {
                hit = r.weight(); where = "所在文件夹";
            }
            if (hit == 0) continue;
            boolean fromPath = "所在文件夹".equals(where);
            if (r.categoryCode() != null && !r.categoryCode().isBlank()) {
                if (fromPath) pathHits.computeIfAbsent(r.categoryCode(), k -> new ArrayList<>()).add(hit);
                else catScore.merge(r.categoryCode(), hit, Integer::sum);
                why.computeIfAbsent(r.categoryCode(), k -> new ArrayList<>()).add(where + "含「" + r.keyword() + "」");
            }
            // 文档类型只看文件名/正文,不看所在文件夹(文件夹名里的「制度」「标准」不代表这份文件的形式)
            if (r.docType() != null && !r.docType().isBlank() && !fromPath) typeScore.merge(r.docType(), hit, Integer::sum);
            if (r.secretLevel() != null) secretHit = secretHit == null ? r.secretLevel() : Math.max(secretHit, r.secretLevel());
        }
        // 文件夹提示可信但不应压过文件名:同一子类的多条路径命中,取最高分 + 其余的 1/3
        for (Map.Entry<String, List<Integer>> e : pathHits.entrySet()) {
            int max = e.getValue().stream().mapToInt(Integer::intValue).max().orElse(0);
            int rest = e.getValue().stream().mapToInt(Integer::intValue).sum() - max;
            catScore.merge(e.getKey(), max + rest / 3, Integer::sum);
        }
        if ("dwg".equals(ext) || "dxf".equals(ext)) {
            catScore.merge("07.02", 20, Integer::sum);
            typeScore.merge("图纸图片", 20, Integer::sum);
        }

        Map<String, Cat> catByCode = new HashMap<>();
        for (Cat c : cats) catByCode.put(c.code(), c);
        String best = null;
        int top = 0, second = 0;
        for (Map.Entry<String, Integer> e : catScore.entrySet()) {
            if (!catByCode.containsKey(e.getKey())) continue;
            int v = e.getValue();
            if (v > top || (v == top && best != null && e.getKey().compareTo(best) < 0)) {
                second = top; top = v; best = e.getKey();
            } else if (v > second) second = v;
        }
        boolean sorted = best != null && top >= MIN_SCORE;
        String category = sorted ? best : UNSORTED;
        int confidence = sorted ? (int) Math.round(100.0 * Math.min(1.0, top / 16.0) * (top - second * 0.6) / top) : 0;

        String docType = pickKey(typeScore);
        if (docType == null) docType = typeByExt(ext);
        Cat cat = catByCode.get(category);
        int secret = secretHit != null ? secretHit : cat != null ? cat.defaultSecret() : 2;

        ArcNamer.Parts parts = ArcNamer.analyze(base, in.fallbackDate());
        String period = parts.period() != null ? parts.period() : ArcNamer.periodFromPath(in.sourcePath(), in.fallbackDate());
        LocalDate docDate = parts.docDate() != null ? parts.docDate() : ArcNamer.dateFromText(in.text());
        String core = parts.core();
        boolean junk = parts.junk() || core.isBlank();
        if (junk) {
            core = ArcNamer.folderCore(in.sourcePath(), in.fallbackDate(), 2);
            if (core.isBlank() && cat != null) core = cat.name();
        }
        // 主题太短(如「证明」「第三季度」)时,前面加上所在文件夹名,避免一堆含义不清的同名文件
        if (!junk && cjkCount(core) <= 2) {
            String folder = ArcNamer.folderCore(in.sourcePath(), in.fallbackDate());
            if (!folder.isBlank()) core = (folder.length() > 24 ? folder.substring(0, 24) : folder) + "-" + core;
        }
        String name = ArcNamer.compose(core, period, parts.version());
        if (name.isBlank()) name = ArcNamer.compose(cat != null ? cat.name() : "资料", period, null);

        List<String> reasons = new ArrayList<>(why.getOrDefault(category, List.of()));
        if (reasons.size() > 4) reasons = new ArrayList<>(reasons.subList(0, 4));
        if (!sorted) reasons.add("没有足够把握的关键词,请手动选择分类");
        if (junk) reasons.add("原文件名没有含义,名称取自所在文件夹,请确认");
        return new Suggestion(name, category, docType, secret, docDate, period, parts.version(),
            confidence, List.copyOf(reasons), junk);
    }

    private static String pickKey(Map<String, Integer> scores) {
        String best = null;
        int top = 0;
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            if (e.getValue() > top || (e.getValue() == top && best != null && e.getKey().compareTo(best) < 0)) {
                top = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    private static int cjkCount(String s) {
        int n = 0;
        for (char ch : s.toCharArray()) if (ch >= 0x4e00 && ch <= 0x9fa5) n++;
        return n;
    }

    static String typeByExt(String ext) {
        return switch (ext) {
            case "dwg", "dxf", "jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff" -> "图纸图片";
            case "xls", "xlsx", "csv" -> "台账清单";
            default -> "其他";
        };
    }

    private static int count(String text, String kw) {
        if (text.isEmpty()) return 0;
        int n = 0, i = 0;
        while ((i = text.indexOf(kw, i)) >= 0 && n < 3) { n++; i += kw.length(); }
        return n;
    }

    private static String extOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
