package com.zhyq.park.common.setting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 业务配置(biz_setting)的统一读取口。
 *
 * <p>此前这张表是「死的」:只有设置页在增删改,没有任何一行业务代码读它,
 * 每个值都在别处又硬编码了一份 —— 而且对不上(配置里到期提醒 90 天,
 * 工作台却写死 30 天)。改配置不生效,用户自然觉得设置页没用。</p>
 *
 * <p>取不到或格式不对时一律回退默认值并告警,不让一条脏配置把功能打挂 ——
 * 配置页是给人手填的,填错是常态。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BizSettings {

    private final JdbcTemplate jdbc;

    /** 原始字符串;查不到返回 null */
    public String raw(String module, String key) {
        try {
            return jdbc.query(
                    "SELECT svalue FROM biz_setting WHERE module = ? AND skey = ? AND deleted = 0 LIMIT 1",
                    rs -> rs.next() ? rs.getString(1) : null, module, key);
        } catch (Exception e) {
            log.warn("[biz-setting] 读取 {}.{} 失败,用默认值", module, key, e);
            return null;
        }
    }

    public int getInt(String module, String key, int defaultValue) {
        String value = raw(module, key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("[biz-setting] {}.{} = 「{}」不是整数,用默认值 {}", module, key, value, defaultValue);
            return defaultValue;
        }
    }

    public BigDecimal getDecimal(String module, String key, BigDecimal defaultValue) {
        String value = raw(module, key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            log.warn("[biz-setting] {}.{} = 「{}」不是数字,用默认值 {}", module, key, value, defaultValue);
            return defaultValue;
        }
    }

    /** 开关:1/true/on/yes 视为开,其余为关 */
    public boolean getBoolean(String module, String key, boolean defaultValue) {
        String value = raw(module, key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String v = value.trim().toLowerCase();
        return "1".equals(v) || "true".equals(v) || "on".equals(v) || "yes".equals(v);
    }

    public String getString(String module, String key, String defaultValue) {
        String value = raw(module, key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
