package com.zhyq.park.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.crm.entity.Channel;
import com.zhyq.park.crm.entity.Commission;
import com.zhyq.park.crm.entity.CrmContractRef;
import com.zhyq.park.crm.mapper.ChannelMapper;
import com.zhyq.park.crm.mapper.CommissionMapper;
import com.zhyq.park.crm.mapper.CrmContractRefMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "招商-佣金")
@RestController
@RequestMapping("/crm/commission")
@RequiredArgsConstructor
public class CommissionController {

    private final CommissionMapper commissionMapper;
    private final ChannelMapper channelMapper;
    private final CrmContractRefMapper contractRefMapper;

    @Operation(summary = "分页查询佣金")
    @GetMapping("/page")
    public Result<PageResult<Commission>> page(@RequestParam(defaultValue = "1") int pageNo,
                                               @RequestParam(defaultValue = "10") int pageSize,
                                               @RequestParam(required = false) Long channelId,
                                               @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Commission> qw = new LambdaQueryWrapper<>();
        qw.eq(channelId != null, Commission::getChannelId, channelId)
          .eq(status != null, Commission::getStatus, status)
          .orderByDesc(Commission::getId);
        IPage<Commission> p = commissionMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "删除佣金")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        commissionMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "按合同生成佣金")
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        if (body.get("contractId") == null || body.get("channelId") == null) {
            throw new BizException("合同和渠道不能为空");
        }
        Long contractId = Long.valueOf(body.get("contractId").toString());
        Long channelId = Long.valueOf(body.get("channelId").toString());

        // 查合同,必须执行中(status=5)
        CrmContractRef contract = contractRefMapper.selectById(contractId);
        if (contract == null) {
            throw new BizException("合同不存在");
        }
        if (!Integer.valueOf(5).equals(contract.getStatus())) {
            throw new BizException("仅执行中合同可计佣");
        }

        // 查渠道取佣金比例
        Channel channel = channelMapper.selectById(channelId);
        if (channel == null) {
            throw new BizException("渠道不存在");
        }
        BigDecimal rate = channel.getCommissionRate() == null ? BigDecimal.ZERO : channel.getCommissionRate();

        // 防重:该合同该渠道已有非作废(status!=3)佣金
        Long dup = commissionMapper.selectCount(new LambdaQueryWrapper<Commission>()
                .eq(Commission::getContractId, contractId)
                .eq(Commission::getChannelId, channelId)
                .ne(Commission::getStatus, 3));
        if (dup != null && dup > 0) {
            throw new BizException("该合同该渠道已生成佣金");
        }

        // 计佣基数 = 首年租金 = rentPrice × rentArea × 12
        BigDecimal rentPrice = contract.getRentPrice() == null ? BigDecimal.ZERO : contract.getRentPrice();
        BigDecimal rentArea = contract.getRentArea() == null ? BigDecimal.ZERO : contract.getRentArea();
        BigDecimal baseAmount = rentPrice.multiply(rentArea).multiply(BigDecimal.valueOf(12))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal commission = baseAmount.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        Commission c = new Commission();
        c.setChannelId(channelId);
        c.setContractId(contractId);
        c.setBaseAmount(baseAmount);
        c.setRate(rate);
        c.setCommission(commission);
        c.setStatus(1);
        commissionMapper.insert(c);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("baseAmount", baseAmount);
        m.put("commission", commission);
        return Result.ok(m);
    }

    @Operation(summary = "结算(待结算->已结算)")
    @PostMapping("/{id}/settle")
    public Result<Void> settle(@PathVariable Long id) {
        LambdaUpdateWrapper<Commission> uw = new LambdaUpdateWrapper<>();
        uw.eq(Commission::getId, id).eq(Commission::getStatus, 1)
          .set(Commission::getStatus, 2)
          .set(Commission::getSettleTime, LocalDateTime.now());
        if (commissionMapper.update(null, uw) == 0) {
            throw new BizException("仅待结算佣金可结算");
        }
        return Result.ok();
    }

    @Operation(summary = "作废佣金")
    @PostMapping("/{id}/void")
    public Result<Void> voidCommission(@PathVariable Long id) {
        LambdaUpdateWrapper<Commission> uw = new LambdaUpdateWrapper<>();
        uw.eq(Commission::getId, id).ne(Commission::getStatus, 3)
          .set(Commission::getStatus, 3);
        if (commissionMapper.update(null, uw) == 0) {
            throw new BizException("该佣金已作废");
        }
        return Result.ok();
    }

    @Operation(summary = "佣金统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        List<Commission> all = commissionMapper.selectList(new LambdaQueryWrapper<>());
        BigDecimal pendingAmount = BigDecimal.ZERO;
        BigDecimal settledAmount = BigDecimal.ZERO;
        for (Commission c : all) {
            BigDecimal amt = c.getCommission() == null ? BigDecimal.ZERO : c.getCommission();
            if (Integer.valueOf(1).equals(c.getStatus())) {
                pendingAmount = pendingAmount.add(amt);
            } else if (Integer.valueOf(2).equals(c.getStatus())) {
                settledAmount = settledAmount.add(amt);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pendingAmount", pendingAmount);
        m.put("settledAmount", settledAmount);
        m.put("count", all.size());
        return Result.ok(m);
    }
}
