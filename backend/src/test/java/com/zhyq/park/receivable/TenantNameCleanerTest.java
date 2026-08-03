package com.zhyq.park.receivable;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhyq.park.receivable.service.TenantNameCleaner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantNameCleanerTest {

    @Test
    @DisplayName("剥半角状态后缀,保留全角括号法定名")
    void clean_stripsHalfWidthSuffix_keepsFullWidth() {
        assertThat(TenantNameCleaner.clean("广州昌泰供应链科技有限公司(新)还未签合同"))
                .isEqualTo("广州昌泰供应链科技有限公司");
        assertThat(TenantNameCleaner.clean("广州昌泰供应链科技有限公司(新)"))
                .isEqualTo("广州昌泰供应链科技有限公司");
        assertThat(TenantNameCleaner.clean("广州鑫晨供应链有限公司(新签订合同)"))
                .isEqualTo("广州鑫晨供应链有限公司");
        assertThat(TenantNameCleaner.clean("中印国际供应链（广州）有限公司"))
                .isEqualTo("中印国际供应链（广州）有限公司");
        assertThat(TenantNameCleaner.clean("  李万能 ")).isEqualTo("李万能");
    }

    @Test
    @DisplayName("多重行尾半角后缀反复剥离;null 透传")
    void clean_stripsRepeatedSuffixes_andNullPassthrough() {
        assertThat(TenantNameCleaner.clean("某某公司(备注)(作废)")).isEqualTo("某某公司");
        assertThat(TenantNameCleaner.clean(null)).isNull();
        assertThat(TenantNameCleaner.clean("")).isEqualTo("");
    }
}
