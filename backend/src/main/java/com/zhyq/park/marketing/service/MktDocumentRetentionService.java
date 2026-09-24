package com.zhyq.park.marketing.service;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.file.entity.SysFile;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;
@Service
@RequiredArgsConstructor
public class MktDocumentRetentionService {
    private final JdbcTemplate jdbc;
    public void assertDeletable(SysFile f) {
        if (f == null || f.getBizType() == null || !f.getBizType().startsWith("mkt_")) return;
        // Marketing uploads are immediately attached to a specific business object.
        // Retain even unsubmitted files so deletion cannot race a contract/payment transaction.
        if (f.getBizId() != null) throw new BizException("营销业务附件需要保留归档，不能删除；可在提交前移除关联");
        String reference="file:"+f.getId();
        for (var pair: Map.of("crm_withdrawal","pay_proof","crm_warehouse_settlement","pay_proof",
                "crm_service_fee_bill","receipt_proof","crm_direct_sign_payment","pay_proof",
                "crm_warehouse","contract_file").entrySet()) {
            Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM "+pair.getKey()+" WHERE "+pair.getValue()+"=? AND deleted=0",Integer.class,reference);
            if (count != null && count > 0) throw new BizException("该附件已用于合同或财务凭证，不能删除");
        }
        if (MktWarehouseFileService.BIZ_TYPE.equals(f.getBizType())) {
            // Archive all submitted warehouse documents; replacing a reference does not erase prior evidence.
            Integer contracts=jdbc.queryForObject("SELECT COUNT(*) FROM crm_service_contract WHERE warehouse_id=? AND deleted=0 AND files IS NOT NULL",Integer.class,f.getBizId());
            Integer steps=jdbc.queryForObject("SELECT COUNT(*) FROM crm_warehouse_onboarding WHERE warehouse_id=? AND attachments IS NOT NULL AND deleted=0",Integer.class,f.getBizId());
            if ((contracts != null && contracts > 0) || (steps != null && steps > 0)) throw new BizException("云仓已提交的合同与资质资料需保留归档，不能删除");
        }
    }
}
