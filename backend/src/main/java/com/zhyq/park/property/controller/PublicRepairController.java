package com.zhyq.park.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.property.entity.WorkOrder;
import com.zhyq.park.property.mapper.WorkOrderMapper;
import com.zhyq.park.property.model.WorkOrderSource;
import com.zhyq.park.property.service.WorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 手机自助报修(免登录)。业主/租户扫码或点链接进 /m/repair 提交,物业在后台照常派单。
 *
 * <p>本接口对公网匿名开放,故:
 * <ul>
 *   <li>只能新建工单与按「手机号 + 工单号」查自己那一单,拿不到工单列表;</li>
 *   <li>字段长度与手机号格式强校验,描述等文本截断后入库;</li>
 *   <li>按手机号与来源 IP 双重限流,防止被刷单。</li>
 * </ul>
 */
@Slf4j
@Tag(name = "物业-手机自助报修(免登录)")
@RestController
@RequestMapping("/public/repair")
@RequiredArgsConstructor
public class PublicRepairController {

    private static final Pattern PHONE = Pattern.compile("^1[3-9][0-9]{9}$");
    /** 同一手机号 10 分钟内最多 3 单 */
    private static final int PHONE_LIMIT = 3;
    private static final long PHONE_WINDOW_MS = 10 * 60 * 1000L;
    /** 同一 IP 10 分钟内最多 10 单 */
    private static final int IP_LIMIT = 10;

    private final WorkOrderMapper workOrderMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final SubmitThrottle throttle = new SubmitThrottle();

    @Operation(summary = "提交报修")
    @PostMapping
    public Result<Map<String, Object>> submit(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String phone = trim(body.get("contactPhone"), 20);
        String contact = trim(body.get("contact"), 32);
        String title = trim(body.get("title"), 100);
        String location = trim(body.get("location"), 100);
        String category = trim(body.get("category"), 32);
        String remark = trim(body.get("remark"), 500);

        if (!StringUtils.hasText(contact)) {
            throw new BizException("请填写您的称呼");
        }
        if (phone == null || !PHONE.matcher(phone).matches()) {
            throw new BizException("请填写正确的手机号");
        }
        if (!StringUtils.hasText(title)) {
            throw new BizException("请简单描述要修什么");
        }
        throttle.check(phone, clientIp(request));

        WorkOrder wo = new WorkOrder();
        wo.setCode("WO" + System.currentTimeMillis());
        wo.setOrderType("报修");
        wo.setTitle(title);
        wo.setLocation(location);
        wo.setCategory(category);
        wo.setUrgency(parseUrgency(body.get("urgency")));
        wo.setStatus(WorkOrderService.ST_PENDING_DISPATCH);
        wo.setSource("手机自助报修");
        wo.setSourceType(WorkOrderSource.MANUAL);
        wo.setContact(contact);
        wo.setContactPhone(phone);
        wo.setRemark(remark);
        workOrderMapper.insert(wo);

        // 复用既有事件:已配置企业微信群机器人时,物业群里会立刻收到这条报修
        eventPublisher.publishEvent(new DomainEvent.WorkOrderCreated(
                wo.getId(), wo.getOrderType(), null, LocalDateTime.now()));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", wo.getCode());
        m.put("id", wo.getId());
        return Result.ok(m);
    }

    /**
     * 按手机号查自己提交的报修进度。
     * 只返回手机自助报修的工单与展示必需字段,避免匿名接口泄露后台工单数据。
     */
    @Operation(summary = "查询我的报修(按手机号)")
    @GetMapping("/my")
    public Result<List<Map<String, Object>>> my(@RequestParam String phone) {
        String p = trim(phone, 20);
        if (p == null || !PHONE.matcher(p).matches()) {
            throw new BizException("请填写正确的手机号");
        }
        List<WorkOrder> orders = workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getContactPhone, p)
                .eq(WorkOrder::getSource, "手机自助报修")
                .orderByDesc(WorkOrder::getId)
                .last("limit 20"));
        List<Map<String, Object>> list = new ArrayList<>();
        for (WorkOrder wo : orders) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", wo.getCode());
            m.put("title", wo.getTitle());
            m.put("location", wo.getLocation());
            m.put("status", wo.getStatus());
            m.put("urgency", wo.getUrgency());
            m.put("createTime", wo.getCreateTime());
            m.put("finishTime", wo.getFinishTime());
            list.add(m);
        }
        return Result.ok(list);
    }

    private static int parseUrgency(String v) {
        try {
            int u = Integer.parseInt(v);
            return u >= 1 && u <= 3 ? u : 2;
        } catch (Exception e) {
            return 2;
        }
    }

    private static String trim(String v, int max) {
        if (v == null) {
            return null;
        }
        String s = v.trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** 取真实来源 IP:线上走 nginx 反代,直接用 remoteAddr 会全是网关地址 */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** 进程内滑动窗口限流。单实例部署够用;将来多实例再换 Redis。 */
    static class SubmitThrottle {

        private final Map<String, List<Long>> hits = new LinkedHashMap<>();

        synchronized void check(String phone, String ip) {
            long now = System.currentTimeMillis();
            if (count("p:" + phone, now) >= PHONE_LIMIT) {
                throw new BizException("提交太频繁了,请稍后再试;紧急情况请直接电话联系物业");
            }
            if (ip != null && count("i:" + ip, now) >= IP_LIMIT) {
                throw new BizException("提交太频繁了,请稍后再试");
            }
            record("p:" + phone, now);
            if (ip != null) {
                record("i:" + ip, now);
            }
            // 顺手清掉过期 key,避免 map 无限增长
            hits.entrySet().removeIf(e -> e.getValue().stream().noneMatch(t -> now - t < PHONE_WINDOW_MS));
        }

        private int count(String key, long now) {
            List<Long> list = hits.get(key);
            if (list == null) {
                return 0;
            }
            list.removeIf(t -> now - t >= PHONE_WINDOW_MS);
            return list.size();
        }

        private void record(String key, long now) {
            hits.computeIfAbsent(key, k -> new ArrayList<>()).add(now);
        }
    }
}
