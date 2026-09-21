package com.zhyq.park.marketing.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.marketing.entity.MktServiceContract;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 先钉住助手本身:占位符还原、IN / = 两种前态写法、SET 多列切分。 */
class WrapperAssertTest {

    @BeforeAll
    static void initMp() {
        MapperBuilderAssistant a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(a, MktServiceContract.class);
    }

    @Test
    void parsesInListAndMultipleSets() {
        LambdaUpdateWrapper<MktServiceContract> w = new LambdaUpdateWrapper<MktServiceContract>()
                .eq(MktServiceContract::getId, 42L)
                .in(MktServiceContract::getStatus, 4, 5, 6)
                .set(MktServiceContract::getStatus, 8)
                .set(MktServiceContract::getTerminateReason, "退租, 客户搬走");

        WrapperAssert wa = WrapperAssert.of(w);

        assertThat(wa.whereEq("id")).isEqualTo("42");
        assertThat(wa.whereIn("status")).containsExactly(4, 5, 6);
        assertThat(wa.setValue("status")).isEqualTo("8");
        assertThat(wa.setValue("terminate_reason")).isEqualTo("退租, 客户搬走");
        assertThat(wa.setColumns()).containsExactly("status", "terminate_reason");
        wa.isStatusTransition("status", 8, 4, 5, 6).hasWhereId(42L);
    }

    @Test
    void parsesSingleEqAsOneElementSet() {
        LambdaUpdateWrapper<MktServiceContract> w = new LambdaUpdateWrapper<MktServiceContract>()
                .eq(MktServiceContract::getId, 1L)
                .eq(MktServiceContract::getStatus, 2)
                .set(MktServiceContract::getStatus, 3);

        WrapperAssert.of(w).isStatusTransition("status", 3, 2);
    }

    @Test
    void failsLoudlyWhenPredecessorSetIsWider() {
        LambdaUpdateWrapper<MktServiceContract> w = new LambdaUpdateWrapper<MktServiceContract>()
                .eq(MktServiceContract::getId, 1L)
                .in(MktServiceContract::getStatus, 1, 2, 3)
                .set(MktServiceContract::getStatus, 9);

        assertThatThrownBy(() -> WrapperAssert.of(w).isStatusTransition("status", 9, 3))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("前态集合");
    }

    @Test
    void conditionalSetThatIsOffDoesNotAppear() {
        LambdaUpdateWrapper<MktServiceContract> w = new LambdaUpdateWrapper<MktServiceContract>()
                .eq(MktServiceContract::getId, 1L)
                .set(MktServiceContract::getStatus, 2)
                .set(false, MktServiceContract::getTerminateReason, "x");

        assertThat(WrapperAssert.of(w).setValue("terminate_reason")).isNull();
        assertThat(WrapperAssert.of(w).whereIn("status")).isEmpty();
    }
}
