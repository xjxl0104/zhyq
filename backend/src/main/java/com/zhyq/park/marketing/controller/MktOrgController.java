package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.service.MktAdminActorService;
import com.zhyq.park.marketing.service.MktCustomerPricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/** 营销组织架构：P4 → P3 → P2 的邀请层级、每个人推荐的已签约客户，以及 P4 调整成员层级。 */
@Tag(name = "全民营销-组织架构")
@RestController
@RequestMapping("/crm/marketing/org")
@RequiredArgsConstructor
public class MktOrgController {
    /** 已签约：合同处于生效、履约中、变更中、已到期（与计佣口径一致）。 */
    private static final Set<Integer> SIGNED_STATUS = Set.of(4, 5, 6, 7);
    private static final int CUSTOMER_LIMIT = 500;

    private final JdbcTemplate jdbc;
    private final MktPromoterMapper promoterMapper;
    private final CustomerMapper customerMapper;
    private final MktServiceContractMapper contractMapper;
    private final MktAdminActorService adminActorService;
    private final MktCustomerPricingService pricingService;

    public record OrgNode(Long id, String name, String positionCode, Integer status, Long parentId,
                          int customers, int signedCustomers) {}

    public record OrgCustomer(Long id, String name, Long referrerId, String referrerName, boolean signed,
                              String contractNo, Integer signMode, Integer contractStatus) {}

    public record OrgCustomers(Long promoterId, List<OrgCustomer> self, List<OrgCustomer> next, int nextMembers) {}

    public record PositionInput(String code) {}

    @Operation(summary = "组织架构：全部伙伴及其推荐客户数/已签约客户数（前端按 parentId 组树）")
    @PreAuthorize("hasAuthority('crm:marketing:promoter:query')")
    @GetMapping("/tree")
    public Result<List<OrgNode>> tree() {
        List<MktPromoter> all = promoterMapper.selectList(new LambdaQueryWrapper<MktPromoter>()
                .select(MktPromoter::getId, MktPromoter::getName, MktPromoter::getPositionCode, MktPromoter::getStatus,
                        MktPromoter::getParentId)
                .orderByAsc(MktPromoter::getId).last("limit 5000"));
        Map<Long, Integer> customers = counts("SELECT referrer_id AS id, COUNT(*) AS n FROM crm_customer "
                + "WHERE deleted = 0 AND referrer_id IS NOT NULL GROUP BY referrer_id");
        Map<Long, Integer> signed = counts("SELECT c.referrer_id AS id, COUNT(DISTINCT c.id) AS n FROM crm_customer c "
                + "JOIN crm_service_contract s ON s.customer_id = c.id AND s.deleted = 0 AND s.status IN (4,5,6,7) "
                + "WHERE c.deleted = 0 AND c.referrer_id IS NOT NULL GROUP BY c.referrer_id");
        return Result.ok(all.stream().map(p -> new OrgNode(p.getId(), p.getName(), p.getPositionCode(), p.getStatus(),
                p.getParentId(), customers.getOrDefault(p.getId(), 0), signed.getOrDefault(p.getId(), 0))).toList());
    }

    @Operation(summary = "某位伙伴自己推荐的客户，以及直属下一层级推荐的客户（含是否已签约）")
    @PreAuthorize("hasAuthority('crm:marketing:promoter:query')")
    @GetMapping("/{id}/customers")
    public Result<OrgCustomers> customers(@PathVariable Long id) {
        MktPromoter me = promoterMapper.selectById(id);
        if (me == null) throw new BizException("伙伴不存在");
        List<MktPromoter> children = promoterMapper.selectList(new LambdaQueryWrapper<MktPromoter>()
                .eq(MktPromoter::getParentId, id).orderByAsc(MktPromoter::getId));
        Map<Long, String> names = new HashMap<>();
        names.put(me.getId(), me.getName());
        children.forEach(c -> names.put(c.getId(), c.getName()));
        Set<Long> referrers = new HashSet<>(names.keySet());
        List<Customer> rows = customerMapper.selectList(new LambdaQueryWrapper<Customer>()
                .in(Customer::getReferrerId, referrers).orderByDesc(Customer::getId).last("limit " + CUSTOMER_LIMIT * 4));
        Map<Long, MktServiceContract> signedContract = new HashMap<>();
        if (!rows.isEmpty()) {
            for (MktServiceContract contract : contractMapper.selectList(new LambdaQueryWrapper<MktServiceContract>()
                    .in(MktServiceContract::getCustomerId, rows.stream().map(Customer::getId).toList())
                    .in(MktServiceContract::getStatus, SIGNED_STATUS).orderByAsc(MktServiceContract::getId))) {
                signedContract.put(contract.getCustomerId(), contract);   // 同一客户取最新的已签约合同
            }
        }
        List<OrgCustomer> self = new ArrayList<>(), next = new ArrayList<>();
        for (Customer c : rows) {
            MktServiceContract contract = signedContract.get(c.getId());
            OrgCustomer view = new OrgCustomer(c.getId(), c.getName(), c.getReferrerId(), names.get(c.getReferrerId()),
                    contract != null, contract == null ? null : contract.getContractNo(),
                    contract == null ? null : contract.getSignMode(), contract == null ? null : contract.getStatus());
            if (id.equals(c.getReferrerId())) { if (self.size() < CUSTOMER_LIMIT) self.add(view); }
            else if (next.size() < CUSTOMER_LIMIT) next.add(view);
        }
        return Result.ok(new OrgCustomers(id, self, next, children.size()));
    }

    @Operation(summary = "调整层级预览：新上级是谁、本人成交的合同里有多少份还没保存过分配")
    @PreAuthorize("hasAuthority('crm:marketing:promoter:query')")
    @GetMapping("/{id}/position-preview")
    public Result<MktCustomerPricingService.PositionPreview> positionPreview(@PathVariable Long id, @RequestParam String code) {
        return Result.ok(pricingService.previewPosition(requireActor().getId(), id, code));
    }

    @Operation(summary = "P4 调整成员层级（如 P2 → P3）；晋升后成员改挂到岗位更高的最近上级，调整前的客户保留原分成")
    @PreAuthorize("hasAuthority('crm:marketing:promoter:edit')")
    @PostMapping("/{id}/position")
    public Result<Void> position(@PathVariable Long id, @RequestBody PositionInput input) {
        pricingService.setTeamPosition(requireActor().getId(), id, input == null ? null : input.code());
        return Result.ok();
    }

    /** 调整层级以当前后台账号关联的 P4 身份执行；未关联时说明如何处理。 */
    private MktPromoter requireActor() {
        MktPromoter actor = adminActorService.currentPromoter();
        if (actor == null) {
            throw new BizException(403, "当前后台账号未关联伙伴档案：请在系统用户中把手机号设为与你的 P4 伙伴档案一致（或昵称与伙伴姓名一致）后重新登录");
        }
        return actor;
    }

    private Map<Long, Integer> counts(String sql) {
        Map<Long, Integer> out = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(sql)) {
            Object id = row.get("id"), n = row.get("n");
            if (id instanceof Number a && n instanceof Number b) out.put(a.longValue(), b.intValue());
        }
        return out;
    }
}
