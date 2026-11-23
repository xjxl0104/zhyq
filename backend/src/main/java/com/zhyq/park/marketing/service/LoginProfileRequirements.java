package com.zhyq.park.marketing.service;

import com.zhyq.park.common.exception.BizException;
import org.springframework.util.StringUtils;

/** Basic contact information required when creating or completing a login identity. */
public final class LoginProfileRequirements {
    private LoginProfileRequirements() {}

    public static boolean missingName(String value) {
        if (!StringUtils.hasText(value)) return true;
        String name = value.trim();
        return name.equals("园区伙伴") || name.equals("微信用户") || name.equals("云仓联系人")
                || name.equals("待完善云仓") || name.matches("伙伴\\d{4}");
    }

    public static String requireName(String value) {
        String name = value == null ? "" : value.trim();
        if (missingName(name)) throw new BizException(400, "请填写您的姓名");
        if (name.length() > 50 || name.codePoints().anyMatch(Character::isISOControl))
            throw new BizException(400, "姓名最多 50 字，不能包含控制字符");
        return name;
    }

    public static String requirePhone(String value) {
        String phone = value == null ? "" : value.trim();
        if (!phone.matches("^1\\d{10}$")) throw new BizException(400, "请填写有效手机号或通过微信授权手机号");
        return phone;
    }

    public static boolean complete(String name, String phone) {
        return !missingName(name) && phone != null && phone.matches("^1\\d{10}$");
    }
}
