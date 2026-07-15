package com.zhyq.park.rule;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONException;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zhyq.park.common.event.DomainEvent.AlarmRaised;

/**
 * 规则条件匹配器(纯函数,无状态)。
 *
 * <p>D1 决策(设计文档 §6):条件用简单 JSON 字段匹配,非表达式引擎。
 * 首期仅支持 {@code minLevel}(int,event.level >= minLevel 即命中),
 * 结构上按 key 分派,后续新增条件键(如 deviceType)只需加一个 if 分支。</p>
 */
public final class RuleEvaluator {

    private RuleEvaluator() {
    }

    /**
     * @param conditionJson 规则条件 JSON;null/空白/"{}" 视为无条件,全匹配
     * @param event         触发事件
     * @return 是否命中;conditionJson 格式错误(非合法 JSON)视为不命中
     */
    public static boolean matches(String conditionJson, AlarmRaised event) {
        if (StrUtil.isBlank(conditionJson)) {
            return true;
        }
        JSONObject condition;
        try {
            condition = JSONUtil.parseObj(conditionJson);
        } catch (JSONException e) {
            return false;
        }
        if (condition.isEmpty()) {
            return true;
        }
        if (condition.containsKey("minLevel")) {
            Integer minLevel = condition.getInt("minLevel");
            Integer eventLevel = parseLevel(event.level());
            if (minLevel == null || eventLevel == null || eventLevel < minLevel) {
                return false;
            }
        }
        return true;
    }

    private static Integer parseLevel(String level) {
        if (StrUtil.isBlank(level)) {
            return null;
        }
        try {
            return Integer.parseInt(level.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
