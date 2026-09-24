package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.entity.MktServiceContractVersion;
import com.zhyq.park.marketing.mapper.MktServiceContractVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

/** 按业务发生日选条款，补录旧订单与固定月费不能套用未来单价。 */
@Service
@RequiredArgsConstructor
public class MktContractTermsService {
    private final MktServiceContractVersionMapper versions;
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public MktServiceContract atDate(MktServiceContract current, LocalDate date) {
        LocalDate from = effectiveFrom(current);
        if (date == null || from == null || !date.isBefore(from)) return current;
        for (var version : versions.selectList(new LambdaQueryWrapper<MktServiceContractVersion>()
                .eq(MktServiceContractVersion::getContractId, current.getId())
                .orderByDesc(MktServiceContractVersion::getVerNo))) {
            try {
                // 兼容旧快照只存部分条款，其余身份字段保持当前合同。
                MktServiceContract old = new MktServiceContract();
                BeanUtils.copyProperties(current, old);
                old.setTermsEffectiveFrom(null);
                var node = JSON.readTree(version.getSnapshot());
                if (node.has("priceTable") && node.get("priceTable").isObject())
                    ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("priceTable", node.get("priceTable").toString());
                JSON.readerForUpdating(old).readValue(node);
                LocalDate oldFrom = effectiveFrom(old);
                if (oldFrom != null && !date.isBefore(oldFrom)) return old;
            } catch (Exception error) {
                throw new BizException("合同历史条款不可读取，请先核对版本记录");
            }
        }
        throw new BizException("业务日期早于合同条款生效日期");
    }

    public static LocalDate effectiveFrom(MktServiceContract c) {
        return c.getTermsEffectiveFrom() == null ? c.getStartDate() : c.getTermsEffectiveFrom();
    }
}
