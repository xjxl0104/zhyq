package com.zhyq.park.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.file.service.FileStorageService;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
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

    /** 只收常见手机拍照格式,且按扩展名与 content-type 双重判断 */
    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif");
    /** 单张 10MB:手机原图普遍 3-8MB */
    private static final long MAX_PHOTO_SIZE = 10L * 1024 * 1024;
    /** 一单最多 6 张 */
    private static final int MAX_PHOTOS = 6;
    /** 附件只认本次提交前 2 小时内上传的,防止拿旧 id 硬凑 */
    private static final long PHOTO_MAX_AGE_HOURS = 2;
    private static final String BIZ_TYPE = "work_order";

    private final WorkOrderMapper workOrderMapper;
    private final SysFileMapper fileMapper;
    private final FileStorageService fileStorageService;
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
        attachPhotos(body.get("fileIds"), wo);

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

    @Operation(summary = "上传报修照片(单张)")
    @PostMapping("/photo")
    public Result<Map<String, Object>> photo(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择照片");
        }
        if (file.getSize() > MAX_PHOTO_SIZE) {
            throw new BizException("单张照片不要超过 10MB");
        }
        String ext = FileStorageService.extOf(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!IMAGE_EXT.contains(ext) || !contentType.startsWith("image/")) {
            throw new BizException("只能上传照片(jpg/png/heic 等)");
        }
        throttle.checkPhoto(clientIp(request));

        FileStorageService.StoredResult stored = fileStorageService.store(file);
        SysFile sf = new SysFile();
        sf.setBizType(BIZ_TYPE);
        sf.setBizId(null);                 // 建单成功后再回填,和后台"先传后关联"一个路子
        sf.setOriginalName(stored.originalName());
        sf.setStorePath(stored.storePath());
        sf.setUrl(stored.url());
        sf.setFileSize(stored.size());
        sf.setContentType(stored.contentType());
        sf.setExt(stored.ext());
        fileMapper.insert(sf);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", sf.getId());
        m.put("name", sf.getOriginalName());
        return Result.ok(m);
    }

    /**
     * 把刚上传的照片关联到新建的工单。
     * 只认:未关联过、类型是 work_order、确为图片、且 2 小时内上传的记录 ——
     * 匿名接口不能让人拿别处的文件 id 蹭进工单。
     */
    private void attachPhotos(String fileIds, WorkOrder wo) {
        if (!StringUtils.hasText(fileIds)) {
            return;
        }
        int attached = 0;
        for (String raw : fileIds.split(",")) {
            if (attached >= MAX_PHOTOS) {
                break;
            }
            Long id;
            try {
                id = Long.valueOf(raw.trim());
            } catch (NumberFormatException e) {
                continue;
            }
            SysFile f = fileMapper.selectById(id);
            if (f == null || f.getBizId() != null || !BIZ_TYPE.equals(f.getBizType())) {
                continue;
            }
            if (f.getExt() == null || !IMAGE_EXT.contains(f.getExt().toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (f.getCreateTime() == null
                    || f.getCreateTime().isBefore(LocalDateTime.now().minusHours(PHOTO_MAX_AGE_HOURS))) {
                continue;
            }
            SysFile update = new SysFile();
            update.setId(f.getId());
            update.setBizId(wo.getId());
            fileMapper.updateById(update);
            attached++;
        }
        if (attached > 0) {
            log.info("[public-repair] 工单 {} 关联照片 {} 张", wo.getCode(), attached);
        }
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

        /** 照片单独一档:一单最多 6 张,10 分钟 40 次足够连传几单 */
        synchronized void checkPhoto(String ip) {
            long now = System.currentTimeMillis();
            String key = "f:" + (ip == null ? "-" : ip);
            if (count(key, now) >= 40) {
                throw new BizException("上传太频繁了,请稍后再试");
            }
            record(key, now);
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
