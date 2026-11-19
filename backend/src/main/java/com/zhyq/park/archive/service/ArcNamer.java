package com.zhyq.park.archive.service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规范化命名:从原文件名里拆出 主题 / 期间 / 版本 / 日期,并去掉「微信图片」「副本」「(1)」「1、」等杂乱字样。
 * 命名格式:主题-期间-版本(识别不到的部分省略,不编造)。纯函数,无外部依赖,可单测。
 */
public final class ArcNamer {
    private ArcNamer() {}

    /** 拆解结果:core 为空表示原名无意义(哈希、微信图片等) */
    public record Parts(String core, String period, String version, LocalDate docDate, boolean junk) {}

    private static final Pattern JUNK = Pattern.compile(
        "^(?:[0-9a-fA-F]{12,}|\\d{6,}|(?:微信图片|wechat ?image|img|dsc|mmexport|screenshot|截图|image|photo|pic)[\\d_\\-\\s()]*)$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern ENUM_DOTTED = Pattern.compile("^\\d{1,2}\\.\\d{1,2}(?=[\\u4e00-\\u9fa5A-Za-z])(?![号月日])");
    private static final Pattern ENUM_SIMPLE = Pattern.compile("^\\(?\\d{1,2}\\)?\\s*(?:、|[.．\\-_ ]+(?!\\d))\\s*");
    private static final Pattern COPY_MARK = Pattern.compile("[\\s_-]*(?:副本|复制|copy)\\s*\\d*", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAIL_NUM = Pattern.compile("(?:\\s*\\(\\d{1,2}\\))+");
    private static final Pattern TRAIL_CODE = Pattern.compile("(?<=[\\u4e00-\\u9fa5)])\\s*\\d{1,2}-\\d{1,2}$");
    private static final Pattern BRACKET_DATE = Pattern.compile("【\\s*(20\\d{2})(\\d{2})(\\d{2})\\s*】");
    private static final Pattern BRACKET_FIX = Pattern.compile("[【(]\\s*(?:修改后最终|修改后|修|改)\\s*[】)]");
    private static final Pattern VERSION = Pattern.compile(
        "(法务审定版|审定版|盖章版|正式版|最终版|终稿|定稿|二稿|三稿|四稿|初稿|草稿|修订版|扫描版|[Vv]\\d+(?:\\.\\d+)*版?)");
    private static final Pattern DAY_RANGE = Pattern.compile(
        "\\(?\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[号日]?\\s*[~至]\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[号日]?\\s*\\)?");
    private static final Pattern MONTH_PAIR = Pattern.compile("(?<![\\d.])(\\d{1,2})\\s*月\\s*(?:和|及|、|至|~)\\s*(\\d{1,2})\\s*月份?");
    private static final Pattern RANGE =Pattern.compile("(20\\d{2})\\s*年\\s*(\\d{1,2})\\s*[~至]\\s*(\\d{1,2})\\s*月");
    private static final Pattern FULL = Pattern.compile(
        "(?<!\\d)(20\\d{2})\\s*[年.\\-/_]?\\s*(\\d{1,2})\\s*[月.\\-/_]\\s*(\\d{1,2})\\s*日?(?!\\d)");
    private static final Pattern FULL8 = Pattern.compile("(?<!\\d)(20\\d{2})(\\d{2})(\\d{2})(?!\\d)");
    private static final Pattern YM = Pattern.compile("(?<!\\d)(20\\d{2})\\s*[年.\\-/_]\\s*(\\d{1,2})\\s*月?(?!\\d)");
    private static final Pattern MONTH = Pattern.compile("(?<![\\d.\\-])(\\d{1,2})\\s*月份?(?![初中底末上下])");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(20\\d{2})\\s*年?(?!\\d)");
    private static final Pattern CONTENT_DATE = Pattern.compile("(20\\d{2})\\s*[年.\\-/]\\s*(\\d{1,2})\\s*[月.\\-/]\\s*(\\d{1,2})\\s*日?");
    private static final Pattern ILLEGAL = Pattern.compile("[\\\\/:*?\"<>|]");

    public static String baseName(String fileName) {
        if (fileName == null) return "";
        String n = fileName.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    public static String norm(String s) {
        return s == null ? "" : Normalizer.normalize(s, Normalizer.Form.NFKC);
    }

    /** 拆解一个文件名(不含扩展名);fallback 用于只写了「7月」时推断年份,可为 null */
    public static Parts analyze(String base, LocalDate fallback) {
        String s = norm(base).trim();
        s = ENUM_DOTTED.matcher(s).replaceFirst("");
        s = ENUM_SIMPLE.matcher(s).replaceFirst("");
        s = COPY_MARK.matcher(s).replaceAll("");
        s = TRAIL_NUM.matcher(s).replaceAll("");
        s = TRAIL_CODE.matcher(s).replaceAll("").trim();
        if (JUNK.matcher(s).matches()) return new Parts("", null, null, null, true);

        LocalDate docDate = null;
        String version = null;
        Matcher m = BRACKET_DATE.matcher(s);
        if (m.find()) {
            docDate = date(m.group(1), m.group(2), m.group(3));
            s = m.replaceFirst(" ");
        }
        m = BRACKET_FIX.matcher(s);
        if (m.find()) {
            version = "修订版";
            s = m.replaceAll(" ");
        }
        s = s.replace("【", " ").replace("】", " ");

        m = VERSION.matcher(s);
        if (m.find()) {
            if (version == null) version = m.group(1).replaceFirst("^v", "V");
            s = removeToken(s, m.start(), m.end());
        }

        String period = null;
        int[] span = null;
        int ry = docDate != null ? docDate.getYear() : fallback != null ? fallback.getYear() : 0;
        String yp = ry > 0 ? ry + "年" : "";
        m = DAY_RANGE.matcher(s);
        if (m.find()) {
            period = yp + Integer.parseInt(m.group(1)) + "月" + Integer.parseInt(m.group(2)) + "日~"
                + Integer.parseInt(m.group(3)) + "月" + Integer.parseInt(m.group(4)) + "日";
            span = new int[]{m.start(), m.end()};
        }
        if (period == null) {
            m = MONTH_PAIR.matcher(s);
            if (m.find()) {
                period = yp + Integer.parseInt(m.group(1)) + "~" + Integer.parseInt(m.group(2)) + "月";
                span = new int[]{m.start(), m.end()};
            }
        }
        m = RANGE.matcher(s);
        if (period == null && m.find()) {
            period = m.group(1) + "年" + Integer.parseInt(m.group(2)) + "~" + Integer.parseInt(m.group(3)) + "月";
            span = new int[]{m.start(), m.end()};
        }
        if (period == null) {
            m = FULL.matcher(s);
            while (m.find()) {
                LocalDate d = date(m.group(1), m.group(2), m.group(3));
                if (d == null) continue;
                if (docDate == null) docDate = d;
                period = fmtDay(d);
                span = new int[]{m.start(), m.end()};
                break;
            }
        }
        if (period == null) {
            m = FULL8.matcher(s);
            while (m.find()) {
                LocalDate d = date(m.group(1), m.group(2), m.group(3));
                if (d == null) continue;
                if (docDate == null) docDate = d;
                period = fmtDay(d);
                span = new int[]{m.start(), m.end()};
                break;
            }
        }
        if (period == null) {
            m = YM.matcher(s);
            if (m.find() && validMonth(m.group(2))) {
                period = m.group(1) + "年" + Integer.parseInt(m.group(2)) + "月";
                span = new int[]{m.start(), m.end()};
            }
        }
        if (period == null) {
            m = MONTH.matcher(s);
            if (m.find() && validMonth(m.group(1))) {
                int year = docDate != null ? docDate.getYear() : fallback != null ? fallback.getYear() : 0;
                period = (year > 0 ? year + "年" : "") + Integer.parseInt(m.group(1)) + "月";
                span = new int[]{m.start(), m.end()};
            }
        }
        if (period == null) {
            m = YEAR.matcher(s);
            if (m.find()) {
                period = m.group(1);
                span = new int[]{m.start(), m.end()};
            }
        }
        if (span != null) s = removeToken(s, span[0], span[1]);
        return new Parts(tidy(s), period, version, docDate, false);
    }

    /** 路径里最近一段能说明期间的文件夹(如「2026.10」「7月」),找不到返回 null */
    public static String periodFromPath(String sourcePath, LocalDate fallback) {
        if (sourcePath == null) return null;
        String[] segs = norm(sourcePath).replace('\\', '/').split("/");
        for (int i = segs.length - 1; i >= 0 && i >= segs.length - 3; i--) {
            Parts p = analyze(segs[i], fallback);
            if (p.period() != null) return p.period();
        }
        return null;
    }

    /** 路径里最近一段有意义的文件夹名(用于哈希/微信图片这类无意义文件名),找不到返回 "" */
    public static String folderCore(String sourcePath, LocalDate fallback) {
        return folderCore(sourcePath, fallback, 1);
    }

    /** 取最近 levels 层有意义的文件夹名,按路径顺序用「-」连接(如「云山(2层A~C区)-7月初」) */
    public static String folderCore(String sourcePath, LocalDate fallback, int levels) {
        if (sourcePath == null) return "";
        String[] segs = norm(sourcePath).replace('\\', '/').split("/");
        java.util.LinkedList<String> picked = new java.util.LinkedList<>();
        for (int i = segs.length - 1; i >= 0 && picked.size() < levels; i--) {
            Parts p = analyze(segs[i], fallback);
            if (!p.junk() && cjkCount(p.core()) >= 2 && !picked.contains(p.core())) picked.addFirst(p.core());
        }
        return String.join("-", picked);
    }

    /** 正文里的第一个完整日期,没有返回 null */
    public static LocalDate dateFromText(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = CONTENT_DATE.matcher(norm(text.length() > 3000 ? text.substring(0, 3000) : text));
        while (m.find()) {
            LocalDate d = date(m.group(1), m.group(2), m.group(3));
            if (d != null) return d;
        }
        return null;
    }

    /** 拼出最终名称:主题-期间-版本;已在主题里出现的不重复追加 */
    public static String compose(String core, String period, String version) {
        StringBuilder sb = new StringBuilder(core == null ? "" : core);
        appendPart(sb, period);
        appendPart(sb, version);
        String name = ILLEGAL.matcher(sb.toString()).replaceAll("-");
        name = name.replaceAll("-{2,}", "-").replaceAll("^[-\\s]+|[-\\s]+$", "");
        return name.length() > 100 ? name.substring(0, 100) : name;
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) return;
        if (sb.indexOf(part) >= 0) return;
        // 主题里已写了「7月」时,不再追加「2026年7月」
        String shortPart = part.replaceFirst("^20\\d{2}年(?=\\d)", "");
        if (!shortPart.equals(part) && sb.indexOf(shortPart) >= 0) return;
        if (sb.length() > 0) sb.append('-');
        sb.append(part);
    }

    private static String removeToken(String s, int start, int end) {
        // 连同两侧括号/连接符一起去掉,避免留下「()」「--」
        int a = start, b = end;
        while (a > 0 && "([（ _-".indexOf(s.charAt(a - 1)) >= 0) a--;
        while (b < s.length() && ")] ）_-".indexOf(s.charAt(b)) >= 0) b++;
        String left = s.substring(0, a), right = s.substring(b);
        // 保留一个分隔,以免把两个词粘在一起
        return left + (left.isEmpty() || right.isEmpty() ? "" : " ") + right;
    }

    private static String tidy(String s) {
        String t = s.replaceAll("\\(\\s*\\)", "").replaceAll("\\s+", " ").trim();
        t = t.replaceAll("^[\\s,，、.·_\\-]+|[\\s,，、.·_\\-]+$", "");
        // 空白统一成连字符(两侧均为英文字母/数字时保留空格,如 DI PARK)
        t = t.replaceAll("(?<![A-Za-z0-9]) | (?![A-Za-z0-9])", "-");
        return t.replaceAll("-{2,}", "-");
    }

    private static boolean validMonth(String m) {
        int v = Integer.parseInt(m);
        return v >= 1 && v <= 12;
    }

    private static LocalDate date(String y, String m, String d) {
        try {
            return LocalDate.of(Integer.parseInt(y), Integer.parseInt(m), Integer.parseInt(d));
        } catch (Exception e) {
            return null;
        }
    }

    private static String fmtDay(LocalDate d) {
        return d.getYear() + "年" + d.getMonthValue() + "月" + d.getDayOfMonth() + "日";
    }

    private static int cjkCount(String s) {
        int n = 0;
        for (char c : s.toCharArray()) if (c >= 0x4e00 && c <= 0x9fa5) n++;
        return n;
    }
}
