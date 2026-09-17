package com.zhyq.park.contract;

import com.zhyq.park.contract.entity.Contract;
import com.zhyq.park.contract.service.ContractExpiryAlertService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContractExpiryAlertServiceTest {

    private final ContractExpiryAlertService service = new ContractExpiryAlertService();

    @Test
    void includesRunningContractsDueTodayThroughTwoMonthsAndSortsByEndDate() {
        LocalDate today = LocalDate.of(2026, 9, 17);
        Contract dueInMonth = contract(2L, "HT-SECOND", 5, today.plusMonths(1));
        Contract dueToday = contract(1L, "HT-FIRST", 5, today);
        Contract dueInTwoMonths = contract(3L, "HT-THIRD", 5, today.plusMonths(2));

        List<ContractExpiryAlertService.ExpiryAlert> alerts = service.findAlerts(
                List.of(dueInMonth, dueInTwoMonths, dueToday), today);

        assertThat(alerts).extracting(ContractExpiryAlertService.ExpiryAlert::code)
                .containsExactly("HT-FIRST", "HT-SECOND", "HT-THIRD");
        assertThat(alerts).extracting(ContractExpiryAlertService.ExpiryAlert::daysRemaining)
                .containsExactly(0L, 30L, 61L);
    }

    @Test
    void excludesExpiredOutOfWindowAndNonRunningContracts() {
        LocalDate today = LocalDate.of(2026, 9, 17);
        Contract expired = contract(1L, "HT-EXPIRED", 5, today.minusDays(1));
        Contract later = contract(2L, "HT-LATER", 5, today.plusMonths(2).plusDays(1));
        Contract terminated = contract(3L, "HT-TERMINATED", 9, today.plusDays(10));
        Contract noEndDate = contract(4L, "HT-NO-END", 5, null);

        assertThat(service.findAlerts(List.of(expired, later, terminated, noEndDate), today)).isEmpty();
    }

    private Contract contract(Long id, String code, int status, LocalDate endDate) {
        Contract contract = new Contract();
        contract.setId(id);
        contract.setCode(code);
        contract.setStatus(status);
        contract.setEndDate(endDate);
        return contract;
    }
}
