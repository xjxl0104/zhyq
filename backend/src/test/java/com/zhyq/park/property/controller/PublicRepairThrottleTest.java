package com.zhyq.park.property.controller;

import com.zhyq.park.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicRepairThrottleTest {

    @Test
    void blocksFourthSubmitFromSamePhone() {
        var throttle = new PublicRepairController.SubmitThrottle();
        for (int i = 0; i < 3; i++) {
            throttle.check("13800000000", "1.1.1.1");
        }

        assertThatThrownBy(() -> throttle.check("13800000000", "1.1.1.1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("频繁");
    }

    @Test
    void blocksByIpEvenWhenPhoneChanges() {
        var throttle = new PublicRepairController.SubmitThrottle();
        // 换手机号绕不过 IP 限额:10 单之后同一出口 IP 再提交就拦
        for (int i = 0; i < 10; i++) {
            throttle.check("1380000" + String.format("%04d", i), "2.2.2.2");
        }

        assertThatThrownBy(() -> throttle.check("13900000000", "2.2.2.2"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void differentPhonesAndIpsDoNotInterfere() {
        var throttle = new PublicRepairController.SubmitThrottle();
        throttle.check("13800000001", "3.3.3.1");
        throttle.check("13800000002", "3.3.3.2");

        assertThat(true).isTrue(); // 没抛异常即通过
    }
}
