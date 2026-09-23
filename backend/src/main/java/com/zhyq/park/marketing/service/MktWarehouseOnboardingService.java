package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseOnboarding;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseOnboardingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** 云仓加盟状态机：资质审核后选择人工订单模式或完成外部 ERP 接入，再签协议上线。
 * 人工订单模式保持 ERP 未连接；任何状态推进均使用数据库条件更新。
 */
@Service
@RequiredArgsConstructor
public class MktWarehouseOnboardingService {

    public static final int JS_APPLIED = 1;
    public static final int JS_QUALIFYING = 2;
    public static final int JS_ERP_CONNECTING = 3;
    public static final int JS_PENDING_AGREEMENT = 4;
    public static final int JS_ONLINE = 5;
    public static final int JS_PAUSED = 6;
    public static final int JS_EXITED = 7;

    public static final int ERP_NONE = 0;
    public static final int ERP_SANDBOX = 1;
    public static final int ERP_LIVE = 2;
    public static final int ERP_DISCONNECTED = 3;

    public static final int STEP_APPLY = 1;
    public static final int STEP_QUALIFY = 2;
    public static final int STEP_ERP = 3;
    public static final int STEP_AGREEMENT = 4;
    public static final int STEP_ONLINE = 5;

    static final int SS_PENDING = 0;
    static final int SS_DOING = 1;
    static final int SS_PASSED = 2;
    static final int SS_REJECTED = 3;
    private static final String BIZ_TYPE = "warehouse";

    private final MktWarehouseMapper warehouseMapper;
    private final MktWarehouseOnboardingMapper stepMapper;
    private final MktAuditService auditService;

    /** 新建云仓 = 提交加盟申请:join_status=1,建 5 行步骤,第 1 步直接通过、第 2 步进行中。 */
    @Transactional
    public MktWarehouse apply(MktWarehouse w) {
        if (!StringUtils.hasText(w.getCode()) || !StringUtils.hasText(w.getName())) {
            throw new BizException("云仓编码与名称必填");
        }
        if (!StringUtils.hasText(w.getFeeModel())) {
            w.setFeeModel(null);
        }
        w.setOrderMode("erp");
        w.setJoinStatus(JS_APPLIED);
        w.setErpStatus(ERP_NONE);
        warehouseMapper.insert(w);
        for (int step = STEP_APPLY; step <= STEP_ONLINE; step++) {
            MktWarehouseOnboarding s = new MktWarehouseOnboarding();
            s.setWarehouseId(w.getId());
            s.setStep(step);
            s.setStatus(step == STEP_APPLY ? SS_PASSED : step == STEP_QUALIFY ? SS_DOING : SS_PENDING);
            s.setDoneTime(step == STEP_APPLY ? LocalDateTime.now() : null);
            s.setProjectId(w.getProjectId());
            stepMapper.insert(s);
        }
        // 申请一提交就进入资质审核
        transition(w.getId(), JS_QUALIFYING, "warehouse.apply", null, JS_APPLIED);
        return w;
    }

    /** 资质审核通过后确认订单接入方式。 */
    @Transactional
    public void passQualification(Long warehouseId, Integer reviewedVersion) {
        if (reviewedVersion == null) throw new BizException("请刷新并查看最新申请资料后再审核");
        validateProfile(require(warehouseId));
        int changed = warehouseMapper.update(null, new LambdaUpdateWrapper<MktWarehouse>()
                .eq(MktWarehouse::getId, warehouseId).eq(MktWarehouse::getVersion, reviewedVersion)
                .in(MktWarehouse::getJoinStatus, JS_QUALIFYING)
                .set(MktWarehouse::getJoinStatus, JS_ERP_CONNECTING).setSql("version = version + 1"));
        if (changed != 1) throw new BizException("申请资料或审核状态已变化，请重新查看最新资料后审核");
        auditService.log("warehouse.qualify.pass", BIZ_TYPE, warehouseId, null);
        passStep(warehouseId, STEP_QUALIFY);
        startStep(warehouseId, STEP_ERP);
    }

    /** 资质驳回 → 回到申请,可重提。 */
    @Transactional
    public void rejectQualification(Long warehouseId, String reason) {
        requireReason(reason);
        transition(warehouseId, JS_APPLIED, "warehouse.qualify.reject", reason, JS_QUALIFYING);
        rejectStep(warehouseId, STEP_QUALIFY, reason);
    }

    /** 只有真正的外部握手可以更新 ERP 状态，运营录单使用独立模式。 */
    @Transactional
    public void markErpConnected(Long warehouseId, String operator) {
        throw new BizException("不能人工标记 ERP 已联通；请配置人工导入订单模式，或完成真实 ERP 接入");
    }

    @Transactional
    public void useManualOrders(Long warehouseId) {
        int updated = warehouseMapper.update(null, new LambdaUpdateWrapper<MktWarehouse>()
                .eq(MktWarehouse::getId, warehouseId).eq(MktWarehouse::getJoinStatus, JS_ERP_CONNECTING)
                .set(MktWarehouse::getJoinStatus, JS_PENDING_AGREEMENT)
                .set(MktWarehouse::getOrderMode, "manual").set(MktWarehouse::getErpStatus, ERP_NONE));
        requireUpdated(updated, warehouseId);
        passStep(warehouseId, STEP_ERP);
        startStep(warehouseId, STEP_AGREEMENT);
        auditService.log("warehouse.orders.manual", BIZ_TYPE, warehouseId, "采用运营 Excel 导入出库单，未连接外部 ERP");
    }

    @Transactional
    public void resubmit(Long warehouseId) {
        validateProfile(require(warehouseId));
        transition(warehouseId, JS_QUALIFYING, "warehouse.resubmit", null, JS_APPLIED);
        stepMapper.update(null, new LambdaUpdateWrapper<MktWarehouseOnboarding>()
                .eq(MktWarehouseOnboarding::getWarehouseId, warehouseId)
                .eq(MktWarehouseOnboarding::getStep, STEP_QUALIFY)
                .set(MktWarehouseOnboarding::getStatus, SS_DOING)
                .set(MktWarehouseOnboarding::getRejectReason, null).set(MktWarehouseOnboarding::getDoneTime, null));
    }

    public static void validateProfile(MktWarehouse w) {
        requireText(w.getName(), "云仓名称", 100);
        requireText(w.getContact(), "联系人", 32);
        requireText(w.getRegion(), "所在区域", 64);
        requireText(w.getAddress(), "详细地址", 255);
        if (w.getPhone() == null || !w.getPhone().matches("^1\\d{10}$")) throw new BizException("请填写正确的 11 位手机号");
        if (w.getAreaSqm() != null && w.getAreaSqm().signum() < 0) throw new BizException("面积不能为负数");
        if (w.getDailyCapacity() != null && w.getDailyCapacity() < 0) throw new BizException("日处理单量不能为负数");
        if (w.getCategories() != null && w.getCategories().length() > 255) throw new BizException("经营品类不能超过 255 字");
        if (w.getRemark() != null && w.getRemark().length() > 500) throw new BizException("补充说明不能超过 500 字");
    }

    private static void requireText(String value, String name, int max) {
        if (!StringUtils.hasText(value) || value.trim().length() > max) throw new BizException(name + "必填且不能超过 " + max + " 字");
    }

    /** 核验订单模式后确认已签署协议并上线；人工模式不更改 ERP 状态为已连接。 */
    @Transactional
    public void signAgreement(Long warehouseId, String contractFile) {
        if (!StringUtils.hasText(contractFile)) {
            throw new BizException("请上传加盟协议签署件");
        }
        MktWarehouse current = require(warehouseId);
        boolean manual = "manual".equals(current.getOrderMode());
        if (!manual && !Integer.valueOf(ERP_LIVE).equals(current.getErpStatus())) throw new BizException("请先选择人工导入模式或完成真实 ERP 接入");
        int updated = warehouseMapper.update(null, new LambdaUpdateWrapper<MktWarehouse>()
                .eq(MktWarehouse::getId, warehouseId)
                .eq(MktWarehouse::getJoinStatus, JS_PENDING_AGREEMENT)
                .set(MktWarehouse::getJoinStatus, JS_ONLINE)
                .set(MktWarehouse::getErpStatus, manual ? ERP_NONE : ERP_LIVE)
                .set(MktWarehouse::getContractFile, contractFile));
        requireUpdated(updated, warehouseId);
        auditService.log("warehouse.online", BIZ_TYPE, warehouseId, "协议签署,上线");
        passStep(warehouseId, STEP_AGREEMENT);
        passStep(warehouseId, STEP_ONLINE);
    }

    @Transactional
    public void pause(Long warehouseId, String reason) {
        requireReason(reason);
        transition(warehouseId, JS_PAUSED, "warehouse.pause", reason, JS_ONLINE);
    }

    /** 恢复时须保有人工模式或正式 ERP 连接。 */
    @Transactional
    public void resume(Long warehouseId) {
        MktWarehouse w = require(warehouseId);
        if (!"manual".equals(w.getOrderMode()) && !Integer.valueOf(ERP_LIVE).equals(w.getErpStatus())) {
            throw new BizException("ERP 未联通,不能恢复上线");
        }
        transition(warehouseId, JS_ONLINE, "warehouse.resume", null, JS_PAUSED);
    }

    /** 退出:调用方需先确认在服客户 = 0 且结算清零(Controller 里查),这里只做状态。 */
    @Transactional
    public void exit(Long warehouseId, String reason) {
        requireReason(reason);
        transition(warehouseId, JS_EXITED, "warehouse.exit", reason, JS_ONLINE, JS_PAUSED);
    }

    /** 已上线且已选择可用订单模式的云仓可承接客户。 */
    public boolean canAcceptCustomers(MktWarehouse w) {
        return w != null && Integer.valueOf(JS_ONLINE).equals(w.getJoinStatus())
                && ("manual".equals(w.getOrderMode()) || Integer.valueOf(ERP_LIVE).equals(w.getErpStatus()));
    }

    // ---------------- 内部 ----------------

    private void transition(Long id, int to, String action, String reason, Integer... from) {
        int updated = warehouseMapper.update(null, new LambdaUpdateWrapper<MktWarehouse>()
                .eq(MktWarehouse::getId, id)
                .in(MktWarehouse::getJoinStatus, (Object[]) from)
                .set(MktWarehouse::getJoinStatus, to));
        requireUpdated(updated, id);
        auditService.log(action, BIZ_TYPE, id, reason);
    }

    private void passStep(Long warehouseId, int step) {
        stepMapper.update(null, new LambdaUpdateWrapper<MktWarehouseOnboarding>()
                .eq(MktWarehouseOnboarding::getWarehouseId, warehouseId)
                .eq(MktWarehouseOnboarding::getStep, step)
                .set(MktWarehouseOnboarding::getStatus, SS_PASSED)
                .set(MktWarehouseOnboarding::getDoneTime, LocalDateTime.now()));
    }

    private void startStep(Long warehouseId, int step) {
        stepMapper.update(null, new LambdaUpdateWrapper<MktWarehouseOnboarding>()
                .eq(MktWarehouseOnboarding::getWarehouseId, warehouseId)
                .eq(MktWarehouseOnboarding::getStep, step)
                .set(MktWarehouseOnboarding::getStatus, SS_DOING));
    }

    private void rejectStep(Long warehouseId, int step, String reason) {
        stepMapper.update(null, new LambdaUpdateWrapper<MktWarehouseOnboarding>()
                .eq(MktWarehouseOnboarding::getWarehouseId, warehouseId)
                .eq(MktWarehouseOnboarding::getStep, step)
                .set(MktWarehouseOnboarding::getStatus, SS_REJECTED)
                .set(MktWarehouseOnboarding::getRejectReason, reason));
    }

    private MktWarehouse require(Long id) {
        MktWarehouse w = warehouseMapper.selectById(id);
        if (w == null) {
            throw new BizException("云仓不存在: " + id);
        }
        return w;
    }

    private static void requireUpdated(int updated, Long id) {
        if (updated == 0) {
            throw new BizException("云仓 " + id + " 状态已变化,请刷新后重试");
        }
    }

    private static void requireReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BizException("必须填写原因");
        }
    }

    /** 供 Controller 查询步骤时间线。 */
    public LambdaQueryWrapper<MktWarehouseOnboarding> stepsOf(Long warehouseId) {
        return new LambdaQueryWrapper<MktWarehouseOnboarding>()
                .eq(MktWarehouseOnboarding::getWarehouseId, warehouseId)
                .orderByAsc(MktWarehouseOnboarding::getStep);
    }
}
