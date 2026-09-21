package com.zhyq.park.marketing.controller;

import com.zhyq.park.marketing.entity.MktServiceContract;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MktServiceContractControllerTest {

    @Test
    void createRequestMappingDoesNotCopySensitivePartnerOrGradeFields() throws Exception {
        MktServiceContractController.CreateReq request = new ObjectMapper().findAndRegisterModules().readValue("""
                {"customerId":11,"warehouseId":22,"signMode":2,"serviceType":1,"feeModel":3,
                 "templateId":99,"startDate":"2026-10-01","endDate":"2027-09-30","deposit":100,
                 "payCycle":3,"priceTable":"{\\"monthly\\":10}","remark":"remark","projectId":7,
                 "partnerId":999,"grade":"FORGED","id":12345}
                """, MktServiceContractController.CreateReq.class);

        MktServiceContract draft = MktServiceContractController.toDraft(request);

        assertThat(draft.getCustomerId()).isEqualTo(11L);
        assertThat(draft.getWarehouseId()).isEqualTo(22L);
        assertThat(draft.getTemplateId()).isEqualTo(99L);
        assertThat(draft.getPartnerId()).isNull();
        assertThat(draft.getGrade()).isNull();
        assertThat(draft.getId()).isNull();
    }
}
