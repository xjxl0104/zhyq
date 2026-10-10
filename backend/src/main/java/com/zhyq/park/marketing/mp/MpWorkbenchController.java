package com.zhyq.park.marketing.mp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPromoterCommissionMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 伙伴工作台汇总。全部走 SQL 聚合,供首页定时刷新,不把明细读进内存。 */
@Tag(name = "小程序-工作台")
@RestController
@RequestMapping("/mp/v1")
@RequiredArgsConstructor
public class MpWorkbenchController {

    private final MktPromoterMapper promoterMapper;
    private final CustomerMapper customerMapper;
    private final MktPromoterCommissionMapper commissionMapper;

    @Operation(summary = "工作台:团队人数 / 客户数 / 已赚 / 待结算 + 收入按来源拆分")
    @GetMapping("/workbench")
    public Result<Map<String, Object>> workbench() {
        Long pid = MpAuthService.currentPromoterId();
        List<Map<String, Object>> bySource = commissionMapper.sumBySource(pid);
        BigDecimal earned = bySource.stream().map(row -> (BigDecimal) row.get("amount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> m = new HashMap<>();
        m.put("teamCount", promoterMapper.selectCount(new LambdaQueryWrapper<MktPromoter>().eq(MktPromoter::getParentId, pid)));
        m.put("customerCount", customerMapper.selectCount(new LambdaQueryWrapper<Customer>().eq(Customer::getReferrerId, pid)));
        m.put("earned", earned);
        m.put("pending", commissionMapper.sumPending(pid));
        m.put("bySource", bySource);
        return Result.ok(m);
    }
}
