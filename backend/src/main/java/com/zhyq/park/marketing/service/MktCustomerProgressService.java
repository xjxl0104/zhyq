package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 伙伴可见进度：快照和追加历史一起提交，不记录内部备注或操作人个人信息。 */
@Service
@RequiredArgsConstructor
public class MktCustomerProgressService {
    private static final int HISTORY_LIMIT = 100;
    private final CustomerMapper customers;
    private final JdbcTemplate jdbc;

    @Transactional
    public void created(Long customerId) {
        record(requireLocked(customerId), 0, "客户需求已提交，等待服务对接", "submitted", "referral");
    }

    @Transactional
    public void transition(Long customerId, int percentage, String summary, String stage) {
        record(requireLocked(customerId), percentage, summary, stage, "system");
    }

    @Transactional
    public void publish(Long customerId, String summary, Integer percentage, String source) {
        Customer customer = requireLocked(customerId);
        if (!StringUtils.hasText(summary) || summary.trim().length() > 500)
            throw new BizException("请填写可向伙伴公开的跟进进度（1–500 字）");
        boolean signed = Integer.valueOf(2).equals(customer.getStatus());
        if (percentage != null && (percentage < 0 || percentage > (signed ? 100 : 99)))
            throw new BizException("未签约客户进度可填写 0–99%，签约后自动更新为 100%");
        int next = signed ? 100 : percentage == null ? Math.min(currentPercentage(customer), 99) : percentage;
        record(customer, next, summary.trim(), signed ? "signed" : "following", source);
    }

    public Map<String,Object> view(Customer customer) {
        Map<String,Object> out = new HashMap<>();
        out.put("progressPercentage", currentPercentage(customer));
        List<Map<String,Object>> history = jdbc.query(
                "SELECT id,percentage,summary,stage,source,create_time FROM crm_customer_progress_history "
                        + "WHERE customer_id=? AND tenant_id=? ORDER BY create_time DESC,id DESC LIMIT " + (HISTORY_LIMIT + 1),
                (rs,n) -> {
                    Map<String,Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id")); row.put("percentage", rs.getObject("percentage", Integer.class));
                    row.put("summary", rs.getString("summary")); row.put("stage", rs.getString("stage"));
                    row.put("source", rs.getString("source"));
                    row.put("createdAt", rs.getTimestamp("create_time").toLocalDateTime());
                    return row;
                }, customer.getId(), tenant(customer));
        out.put("progressHistory", history.size() > HISTORY_LIMIT ? history.subList(0, HISTORY_LIMIT) : history);
        out.put("progressHistoryHasMore", history.size() > HISTORY_LIMIT);
        return out;
    }

    public static Integer requestPercentage(Object value) {
        if (value == null) return null;
        if (!(value instanceof Number)) throw new BizException("服务进度必须是整数百分比");
        try { return new BigDecimal(value.toString()).intValueExact(); }
        catch (ArithmeticException | NumberFormatException e) { throw new BizException("服务进度必须是整数百分比"); }
    }

    public static int stagePercentage(Customer customer) {
        if (Integer.valueOf(2).equals(customer.getStatus())) return 100;
        if (Integer.valueOf(MktCustomerAssignmentService.ACCEPTED).equals(customer.getWarehouseAssignmentStatus())) return 60;
        if (Integer.valueOf(MktCustomerAssignmentService.PENDING).equals(customer.getWarehouseAssignmentStatus())) return 30;
        return 0;
    }

    private int currentPercentage(Customer customer) {
        return customer.getProgressPercentage() == null ? stagePercentage(customer) : customer.getProgressPercentage();
    }

    private void record(Customer customer, int percentage, String summary, String stage, String source) {
        LocalDateTime now = LocalDateTime.now();
        int changed = customers.update(null, new LambdaUpdateWrapper<Customer>()
                .eq(Customer::getId, customer.getId())
                .eq(customer.getVersion() != null, Customer::getVersion, customer.getVersion())
                .set(Customer::getProgressPercentage, percentage)
                .set(Customer::getPublicProgress, summary)
                .set(Customer::getProgressUpdatedAt, now).setSql("version = version + 1"));
        if (changed != 1) throw new BizException(409, "客户进度已变化，请刷新后重试");
        if (jdbc.update("INSERT INTO crm_customer_progress_history(customer_id,tenant_id,percentage,summary,stage,source,create_time) "
                        + "VALUES (?,?,?,?,?,?,?)", customer.getId(), tenant(customer), percentage, summary, stage, source, now) != 1)
            throw new BizException("进度记录保存失败，请重试");
    }

    private Customer requireLocked(Long customerId) {
        Customer customer = customerId == null ? null : customers.selectForUpdate(customerId);
        if (customer == null) throw new BizException("客户不存在");
        return customer;
    }

    private static long tenant(Customer customer) {
        return customer.getTenantId() == null ? com.zhyq.park.common.config.MyMetaObjectHandler.DEFAULT_TENANT_ID : customer.getTenantId();
    }
}
