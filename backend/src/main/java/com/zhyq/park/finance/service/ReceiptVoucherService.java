package com.zhyq.park.finance.service;

import com.zhyq.park.building.entity.Project;
import com.zhyq.park.building.mapper.ProjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.entity.Payment;
import com.zhyq.park.finance.entity.Receipt;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.finance.mapper.PaymentMapper;
import com.zhyq.park.finance.mapper.ReceiptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/** 为租户打印的收据凭单数据。 */
@Service
@RequiredArgsConstructor
public class ReceiptVoucherService {

    private static final String DEFAULT_ISSUER = "DIPARK数智云仓产业园";

    private final ReceiptMapper receiptMapper;
    private final PaymentMapper paymentMapper;
    private final BillMapper billMapper;
    private final ProjectMapper projectMapper;
    private final FinanceViewEnricher viewEnricher;

    public ReceiptVoucher voucher(Long receiptId) {
        Receipt receipt = requirePrintableReceipt(receiptId);
        FinanceViewEnricher.BillView view = receipt.getBillId() == null ? null
                : viewEnricher.resolveBillViews(List.of(receipt.getBillId())).get(receipt.getBillId());
        Bill bill = billOf(receipt.getBillId());
        Payment payment = receipt.getPaymentId() == null ? null : paymentMapper.selectById(receipt.getPaymentId());
        Project project = projectOf(bill);

        String feeType = view == null || !StringUtils.hasText(view.feeType()) ? "收款" : view.feeType();
        String payerName = view == null || !StringUtils.hasText(view.tenantName())
                ? "租客 #" + (receipt.getTenantRefId() == null ? "-" : receipt.getTenantRefId())
                : view.tenantName();
        String billCode = view == null || !StringUtils.hasText(view.billCode())
                ? "-" : view.billCode();
        String contractNo = view == null || !StringUtils.hasText(view.agreementNo())
                ? billCode : view.agreementNo();
        BigDecimal amount = receipt.getAmount() == null ? BigDecimal.ZERO : receipt.getAmount();
        return new ReceiptVoucher(
                feeType.contains("保证金") ? "保证金收据" : "收款收据",
                receipt.getReceiptNo(),
                issuerName(project),
                payerName,
                contractNo,
                leaseAddress(project),
                billCode,
                feeType,
                amount,
                toChineseCurrency(amount),
                payment == null ? receipt.getCreateTime() : payment.getPayTime(),
                payment == null || !StringUtils.hasText(payment.getPayMethod()) ? "-" : payment.getPayMethod(),
                "system".equalsIgnoreCase(receipt.getPayee()) ? "" : receipt.getPayee(),
                receipt.getRemark());
    }

    /** 已作废的收据不能再作为对外凭证打印。 */
    public Receipt requirePrintableReceipt(Long receiptId) {
        Receipt receipt = receiptMapper.selectById(receiptId);
        if (receipt == null) {
            throw new BizException("收据不存在: " + receiptId);
        }
        if (Integer.valueOf(1).equals(receipt.getVoidStatus())) {
            throw new BizException("该收据已作废，不能打印或交付租户");
        }
        return receipt;
    }

    private Bill billOf(Long billId) {
        if (billId == null) {
            return null;
        }
        return billMapper.selectByIdsIncludingDeleted(List.of(billId)).stream().findFirst().orElse(null);
    }

    private Project projectOf(Bill bill) {
        return bill == null || bill.getProjectId() == null ? null : projectMapper.selectById(bill.getProjectId());
    }

    private String issuerName(Project project) {
        return project != null && StringUtils.hasText(project.getName()) ? project.getName() : DEFAULT_ISSUER;
    }

    /** 收据上的租赁地址优先使用园区已维护的实际地址，未维护时退回园区名称。 */
    private String leaseAddress(Project project) {
        if (project == null) {
            return "-";
        }
        return StringUtils.hasText(project.getAddress()) ? project.getAddress() : issuerName(project);
    }

    /** 人民币大写，例如 108000.00 → 壹拾万捌仟元整。 */
    public static String toChineseCurrency(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("收据金额必须为非负数");
        }
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
        BigInteger cents = rounded.movePointRight(2).toBigIntegerExact();
        BigInteger[] parts = cents.divideAndRemainder(BigInteger.valueOf(100));
        int jiao = parts[1].intValue() / 10;
        int fen = parts[1].intValue() % 10;
        String whole = integerToChinese(parts[0]);
        if (jiao == 0 && fen == 0) {
            return whole + "元整";
        }
        StringBuilder result = new StringBuilder(whole).append("元");
        if (jiao == 0) {
            result.append("零");
        } else {
            result.append(DIGITS[jiao]).append("角");
        }
        if (fen != 0) {
            result.append(DIGITS[fen]).append("分");
        }
        return result.toString();
    }

    private static final String[] DIGITS = {"零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"};
    private static final String[] GROUP_UNITS = {"", "万", "亿", "兆"};
    private static final String[] SMALL_UNITS = {"", "拾", "佰", "仟"};

    private static String integerToChinese(BigInteger value) {
        if (BigInteger.ZERO.equals(value)) {
            return DIGITS[0];
        }
        List<Integer> groups = new java.util.ArrayList<>();
        BigInteger tenThousand = BigInteger.valueOf(10_000);
        while (value.signum() > 0) {
            BigInteger[] division = value.divideAndRemainder(tenThousand);
            groups.add(division[1].intValue());
            value = division[0];
        }
        if (groups.size() > GROUP_UNITS.length) {
            throw new IllegalArgumentException("收据金额超出可打印范围");
        }
        StringBuilder result = new StringBuilder();
        boolean pendingZero = false;
        for (int index = groups.size() - 1; index >= 0; index--) {
            int group = groups.get(index);
            if (group == 0) {
                if (!result.isEmpty()) {
                    pendingZero = true;
                }
                continue;
            }
            if (!result.isEmpty() && (pendingZero || group < 1000)) {
                result.append(DIGITS[0]);
            }
            result.append(groupToChinese(group)).append(GROUP_UNITS[index]);
            pendingZero = false;
        }
        return result.toString();
    }

    private static String groupToChinese(int group) {
        StringBuilder result = new StringBuilder();
        boolean pendingZero = false;
        for (int position = 3; position >= 0; position--) {
            int divisor = (int) Math.pow(10, position);
            int digit = group / divisor;
            group %= divisor;
            if (digit == 0) {
                if (!result.isEmpty()) {
                    pendingZero = true;
                }
                continue;
            }
            if (pendingZero) {
                result.append(DIGITS[0]);
                pendingZero = false;
            }
            result.append(DIGITS[digit]).append(SMALL_UNITS[position]);
        }
        return result.toString();
    }

    public record ReceiptVoucher(String title, String receiptNo, String issuerName, String payerName,
                                 String contractNo, String leaseAddress, String billCode, String feeType,
                                 BigDecimal amount, String amountUppercase,
                                 LocalDateTime receivedAt, String payMethod, String payee, String remark) {
    }
}
