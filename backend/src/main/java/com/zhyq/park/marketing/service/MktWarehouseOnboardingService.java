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

/**
 * 云仓加盟状态机(PARK-MKT-001 §2.5,v7 顺序:先打通 ERP 再签协议):
 * join_status 1申请 → 2资质审核 → 3ERP对接中 → 4待签协议 → 5已上线;5 ↔ 6暂停;5/6 → 7退出。
 * 每一步在 crm_warehouse_onboarding 里留一行(step 1–5,status 0待处理 1进行中 2通过 3驳回)。
 *
 * <p>阶段 A 没有真实 ERP 接入,第 3 步由运营「人工标记已联通」({@link #markErpConnected})推进;
 * 阶段 C 接开放接口后改成自动验收。</p>
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

    /** 资质审核通过 → ERP 对接中(签发沙箱凭证是阶段 C 的事,这里只推进状态)。 */
    @Transactional
    public void passQualification(Long warehouseId) {
        transition(warehouseId, JS_ERP_CONNECTING, "warehouse.qualify.pass", null, JS_QUALIFYING);
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

    /** 阶段 A:运营人工标记 ERP 已联通(沙箱)→ 待签协议。 */
    @Transactional
    public void markErpConnected(Long warehouseId, String operator) {
        int updated = warehouseMapper.update(null, new LambdaUpdateWrapper<MktWarehouse>()
                .eq(MktWarehouse::getId, warehouseId)
                .eq(MktWarehouse::getJoinStatus, JS_ERP_CONNECTING)
                .set(MktWarehouse::getJoinStatus, JS_PENDING_AGREEMENT)
                .set(MktWarehouse::getErpStatus, ERP_SANDBOX)
                .set(MktWarehouse::getErpMarkedBy, operator)
                .set(MktWarehouse::getErpMarkedAt, LocalDateTime.now()));
        requireUpdated(updated, warehouseId);
        auditService.log("warehouse.erp.mark", BIZ_TYPE, warehouseId, "人工标记 ERP 已联通(沙箱)");
        passStep(warehouseId, STEP_ERP);
        startStep(warehouseId, STEP_AGREEMENT);
    }

    /** 协议签署(线下上传)→ 已上线,ERP 切正式。 */
    @Transactional
    public void signAgreement(Long warehouseId, String contractFile) {
        if (!StringUtils.hasText(contractFile)) {
            throw new BizException("请上传加盟协议签署件");
        }
        int updated = warehouseMapper.update(null, new LambdaUpdateWrapper<MktWarehouse>()
                .eq(MktWarehouse::getId, warehouseId)
                .eq(MktWarehouse::getJoinStatus, JS_PENDING_AGREEMENT)
                .set(MktWarehouse::getJoinStatus, JS_ONLINE)
                .set(MktWarehouse::getErpStatus, ERP_LIVE)
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

    /** 恢复:必须 ERP 仍联通(§2.5)。 */
    @Transactional
    public void resume(Long warehouseId) {
        MktWarehouse w = require(warehouseId);
        if (w.getErpStatus() == null || w.getErpStatus() == ERP_DISCONNECTED || w.getErpStatus() == ERP_NONE) {
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

    /** 只有已上线且 ERP 联通的云仓可承接客户(分配云仓下拉用)。 */
    public boolean canAcceptCustomers(MktWarehouse w) {
        return w != null && Integer.valueOf(JS_ONLINE).equals(w.getJoinStatus())
                && w.getErpStatus() != null && (w.getErpStatus() == ERP_SANDBOX || w.getErpStatus() == ERP_LIVE);
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
