package com.zhyq.park.receivable;

import com.zhyq.park.receivable.mapper.ReceivableRegisterMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceivableRegisterLockContractTest {

    @Test
    void mapperLocksOnlyActiveRegisterRowsForUpdate() throws Exception {
        var method = ReceivableRegisterMapper.class.getMethod("selectByIdForUpdate", Long.class);
        Select select = method.getAnnotation(Select.class);

        assertNotNull(select);
        String sql = String.join(" ", select.value()).toUpperCase();
        assertTrue(sql.contains("DELETED = 0"));
        assertTrue(sql.contains("FOR UPDATE"));
    }
}
