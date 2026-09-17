package com.zhyq.park.contract.service;

import com.zhyq.park.contract.entity.Contract;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * 合同到期预警。
 *
 * <p>预警仅面向执行中的合同；合同在今天至两个月后的同一天之间到期时，
 * 在合同归档页提示运营人员提前续签或办理退租。</p>
 */
@Service
public class ContractExpiryAlertService {

    public List<ExpiryAlert> findAlerts(List<Contract> contracts, LocalDate today) {
        if (contracts == null || contracts.isEmpty() || today == null) {
            return List.of();
        }
        LocalDate deadline = today.plusMonths(2);
        return contracts.stream()
                .filter(contract -> Integer.valueOf(5).equals(contract.getStatus()))
                .filter(contract -> contract.getEndDate() != null)
                .filter(contract -> !contract.getEndDate().isBefore(today))
                .filter(contract -> !contract.getEndDate().isAfter(deadline))
                .sorted(Comparator.comparing(Contract::getEndDate))
                .map(contract -> new ExpiryAlert(
                        contract.getId(),
                        contract.getCode(),
                        contract.getTenantName(),
                        contract.getEndDate(),
                        ChronoUnit.DAYS.between(today, contract.getEndDate())))
                .toList();
    }

    public record ExpiryAlert(Long id, String code, String tenantName,
                              LocalDate endDate, long daysRemaining) {
    }
}
