package com.zhyq.park.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.contract.entity.ApprovalRef;
import com.zhyq.park.contract.entity.Contract;
import com.zhyq.park.contract.entity.ContractRoom;
import com.zhyq.park.contract.entity.ContractVersion;
import com.zhyq.park.contract.entity.FinBill;
import com.zhyq.park.contract.entity.RoomRef;
import com.zhyq.park.contract.mapper.ApprovalRefMapper;
import com.zhyq.park.contract.mapper.ContractMapper;
import com.zhyq.park.contract.mapper.ContractRoomMapper;
import com.zhyq.park.contract.mapper.ContractVersionMapper;
import com.zhyq.park.contract.mapper.FinBillMapper;
import com.zhyq.park.contract.mapper.RoomRefMapper;
import com.zhyq.park.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
    private final FinBillMapper finBillMapper;
    private final RoomRefMapper roomRefMapper;
    private final ApprovalRefMapper approvalRefMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final WorkflowService workflowService;

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

        // ③ 生成周期账单计划
        generateBillPlan(c, rooms);

        // ④ 发布领域事件(保守:副作用已在上方同事务完成,本事件仅供下游感知;
        //    AFTER_COMMIT 消费,事务回滚则不发)
        eventPublisher.publishEvent(new DomainEvent.ContractApproved(
                c.getId(), c.getCode(), c.getTenantRefId(), c.getProjectId(), LocalDateTime.now()));
    }

    /**
     * 账单计划生成算法:
     * 从 start_date 到 end_date,按 pay_cycle(月数)切分周期,每周期一条 fin_bill。
     * - 整周期金额 = rent_price × rent_area × pay_cycle
     * - 尾期不足整周期:按实际天数占整周期天数的比例折算
     * - 免租期(free_months):与周期重叠的月数按比例扣减(支持部分免租)
     * - 保证金(deposit>0)额外生成一条 fee_type="保证金" 账单
     */
    private void generateBillPlan(Contract c, List<ContractRoom> rooms) {
        LocalDate start = c.getStartDate();
        LocalDate end = c.getEndDate();
        int cycle = (c.getPayCycle() == null || c.getPayCycle() < 1) ? 1 : c.getPayCycle();
        int freeMonths = c.getFreeMonths() == null ? 0 : c.getFreeMonths();
        BigDecimal rentPrice = c.getRentPrice() == null ? BigDecimal.ZERO : c.getRentPrice();
        BigDecimal rentArea = c.getRentArea() == null ? BigDecimal.ZERO : c.getRentArea();
        // 单个整周期租金 = 单价 × 面积 × 周期月数
        BigDecimal cycleAmount = rentPrice
                .multiply(rentArea)
                .multiply(BigDecimal.valueOf(cycle));

        Long roomId = rooms.isEmpty() ? null : rooms.get(0).getRoomId();
        // 账单号带合同 ID,避免不同合同同毫秒审批撞唯一键
        String codePrefix = "ZD" + System.currentTimeMillis() + "C" + c.getId() + "N";

        int seq = 0;
        LocalDate periodStart = start;
        int monthsElapsed = 0; // 该周期起点距合同起始已过的月数,用于免租重叠计算
        while (periodStart.isBefore(end)) {
            LocalDate fullPeriodEnd = periodStart.plusMonths(cycle); // 自然整周期终点
            LocalDate periodEnd = fullPeriodEnd.isAfter(end) ? end : fullPeriodEnd;

            BigDecimal amount = cycleAmount;

            // 尾期不足整周期 → 按天数比例折算
            if (periodEnd.isBefore(fullPeriodEnd)) {
                long fullDays = ChronoUnit.DAYS.between(periodStart, fullPeriodEnd);
                long actualDays = ChronoUnit.DAYS.between(periodStart, periodEnd);
                if (fullDays > 0) {
                    amount = amount.multiply(BigDecimal.valueOf(actualDays))
                            .divide(BigDecimal.valueOf(fullDays), 2, RoundingMode.HALF_UP);
                }
            }

            // 免租重叠月数:[monthsElapsed, monthsElapsed+cycle) 与 [0, freeMonths) 的交集
            int freeOverlap = Math.min(monthsElapsed + cycle, freeMonths) - monthsElapsed;
            if (freeOverlap > 0) {
                if (freeOverlap >= cycle) {
                    amount = BigDecimal.ZERO; // 整周期免租
                } else {
                    // 部分免租:按免租月数占周期月数比例扣减
                    amount = amount.multiply(BigDecimal.valueOf(cycle - freeOverlap))
                            .divide(BigDecimal.valueOf(cycle), 2, RoundingMode.HALF_UP);
                }
            }

            FinBill bill = new FinBill();
            bill.setCode(codePrefix + (++seq));
            bill.setContractId(c.getId());
            bill.setTenantRefId(c.getTenantRefId());
            bill.setProjectId(c.getProjectId());
            bill.setRoomId(roomId);
            bill.setDirection(1);
            bill.setFeeType("租金");
            bill.setSource("合同计划");
            bill.setStatus(3); // 待收付
            bill.setAmount(amount);
            bill.setPeriodStart(periodStart);
            bill.setPeriodEnd(periodEnd);
            bill.setDueDate(periodStart);
            finBillMapper.insert(bill);

            periodStart = fullPeriodEnd;
            monthsElapsed += cycle;
        }

        // 保证金账单
        BigDecimal deposit = c.getDeposit();
        if (deposit != null && deposit.compareTo(BigDecimal.ZERO) > 0) {
            FinBill depositBill = new FinBill();
            depositBill.setCode(codePrefix + (++seq));
            depositBill.setContractId(c.getId());
            depositBill.setTenantRefId(c.getTenantRefId());
            depositBill.setProjectId(c.getProjectId());
            depositBill.setRoomId(roomId);
            depositBill.setDirection(1);
            depositBill.setFeeType("保证金");
            depositBill.setSource("合同计划");
            depositBill.setStatus(3);
            depositBill.setAmount(deposit);
            depositBill.setPeriodStart(start);
            depositBill.setPeriodEnd(start);
            depositBill.setDueDate(start);
            finBillMapper.insert(depositBill);
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

        // 关联房源改回可租
        List<ContractRoom> rooms = contractRoomMapper.selectList(
                new LambdaQueryWrapper<ContractRoom>().eq(ContractRoom::getContractId, id));
        for (ContractRoom cr : rooms) {
            if (cr.getRoomId() == null) {
                continue;
            }
            RoomRef room = new RoomRef();
            room.setId(cr.getRoomId());
            room.setStatus(ROOM_RENTABLE);
            roomRefMapper.updateById(room);
        }

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
