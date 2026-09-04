package com.zhyq.park.contract;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.contract.entity.Contract;
import com.zhyq.park.contract.mapper.ContractMapper;
import com.zhyq.park.contract.service.ContractExpiryJob;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 合同到期自动置状态。此前没有任何机制做这件事,归档页「已到期」页签永远空着 ——
 * 这里锁住三件事:只动执行中的、只动结束日已过的、条件更新一次性完成(幂等)。
 */
@ExtendWith(MockitoExtension.class)
class ContractExpiryJobTest {

    @Mock private ContractMapper contractMapper;
    @Mock private BizSettings settings;

    @BeforeAll
    static void initMpLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Contract.class);
    }

    @Test
    @DisplayName("条件更新一次性完成:WHERE 带执行中(5)与结束日已过,SET 置已到期(8)")
    void expiresOnlyRunningContractsPastEndDate() {
        when(contractMapper.update(isNull(), any())).thenReturn(3);

        ContractExpiryJob job = new ContractExpiryJob(contractMapper, settings);
        assertThat(job.expireDueContracts()).isEqualTo(3);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaUpdateWrapper<Contract>> wrapper =
                ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
        verify(contractMapper).update(isNull(), wrapper.capture());
        String sql = wrapper.getValue().getCustomSqlSegment();
        // 守卫:只翻执行中的、只翻结束日已过的 —— 未到期或已终止/已归档的一律不碰
        assertThat(sql).contains("status");
        assertThat(sql).contains("end_date");
        assertThat(wrapper.getValue().getSqlSet()).contains("status");
    }

    @Test
    @DisplayName("没有到期合同时命中 0 行,不报错、不打日志噪声")
    void noDueContractsIsNoOp() {
        when(contractMapper.update(isNull(), any())).thenReturn(0);
        // 开关打开,sync() 才会真正走到条件更新;mock 默认 false 会直接跳过,用例就空转了
        when(settings.getBoolean("contract", "auto_expire_enabled", true)).thenReturn(true);

        ContractExpiryJob job = new ContractExpiryJob(contractMapper, settings);
        assertThat(job.expireDueContracts()).isZero();
        job.sync(); // 不应抛异常
    }
}
