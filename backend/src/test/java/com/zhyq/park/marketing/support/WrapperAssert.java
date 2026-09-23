package com.zhyq.park.marketing.support;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 把 Mockito 捕获到的 {@link LambdaUpdateWrapper} 还原成"哪一列、允许哪些前态、要改成什么",
 * 让状态机测试能真的断言「条件更新」的前态集合,而不是只 mock 一个返回值。
 *
 * <p>MyBatis-Plus 的 SQL 片段里参数是 {@code #{ew.paramNameValuePairs.MPGENVAL3}} 这种占位符,
 * 这里先用 {@code getParamNameValuePairs()} 把占位符换回真实值再解析。</p>
 */
public final class WrapperAssert {

    private static final Pattern PLACEHOLDER = Pattern.compile("#\\{ew\\.paramNameValuePairs\\.(\\w+)}");

    private final String where;
    private final String set;

    private WrapperAssert(String where, String set) {
        this.where = where;
        this.set = set;
    }

    public static WrapperAssert of(UpdateWrapper<?> wrapper) {
        return new WrapperAssert(resolve(wrapper.getSqlSegment(), wrapper.getParamNameValuePairs()),
                resolve(wrapper.getSqlSet(), wrapper.getParamNameValuePairs()));
    }

    public static WrapperAssert of(LambdaUpdateWrapper<?> wrapper) {
        return new WrapperAssert(resolve(wrapper.getSqlSegment(), wrapper.getParamNameValuePairs()),
                resolve(wrapper.getSqlSet(), wrapper.getParamNameValuePairs()));
    }

    /** WHERE 里对某一列的取值约束:兼容 {@code col = v} 与 {@code col IN (v1,v2)};没有约束返回空集。 */
    public Set<Integer> whereIn(String column) {
        Set<Integer> out = new LinkedHashSet<>();
        Matcher in = Pattern.compile("\\b" + Pattern.quote(column) + " IN \\(([^)]*)\\)").matcher(where);
        while (in.find()) {
            for (String v : in.group(1).split(",")) {
                out.add(Integer.valueOf(v.trim()));
            }
        }
        Matcher eq = Pattern.compile("\\b" + Pattern.quote(column) + " = ([^\\s)]+)").matcher(where);
        while (eq.find()) {
            out.add(Integer.valueOf(eq.group(1).trim()));
        }
        return out;
    }

    /** WHERE 里 {@code col = v} 的原始字符串值(id、payNo 之类不是整数的列用这个)。 */
    public String whereEq(String column) {
        Matcher eq = Pattern.compile("\\b" + Pattern.quote(column) + " = ([^\\s)]+)").matcher(where);
        return eq.find() ? eq.group(1).trim() : null;
    }

    /** SET 里某一列的目标值;没 set 这一列返回 null。 */
    public String setValue(String column) {
        for (String piece : splitSet()) {
            int i = piece.indexOf('=');
            if (i > 0 && piece.substring(0, i).trim().equals(column)) {
                return piece.substring(i + 1).trim();
            }
        }
        return null;
    }

    public List<String> setColumns() {
        List<String> cols = new ArrayList<>();
        for (String piece : splitSet()) {
            int i = piece.indexOf('=');
            if (i > 0) {
                cols.add(piece.substring(0, i).trim());
            }
        }
        return cols;
    }

    public String where() {
        return where;
    }

    public String set() {
        return set;
    }

    // ---- 常用断言 ----

    /** 断言这是一次「status 只允许从 from 集合出发、改成 to」的条件更新。 */
    public WrapperAssert isStatusTransition(String statusColumn, int to, Integer... from) {
        assertThat(whereIn(statusColumn)).as("WHERE %s 前态集合(实际 SQL: %s)", statusColumn, where)
                .containsExactlyInAnyOrder(from);
        assertThat(setValue(statusColumn)).as("SET %s 目标态(实际 SET: %s)", statusColumn, set)
                .isEqualTo(String.valueOf(to));
        return this;
    }

    public WrapperAssert hasWhereId(Object id) {
        assertThat(whereEq("id")).as("WHERE id(实际 SQL: %s)", where).isEqualTo(String.valueOf(id));
        return this;
    }

    // ---- internals ----

    private List<String> splitSet() {
        List<String> out = new ArrayList<>();
        if (StringUtils.isBlank(set)) {
            return out;
        }
        // set 片段形如 "status=2,terminate_reason=退租,pay_at=2026-09-21T01:00";值里本身不会出现逗号+列名=,
        // 简单按 ",<identifier>=" 边界切。
        Matcher m = Pattern.compile(",(?=\\s*\\w+\\s*=)").matcher(set);
        int last = 0;
        while (m.find()) {
            out.add(set.substring(last, m.start()));
            last = m.end();
        }
        out.add(set.substring(last));
        return out;
    }

    private static String resolve(String sql, Map<String, Object> params) {
        if (sql == null) {
            return "";
        }
        Matcher m = PLACEHOLDER.matcher(sql);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object v = params.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(v)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
