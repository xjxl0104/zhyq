package com.zhyq.park.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.contract.entity.ApprovalRef;
import com.zhyq.park.contract.entity.Contract;
import com.zhyq.park.contract.entity.ContractRoom;
import com.zhyq.park.contract.entity.ContractVersion;
import com.zhyq.park.contract.entity.RoomRef;
import com.zhyq.park.contract.mapper.ApprovalRefMapper;
import com.zhyq.park.contract.mapper.ContractMapper;
import com.zhyq.park.contract.mapper.ContractRoomMapper;
import com.zhyq.park.contract.mapper.ContractVersionMapper;
import com.zhyq.park.contract.mapper.RoomRefMapper;
import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.mapper.ReceivableRegisterMapper;
import com.zhyq.park.receivable.service.ReceivablePlanService;
import com.zhyq.park.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 合同全生命周期业务:提交审批、审批通过(生成房源在租 + 周期账单计划)、退租。
 * 状态流转一律用「条件 UPDATE 抢状态」保证并发下只有一个请求生效(乐观锁替代方案)。
 */
@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractMapper contractMapper;
    private final ContractRoomMapper contractRoomMapper;
    private final ContractVersionMapper contractVersionMapper;
    private final RoomRefMapper roomRefMapper;
    private final ApprovalRefMapper approvalRefMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final WorkflowService workflowService;
    private final ReceivableRegisterMapper receivableRegisterMapper;
    private final ReceivablePlanService receivablePlanService;

    // 合同状态
    private static final int ST_DRAFT = 1;      // 草稿
    private static final int ST_AUDITING = 2;   // 待审核
    private static final int ST_RUNNING = 5;    // 执行中
    private static final int ST_EXPIRED = 8;    // 到期仍需实际办理退租
    private static final int ST_TERMINATED = 9; // 已终止

    // 房源状态
    private static final int ROOM_RENTABLE = 1; // 可租
    private static final int ROOM_RENTED = 5;   // 在租

    /** 普通编辑只能修改草稿条款，生命周期必须经过领域动作。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateDraft(Contract request) {
        Contract current = contractMapper.selectById(request.getId());
        if (current == null) throw new BizException("合同不存在");
        if (!Integer.valueOf(ST_DRAFT).equals(current.getStatus())) throw new BizException("仅草稿合同可编辑");
        if (request.getStatus() != null && !Integer.valueOf(ST_DRAFT).equals(request.getStatus()))
            throw new BizException("合同状态须通过提交、审批或退租流程修改");
        if (request.getVersion() != null && !request.getVersion().equals(current.getVersion()))
            throw new BizException("合同已变化，请刷新后重试");
        LambdaUpdateWrapper<Contract> update = new LambdaUpdateWrapper<Contract>()
                .eq(Contract::getId, current.getId()).eq(Contract::getStatus, ST_DRAFT)
                .eq(Contract::getVersion, current.getVersion()).setSql("version = version + 1")
                .set(request.getCode() != null, Contract::getCode, request.getCode())
                .set(request.getTenantRefId() != null, Contract::getTenantRefId, request.getTenantRefId())
                .set(request.getProjectId() != null, Contract::getProjectId, request.getProjectId())
                .set(request.getContractType() != null, Contract::getContractType, request.getContractType())
                .set(request.getStartDate() != null, Contract::getStartDate, request.getStartDate())
                .set(request.getEndDate() != null, Contract::getEndDate, request.getEndDate())
                .set(request.getSignDate() != null, Contract::getSignDate, request.getSignDate())
                .set(request.getRentPrice() != null, Contract::getRentPrice, request.getRentPrice())
                .set(request.getPropertyPrice() != null, Contract::getPropertyPrice, request.getPropertyPrice())
                .set(request.getRentArea() != null, Contract::getRentArea, request.getRentArea())
                .set(request.getDeposit() != null, Contract::getDeposit, request.getDeposit())
                .set(request.getChargeMode() != null, Contract::getChargeMode, request.getChargeMode())
                .set(request.getPayCycle() != null, Contract::getPayCycle, request.getPayCycle())
                .set(request.getFreeMonths() != null, Contract::getFreeMonths, request.getFreeMonths())
                .set(request.getIncreaseRate() != null, Contract::getIncreaseRate, request.getIncreaseRate())
                .set(request.getGrade() != null, Contract::getGrade, request.getGrade())
                .set(request.getRemark() != null, Contract::getRemark, request.getRemark());
        if (contractMapper.update(null, update) != 1) throw new BizException("合同状态已变化，请刷新后重试");
    }

    /**
     * 提交审批:草稿(1)→待审核(2),并写入一条审批中心待办(biz_approval,审批中)。
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        Contract c = contractMapper.selectById(id);
        if (c == null) {
            throw new BizException("合同不存在");
        }
        // 条件更新抢状态:仅当当前仍是草稿才会更新成功
        int updated = contractMapper.update(null, new LambdaUpdateWrapper<Contract>()
                .eq(Contract::getId, id)
                .eq(Contract::getStatus, ST_DRAFT)
                .set(Contract::getStatus, ST_AUDITING));
        if (updated == 0) {
            throw new BizException("仅草稿状态的合同可提交审批");
        }
        // 抢状态成功后写入审批单(审批中心据此展示待办);并发下抢到的只有一个,不会重复插入
        ApprovalRef approval = new ApprovalRef();
        approval.setBizType("contract");
        approval.setBizId(id);
        approval.setTitle("合同" + c.getCode() + "审批");
        approval.setStatus(2); // 审批中
        approval.setApplyBy("system");
        approvalRefMapper.insert(approval);

        eventPublisher.publishEvent(new DomainEvent.ContractSubmitted(id, c.getCode(), LocalDateTime.now()));

        // 发起审批链(叠加在合同审批之上的前置流程)。未配启用流程定义时 start() 直接返回,降级为旧单节点审批。
        // 传入 approval.getId() 作单据头(D1-方案A)。start 内部只用 wf_* 表 + 发事件,不回调 submit,无循环依赖。
        workflowService.start("contract", id, approval.getId());
    }

    /**
     * 审批通过:待审核(2)→执行中(5)。
     * 同时:① 关联房源状态改为在租(5);② 生成周期账单计划写入 fin_bill。
     * 先抢状态再做副作用,并发下重复 approve 只有一个能成功,不会重复生成账单。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        Contract c = contractMapper.selectById(id);
        if (c == null) {
            throw new BizException("合同不存在");
        }
        // 审批前校验合同基础数据,避免生成空账单后合同却进入执行中
        if (c.getStartDate() == null || c.getEndDate() == null || !c.getStartDate().isBefore(c.getEndDate())) {
            throw new BizException("合同起止日期缺失或起始日不早于结束日,无法审批");
        }
        List<ReceivableRegister> registers = receivableRegisterMapper.selectList(
                new LambdaQueryWrapper<ReceivableRegister>()
                        .eq(ReceivableRegister::getContractId, id)
                        .in(ReceivableRegister::getStatus, "CONFIRMED", "ACTIVE"));
        if (registers.isEmpty()) {
            throw new BizException("合同审批前必须关联至少一条已确认的应收登记表");
        }

        // ① 条件更新抢状态(并发防重的关键步骤,先于一切副作用)
        int updated = contractMapper.update(null, new LambdaUpdateWrapper<Contract>()
                .eq(Contract::getId, id)
                .eq(Contract::getStatus, ST_AUDITING)
                .set(Contract::getStatus, ST_RUNNING));
        if (updated == 0) {
            throw new BizException("仅待审核状态的合同可审批通过");
        }

        // ② 关联房源改为在租
        List<ContractRoom> rooms = contractRoomMapper.selectList(
                new LambdaQueryWrapper<ContractRoom>().eq(ContractRoom::getContractId, id));
        for (ContractRoom cr : rooms) {
            if (cr.getRoomId() == null) {
                continue;
            }
            RoomRef room = new RoomRef();
            room.setId(cr.getRoomId());
            room.setStatus(ROOM_RENTED);
            roomRefMapper.updateById(room);
        }

        // ③ 按权威应收登记表生成租金、物业费和两类保证金分账计划
        registers.forEach(register -> receivablePlanService.generate(register.getId()));

        // ④ 营销计佣等业务监听与本次审批同事务；通知类监听在提交后消费。
        eventPublisher.publishEvent(new DomainEvent.ContractApproved(
                c.getId(), c.getCode(), c.getTenantRefId(), c.getProjectId(), LocalDateTime.now()));
    }

    /** 审批驳回后回到草稿；工作流、审批单和合同在同一事务内恢复可编辑状态。 */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id) {
        int updated = contractMapper.update(null, new LambdaUpdateWrapper<Contract>()
                .eq(Contract::getId, id).eq(Contract::getStatus, ST_AUDITING)
                .set(Contract::getStatus, ST_DRAFT));
        if (updated != 1) throw new BizException("仅待审核合同可驳回，请刷新后重试");
    }

    /**
     * 把合同关联的房源放回可租。退租与合同重置共用 —— 房源状态是全局资源,
     * 合同不再有效就必须释放,否则房源永远锁在一份已终止/已作废的合同上。
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseRooms(Long contractId) {
        List<ContractRoom> rooms = contractRoomMapper.selectList(
                new LambdaQueryWrapper<ContractRoom>().eq(ContractRoom::getContractId, contractId));
        for (ContractRoom cr : rooms) {
            if (cr.getRoomId() == null) {
                continue;
            }
            RoomRef room = new RoomRef();
            room.setId(cr.getRoomId());
            room.setStatus(ROOM_RENTABLE);
            roomRefMapper.updateById(room);
        }
    }

    /**
     * 退租:执行中(5)→已终止(9),terminate_date=今天;关联房源改回可租(1);记录一条退租版本。
     * 执行中或已到期的合同可退租;条件更新抢状态,重复退租只有一次生效。
     */
    @Transactional(rollbackFor = Exception.class)
    public void terminate(Long id) {
        Contract c = contractMapper.selectById(id);
        if (c == null) {
            throw new BizException("合同不存在");
        }
        LocalDate today = LocalDate.now();

        int updated = contractMapper.update(null, new LambdaUpdateWrapper<Contract>()
                .eq(Contract::getId, id)
                .in(Contract::getStatus, ST_RUNNING, ST_EXPIRED)
                .set(Contract::getStatus, ST_TERMINATED)
                .set(Contract::getTerminateDate, today));
        if (updated == 0) {
            throw new BizException("仅执行中或已到期的合同可办理退租");
        }

        releaseRooms(id);

        // 记录退租版本
        Long maxVersion = contractVersionMapper.selectCount(
                new LambdaQueryWrapper<ContractVersion>().eq(ContractVersion::getContractId, id));
        ContractVersion cv = new ContractVersion();
        cv.setContractId(id);
        cv.setVersionNo((maxVersion == null ? 0 : maxVersion.intValue()) + 1);
        cv.setChangeType("退租");
        cv.setEffectDate(today);
        contractVersionMapper.insert(cv);

        // 发布退租事件(供下游感知,不改既有逻辑)
        eventPublisher.publishEvent(new DomainEvent.ContractTerminated(
                c.getId(), c.getCode(), c.getTenantRefId(), c.getProjectId(), LocalDateTime.now()));
    }
}
