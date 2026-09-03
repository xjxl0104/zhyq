package com.zhyq.park.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.mapper.ReceivableRegisterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 逾期与滞纳金计算(从 BillController 抽出)。
 *
 * <p>手动端点(POST /finance/bill/calcLateFee)与每日自愈任务
 * {@code ReceivableBillSyncJob} 共用同一份实现 —— 逾期状态与滞纳金从此不靠人点按钮,
 * 登记表生成的账单到期后自动进入逾期口径。</p>
 *
 * <p>万分之五/天,基数为剩余本金欠款(amount - paid),每次全量重算,重复调用幂等。</p>
 */
@Service
@RequiredArgsConstructor
public class LateFeeService {

    private static final BigDecimal DAILY_RATE = new BigDecimal("0.0005");

    private final BillMapper billMapper;
    private final ReceivableRegisterMapper registerMapper;

    /**
     * 重算所有逾期未结清应收账单的滞纳金与逾期天数。
     *
     * @return 实际更新的账单条数(条件不满足的并发跳过不计)
     */
    public int recalc() {
        LocalDate today = LocalDate.now();
        // 仅应收方向(direction=1)、可催缴状态(待收付3/部分结清4/已逾期6)、应收日<今天。
        // 逐行独立条件更新、不整批包事务:每行都是幂等重算,整批事务只会把行锁攥到循环
        // 结束(卡住并发收款),两次并发触发还可能因扫描顺序不同互相死锁;按 id 排序让
        // 加锁顺序确定
        LambdaQueryWrapper<Bill> qw = new LambdaQueryWrapper<>();
        qw.eq(Bill::getDirection, 1)
          .in(Bill::getStatus, 3, 4, 6)
          .lt(Bill::getDueDate, today)
          // 人工调整过的滞纳金(如现场协商减免)锁定,自动重算整条跳过,
          // 否则管理员改成 0 后第二天就被自愈任务算回去了
          .ne(Bill::getLateFeeManual, 1)
          .orderByAsc(Bill::getId);
        List<Bill> list = billMapper.selectList(qw);
        Map<Long, LocalDate> policyStarts = policyStartDates(list);
        int count = 0;
        for (Bill b : list) {
            if (b.getDueDate() == null) {
                continue;
            }
            // 逾期天数按真实应收日展示;滞纳金天数从起算日(应收日与账单创建日取较晚者)计。
            // 自动回填的历史账单(创建晚于应收日)从补录日起算、不追溯 —— 2026-09-01 用户
            // 拍板的口径:欠款照实进收银台,但不会一上线就冒出追溯几个月的滞纳金
            long overdueDays = today.toEpochDay() - b.getDueDate().toEpochDay();
            if (overdueDays <= 0) {
                continue;
            }
            BigDecimal outstanding = nz(b.getAmount()).subtract(nz(b.getPaidAmount()));
            if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            // 不可变 Map 对 null 键 get 会 NPE:没挂登记表的历史账单 registerId 为空
            LocalDate policyStart = b.getReceivableRegisterId() == null
                    ? null : policyStarts.get(b.getReceivableRegisterId());
            long feeDays = today.toEpochDay() - effectiveFeeStart(b, policyStart).toEpochDay();
            BigDecimal lateFee = feeDays <= 0 ? BigDecimal.ZERO.setScale(2)
                    : outstanding
                    .multiply(DAILY_RATE)
                    .multiply(BigDecimal.valueOf(feeDays))
                    .setScale(2, RoundingMode.HALF_UP);
            // 条件更新:上面的 selectList 是旧读,这中间账单可能刚被收满(status→5)。
            // updateById 盲写会把已结清账单打回"逾期";WHERE 里必须再验一次
            // "仍在可催缴状态且本金未收齐",条件不满足(updated==0)就跳过。
            // SET 走实体而不是 wrapper.set():实体形式才会触发 MyMetaObjectHandler
            // 自动填充 update_time/update_by,审计字段不掉队
            Bill patch = new Bill();
            patch.setOverdueDays((int) overdueDays);
            patch.setLateFee(lateFee);
            patch.setStatus(6);
            int updated = billMapper.update(patch, new LambdaUpdateWrapper<Bill>()
                    .eq(Bill::getId, b.getId())
                    .in(Bill::getStatus, 3, 4, 6)
                    // 快照后可能刚被人工锁定:条件更新里再验一次,不覆盖人工值
                    .ne(Bill::getLateFeeManual, 1)
                    .apply("paid_amount < amount"));
            if (updated == 1) {
                count++;
            }
        }
        return count;
    }

    /**
     * 批量取账单所属登记表的「滞纳金起算日」政策(late_fee_start_date),避免逐单查库。
     * 没挂登记表的账单(历史/合同计划来源)不受政策影响。
     */
    private Map<Long, LocalDate> policyStartDates(List<Bill> bills) {
        List<Long> registerIds = bills.stream()
                .map(Bill::getReceivableRegisterId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (registerIds.isEmpty()) {
            return Map.of();
        }
        return registerMapper.selectBatchIds(registerIds).stream()
                .filter(register -> register != null && register.getLateFeeStartDate() != null)
                .collect(Collectors.toMap(ReceivableRegister::getId,
                        ReceivableRegister::getLateFeeStartDate));
    }

    /**
     * 实际滞纳金起算日 = 默认起算日与登记表政策起算日取较晚者。
     *
     * <p>政策日只能把起算推后(如「10 月以后才开始算」),不能把它提前到默认口径之前
     * —— 否则会绕过「补录不追溯」的保护。</p>
     */
    private static LocalDate effectiveFeeStart(Bill bill, LocalDate policyStart) {
        LocalDate start = lateFeeStart(bill);
        return policyStart != null && policyStart.isAfter(start) ? policyStart : start;
    }

    /**
     * 滞纳金起算日:应收日与账单创建日取较晚者。
     *
     * <p>正常流程账单先于应收日生成(创建早于应收日)→ 仍从应收日起算,行为不变;
     * 自动回填的历史账单(创建晚于应收日)→ 从补录日起算,不追溯历史账期的滞纳金。</p>
     */
    private static LocalDate lateFeeStart(Bill bill) {
        LocalDate due = bill.getDueDate();
        LocalDateTime created = bill.getCreateTime();
        if (created == null) {
            return due;
        }
        LocalDate createdDate = created.toLocalDate();
        return createdDate.isAfter(due) ? createdDate : due;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
