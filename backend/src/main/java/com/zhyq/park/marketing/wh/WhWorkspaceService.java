package com.zhyq.park.marketing.wh;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.auth.JwtAccountService;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseContact;
import com.zhyq.park.marketing.mapper.MktWarehouseContactMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktCustomerAssignmentService;
import com.zhyq.park.marketing.service.MktReferralRequirements;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

/** Contact-scoped warehouse workspace. Every operation rechecks the persisted identity. */
@Service
@RequiredArgsConstructor
public class WhWorkspaceService {
    private final MktWarehouseContactMapper contacts;
    private final MktWarehouseMapper warehouses;
    private final CustomerMapper customers;
    private final WhIdentityService identities;
    private final MktCustomerAssignmentService assignments;
    private final com.zhyq.park.marketing.service.MktCustomerProgressService progress;
    private final MktAuditService audit;
    private final JdbcTemplate jdbc;
    private final com.zhyq.park.marketing.quote.QuoteIntakeService quoteIntake;
    public static final List<String> STAGES = List.of("待业务员承接", "业务员承接", "价格谈判", "签约", "货品入仓", "正式发货");

    public WhIdentityService.Identity current() {
        Long warehouseId = WhAuthContext.currentWarehouseId();
        MktWarehouseContact contact = contacts.selectById(WhAuthContext.currentContactId());
        WhIdentityService.active(contact);
        if (!Objects.equals(warehouseId, contact.getWarehouseId())) throw forbidden();
        MktWarehouse warehouse = warehouses.selectById(warehouseId);
        JwtAccountService.assertWarehouseActive(warehouse);
        if (!sameScope(contact.getProjectId(), warehouse.getProjectId())
                || !sameScope(contact.getTenantId(), warehouse.getTenantId())) throw forbidden();
        if ("owner".equals(contact.getRole()) && !Objects.equals(contact.getPhone(), warehouse.getPhone())) throw forbidden();
        return new WhIdentityService.Identity(warehouse, contact);
    }

    public WhIdentityService.Identity owner() {
        WhIdentityService.Identity who = current();
        if (!"owner".equals(who.contact().getRole())) throw new BizException(403, "仅云仓主账号可以操作");
        return who;
    }

    public Map<String,Object> profile() {
        var who = current();
        return WhIdentityService.profile(who.contact(), who.warehouse());
    }

    @Transactional
    public Map<String,Object> updateProfile(Map<String,String> body) {
        var who = current();
        identities.lockPhone(who.contact().getPhone());
        warehouses.selectForUpdate(who.warehouse().getId());
        who = current();
        for (String key : body.keySet()) if (!Set.of("name", "phone", "warehouseName").contains(key))
            throw new BizException(400, "不支持修改该账号字段");
        String name = WhIdentityService.requireName(body.get("name"));
        if (body.containsKey("phone") && !Objects.equals(body.get("phone"), who.contact().getPhone()))
            throw new BizException(403, "手机号由主账号核验维护，不能自行变更登录身份");
        boolean owner = "owner".equals(who.contact().getRole());
        if (!owner && body.containsKey("warehouseName") && !Objects.equals(body.get("warehouseName"), who.warehouse().getName())) throw forbidden();
        if (!owner && contacts.update(null, new LambdaUpdateWrapper<MktWarehouseContact>().eq(MktWarehouseContact::getId,who.contact().getId())
                .eq(MktWarehouseContact::getStatus,1).set(MktWarehouseContact::getName,name)) != 1) throw conflict();
        if (owner) {
            identities.maintainOwner(who.warehouse().getId(),name,who.contact().getPhone(),false);
            var update = new LambdaUpdateWrapper<MktWarehouse>().eq(MktWarehouse::getId, who.warehouse().getId()).set(MktWarehouse::getContact,name);
            if (body.containsKey("warehouseName")) {
                String warehouseName = MktReferralRequirements.text(body.get("warehouseName"),100,"云仓名称");
                if (!StringUtils.hasText(warehouseName)) throw new BizException(400,"请填写云仓名称");
                update.set(MktWarehouse::getName,warehouseName);
            }
            if (warehouses.update(null,update) != 1) throw conflict();
        }
        audit.log("warehouse.contact.profile", "warehouse", who.warehouse().getId(), "更新本人姓名资料");
        return profile();
    }

    public List<Map<String,Object>> staff() {
        var who = owner();
        return contacts.selectList(new LambdaQueryWrapper<MktWarehouseContact>()
                .eq(MktWarehouseContact::getWarehouseId,who.warehouse().getId()).eq(MktWarehouseContact::getRole,"member")
                .orderByAsc(MktWarehouseContact::getId)).stream().map(this::staffView).toList();
    }

    @Transactional
    public Map<String,Object> saveStaff(Long id, Map<String,Object> body) {
        var who = owner();
        for (String key : body.keySet()) if (!Set.of("name", "phone", "status").contains(key)) throw new BizException(400,"只可维护业务员姓名、手机号及启停状态");
        String name = WhIdentityService.requireName((String)body.get("name"));
        String phone = WhIdentityService.requirePhone((String)body.get("phone"));
        Integer status = integer(body.get("status"),1);
        if (status != 0 && status != 1) throw new BizException(400,"业务员状态无效");
        identities.lockPhone(phone);
        warehouses.selectForUpdate(who.warehouse().getId());
        who = owner();
        MktWarehouseContact contact = id == null ? new MktWarehouseContact() : contacts.selectById(id);
        if (id != null && (contact == null || !Objects.equals(contact.getWarehouseId(),who.warehouse().getId()) || !"member".equals(contact.getRole()))) throw forbidden();
        identities.reservePhone(phone,null,id);
        if (id == null) {
            contact.setWarehouseId(who.warehouse().getId()); contact.setRole("member");
            contact.setName(name); contact.setPhone(phone); contact.setStatus(status);
            contact.setProjectId(who.warehouse().getProjectId()); contact.setTenantId(who.warehouse().getTenantId());
            contacts.insert(contact);
        } else {
            var update = new LambdaUpdateWrapper<MktWarehouseContact>().eq(MktWarehouseContact::getId,id)
                    .eq(MktWarehouseContact::getWarehouseId,who.warehouse().getId()).eq(MktWarehouseContact::getRole,"member")
                    .set(MktWarehouseContact::getName,name).set(MktWarehouseContact::getPhone,phone).set(MktWarehouseContact::getStatus,status);
            // A changed phone must be freshly verified; the old WeChat/token loses access immediately.
            if (!Objects.equals(contact.getPhone(),phone)) update.set(MktWarehouseContact::getOpenid,null).set(MktWarehouseContact::getUnionid,null);
            if (!Objects.equals(contact.getPhone(),phone) || !Objects.equals(contact.getStatus(),status)) update.setSql("auth_revision=auth_revision+1");
            if (contacts.update(null,update) != 1) throw conflict();
            contact = contacts.selectById(id);
        }
        audit.log("warehouse.staff.save", "warehouse", who.warehouse().getId(), "维护云仓业务员 " + contact.getId());
        return staffView(contact);
    }

    public LambdaQueryWrapper<Customer> customerScope(WhIdentityService.Identity who) {
        var w = who.warehouse();
        var q = new LambdaQueryWrapper<Customer>().eq(Customer::getAssignedWarehouseId,w.getId())
                .eq(w.getProjectId()!=null,Customer::getProjectId,w.getProjectId())
                .eq(w.getTenantId()!=null,Customer::getTenantId,w.getTenantId());
        if (!"owner".equals(who.contact().getRole())) q.eq(Customer::getWarehouseSalespersonId,who.contact().getId());
        return q;
    }

    public Customer requireCustomer(Long id, boolean lock) {
        var who = current();
        Customer customer = id == null ? null : lock ? customers.selectForUpdate(id) : customers.selectById(id);
        if (customer == null || !Objects.equals(customer.getAssignedWarehouseId(),who.warehouse().getId())
                || !sameScope(customer.getProjectId(),who.warehouse().getProjectId())
                || !sameScope(customer.getTenantId(),who.warehouse().getTenantId())
                || (!"owner".equals(who.contact().getRole()) && !Objects.equals(customer.getWarehouseSalespersonId(),who.contact().getId()))) throw forbidden();
        return customer;
    }

    public Map<String,Object> customerView(Customer customer) {
        Map<String,Object> out = assignments.warehouseView(customer);
        out.put("warehouseSalespersonId",customer.getWarehouseSalespersonId());
        out.put("warehouseSalesStage",customer.getWarehouseSalesStage() == null ? 0 : customer.getWarehouseSalesStage());
        MktWarehouseContact contact = customer.getWarehouseSalespersonId() == null ? null : contacts.selectById(customer.getWarehouseSalespersonId());
        if (contact != null && Objects.equals(contact.getWarehouseId(),customer.getAssignedWarehouseId())) {
            out.put("salespersonName",contact.getName()); out.put("salespersonStatus",contact.getStatus());
        }
        return out;
    }

    public Map<String,Object> detail(Long id) {
        Customer customer = requireCustomer(id,false);
        Map<String,Object> out = customerView(customer);
        if (List.of(1,2).contains(Objects.requireNonNullElse(customer.getWarehouseAssignmentStatus(),0)))
            out.put("quoteIntake",quoteIntake.submittedDetails(id,customer.getTenantId(),customer.getProjectId()));
        if (Integer.valueOf(2).equals(customer.getWarehouseAssignmentStatus())) out.put("phone",customer.getPhone());
        var who = current();
        out.put("isOwner","owner".equals(who.contact().getRole()));
        out.put("salesProgressHistory",jdbc.queryForList("SELECT id, stage, summary, actor_name AS actorName, create_time AS createTime FROM crm_warehouse_customer_progress WHERE warehouse_id=? AND customer_id=? AND tenant_id=? ORDER BY id DESC LIMIT 200",who.warehouse().getId(),id,tenant(who)));
        return out;
    }

    @Transactional
    public Map<String,Object> assignStaff(Long customerId, Long contactId) {
        var who = owner();
        Customer customer = requireCustomer(customerId,true);
        if (!Integer.valueOf(MktCustomerAssignmentService.ACCEPTED).equals(customer.getWarehouseAssignmentStatus()))
            throw new BizException(409,"请先由云仓主账号确认承接客户，再分配业务员");
        warehouses.selectForUpdate(who.warehouse().getId());
        who = owner();
        MktWarehouseContact contact = contactId == null ? null : contacts.selectById(contactId);
        if (contact != null && (!Objects.equals(contact.getWarehouseId(),who.warehouse().getId()) || !"member".equals(contact.getRole()) || !Integer.valueOf(1).equals(contact.getStatus()))) throw forbidden();
        if (contactId != null && contact == null) throw forbidden();
        if (Objects.equals(customer.getWarehouseSalespersonId(),contactId)) return detail(customerId);
        if (customers.update(null, version(customer).set(Customer::getWarehouseSalespersonId,contactId).set(Customer::getWarehouseSalesStage,0)) != 1) throw conflict();
        history(who,customerId,contactId,0,contact == null ? "主账号取消业务员分配" : "主账号分配业务员：" + contact.getName());
        progress.transition(customerId,0,contact == null ? "已取消业务员分配，等待重新安排" : "已分配业务员，等待承接需求", "salesperson_assigned");
        audit.log("warehouse.customer.staff", "customer", customerId, "更新云仓业务员分配");
        return detail(customerId);
    }

    @Transactional
    public void accept(Long id) {
        owner();
        requireCustomer(id,true);
        assignments.accept(id,WhAuthContext.currentWarehouseId());
    }

    @Transactional
    public void updateStage(Long id, Integer stage, String summary) {
        if (stage == null || stage < 1 || stage > 5) throw new BizException(400,"请选择业务员承接至正式发货的实际进度");
        var who = current();
        Customer customer = requireCustomer(id,true);
        if (customer.getWarehouseSalespersonId() == null) throw new BizException(409,"请主账号先分配业务员");
        if (!Integer.valueOf(2).equals(customer.getWarehouseAssignmentStatus())) throw new BizException(409,"请先确认承接客户");
        summary = MktReferralRequirements.text(summary,450,"跟进说明");
        String text = STAGES.get(stage) + (StringUtils.hasText(summary) ? "：" + summary : "");
        if (customers.update(null,version(customer).set(Customer::getWarehouseSalesStage,stage)) != 1) throw conflict();
        history(who,id,customer.getWarehouseSalespersonId(),stage,text);
        assignments.updateWarehouseProgress(id,who.warehouse().getId(),text,null);
    }

    private void history(WhIdentityService.Identity who, Long id, Long staffId, int stage, String summary) {
        if (jdbc.update("INSERT INTO crm_warehouse_customer_progress(warehouse_id,customer_id,salesperson_id,actor_contact_id,actor_name,stage,summary,tenant_id,create_time) VALUES(?,?,?,?,?,?,?,?,?)",
                who.warehouse().getId(),id,staffId,who.contact().getId(),who.contact().getName(),stage,summary,tenant(who),LocalDateTime.now()) != 1) throw conflict();
    }
    private static Long tenant(WhIdentityService.Identity who) { return who.warehouse().getTenantId() == null ? 1L : who.warehouse().getTenantId(); }
    private Map<String,Object> staffView(MktWarehouseContact contact) {
        Map<String,Object> out = new HashMap<>();
        out.put("id",contact.getId());out.put("name",contact.getName());out.put("phone",contact.getPhone());out.put("status",contact.getStatus());out.put("role",contact.getRole());
        out.put("wechatBound",StringUtils.hasText(contact.getOpenid()));
        return out;
    }
    private static boolean sameScope(Long a,Long b) { return b == null || Objects.equals(a,b); }
    private static LambdaUpdateWrapper<Customer> version(Customer c) { return new LambdaUpdateWrapper<Customer>().eq(Customer::getId,c.getId()).eq(c.getVersion()!=null,Customer::getVersion,c.getVersion()).setSql("version=version+1"); }
    public static Integer integer(Object raw,Integer fallback) {
        if (raw == null) return fallback;
        try { return new java.math.BigDecimal(raw.toString()).intValueExact(); } catch (Exception e) { throw new BizException(400,"参数必须为整数"); }
    }
    private static BizException forbidden() { return new BizException(403,"无权访问此云仓账号或客户"); }
    private static BizException conflict() { return new BizException(409,"资料已更新，请刷新后重试"); }
}
