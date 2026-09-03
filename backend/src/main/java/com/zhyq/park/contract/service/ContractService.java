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
    private static final int ST_TERMINATED = 9; // 已终止

    // 房源状态
    private static final int ROOM_RENTABLE = 1; // 可租
    private static final int ROOM_RENTED = 5;   // 在租

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

        // ④ 发布领域事件(保守:副作用已在上方同事务完成,本事件仅供下游感知;
        //    AFTER_COMMIT 消费,事务回滚则不发)
        eventPublisher.publishEvent(new DomainEvent.ContractApproved(
                c.getId(), c.getCode(), c.getTenantRefId(), c.getProjectId(), LocalDateTime.now()));
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
     * 仅执行中的合同可退租;条件更新抢状态,重复退租只有一次生效。
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
                .eq(Contract::getStatus, ST_RUNNING)
                .set(Contract::getStatus, ST_TERMINATED)
                .set(Contract::getTerminateDate, today));
        if (updated == 0) {
            throw new BizException("仅执行中的合同可退租");
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
