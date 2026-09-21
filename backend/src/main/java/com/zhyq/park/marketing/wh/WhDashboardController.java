package com.zhyq.park.marketing.wh;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktCustomerErpMap;
import com.zhyq.park.marketing.entity.MktReferralOrder;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktCustomerErpMapMapper;
import com.zhyq.park.marketing.mapper.MktReferralOrderMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.service.MktWarehouseOnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Warehouse-scoped read models. Sensitive promoter/phone/commission columns are never serialized. */
@Tag(name = "云仓端-看板")
@RestController
@RequestMapping("/wh/v1")
@RequiredArgsConstructor
public class WhDashboardController {
    private final MktWarehouseMapper warehouseMapper;
    private final MktCustomerErpMapMapper mapMapper;
    private final CustomerMapper customerMapper;
    private final MktReferralOrderMapper orderMapper;

    @Operation(summary = "当前云仓运营汇总")
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Long id = WhAuthContext.currentWarehouseId(); MktWarehouse w = WhAuthContext.requireWarehouse(warehouseMapper, id);
        LambdaQueryWrapper<MktReferralOrder> oq = orderScope(w).eq(MktReferralOrder::getSourceType, 2);
        List<MktReferralOrder> orders = orderMapper.selectList(oq);
        long today = orders.stream().filter(o -> o.getEventTime() != null && o.getEventTime().toLocalDate().equals(java.time.LocalDate.now())).count();
        long month = orders.stream().filter(o -> o.getEventTime() != null && o.getEventTime().getMonth() == java.time.LocalDate.now().getMonth() && o.getEventTime().getYear() == java.time.LocalDate.now().getYear()).count();
        Map<String,Object> out = new HashMap<>();
        out.put("warehouseId", id); out.put("warehouseCode", w.getCode()); out.put("warehouseName", w.getName());
        out.put("customerCount", mapMapper.selectCount(mapScope(w))); out.put("todayOrders", today); out.put("monthOrders", month);
        out.put("erpStatus", w.getErpStatus()); out.put("joinStatus", w.getJoinStatus());
        return Result.ok(out);
    }

    @Operation(summary = "当前云仓承接客户")
    @GetMapping("/customers")
    public Result<PageResult<Map<String,Object>>> customers(@RequestParam(defaultValue="1") int pageNo,
                                                              @RequestParam(defaultValue="20") int pageSize) {
        MktWarehouse w = WhAuthContext.requireWarehouse(warehouseMapper);
        IPage<MktCustomerErpMap> page = mapMapper.selectPage(new Page<>(pageNo,pageSize), mapScope(w).orderByDesc(MktCustomerErpMap::getId));
        List<Map<String,Object>> rows = page.getRecords().stream().map(this::customerView).collect(Collectors.toList());
        return Result.ok(PageResult.of(page.getTotal(), rows));
    }

    @Operation(summary = "当前云仓出库单")
    @GetMapping("/orders/page")
    public Result<PageResult<Map<String,Object>>> orders(@RequestParam(defaultValue="1") int pageNo,
                                                          @RequestParam(defaultValue="20") int pageSize,
                                                          @RequestParam(required=false) Integer status) {
        MktWarehouse w = WhAuthContext.requireWarehouse(warehouseMapper);
        LambdaQueryWrapper<MktReferralOrder> q = orderScope(w).eq(status != null, MktReferralOrder::getStatus, status).orderByDesc(MktReferralOrder::getId);
        IPage<MktReferralOrder> page = orderMapper.selectPage(new Page<>(pageNo,pageSize), q);
        List<Map<String,Object>> rows = page.getRecords().stream().map(this::orderView).collect(Collectors.toList());
        return Result.ok(PageResult.of(page.getTotal(), rows));
    }

    private LambdaQueryWrapper<MktCustomerErpMap> mapScope(MktWarehouse w) {
        return new LambdaQueryWrapper<MktCustomerErpMap>().eq(MktCustomerErpMap::getWarehouseId, w.getId()).eq(w.getProjectId()!=null, MktCustomerErpMap::getProjectId, w.getProjectId());
    }
    private LambdaQueryWrapper<MktReferralOrder> orderScope(MktWarehouse w) {
        return new LambdaQueryWrapper<MktReferralOrder>().eq(MktReferralOrder::getWarehouseId, w.getId()).eq(w.getProjectId()!=null, MktReferralOrder::getProjectId, w.getProjectId());
    }
    private Map<String,Object> customerView(MktCustomerErpMap map) {
        Customer c = map.getCustomerId()==null ? null : customerMapper.selectById(map.getCustomerId());
        Map<String,Object> v = new HashMap<>(); v.put("id", map.getCustomerId()); v.put("customerCode", map.getCustomerCode()); v.put("status", map.getStatus());
        if (c != null) { v.put("name", c.getName()); v.put("grade", c.getGrade()); v.put("serviceType", c.getServiceType()); v.put("status", c.getStatus()); }
        return v;
    }
    private Map<String,Object> orderView(MktReferralOrder o) {
        Map<String,Object> v = new HashMap<>(); v.put("id",o.getId()); v.put("sourceNo",o.getSourceNo()); v.put("customerId",o.getCustomerId()); v.put("customerCode",o.getCustomerCode());
        v.put("qty",o.getQty()); v.put("packages",o.getPackages()); v.put("serviceFee",o.getServiceFee()); v.put("goodsAmount",o.getGoodsAmount()); v.put("logisticsNo",o.getLogisticsNo()); v.put("status",o.getStatus()); v.put("eventTime",o.getEventTime());
        return v;
    }
}
