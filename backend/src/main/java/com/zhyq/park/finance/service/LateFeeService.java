package com.zhyq.park.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
          .orderByAsc(Bill::getId);
        List<Bill> list = billMapper.selectList(qw);
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
            long feeDays = today.toEpochDay() - lateFeeStart(b).toEpochDay();
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
                    .apply("paid_amount < amount"));
            if (updated == 1) {
                count++;
            }
        }
        return count;
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
