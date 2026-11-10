package com.zhyq.park.marketing.service;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MktPartnerShipmentServiceTest {
    final MktPromoterMapper promoters = mock(MktPromoterMapper.class);
    final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    final MktPartnerShipmentService service = new MktPartnerShipmentService(promoters, jdbc);
    final Map<Long, MktPromoter> people = new LinkedHashMap<>();
    Map<String, ?> queryParameters;
    List<MktPartnerShipmentService.Fact> queryRows = List.of();

    @BeforeEach void setup() {
        put(4, "P4", null, 1); put(3, "P3", 4L, 1); put(2, "P2", 3L, 1); put(1, "P1", 2L, 1);
        put(8, "P4", null, 1); put(9, "P1", 4L, 2); put(10, "P1", 4L, 1);
        people.get(10L).setDeleted(1);
        put(11, "P2", 12L, 1); put(12, "P1", 11L, 1); put(13, "P1", 999L, 1);
        when(promoters.selectById(anyLong())).thenAnswer(i -> people.get(i.getArgument(0)));
        when(promoters.selectList(any())).thenAnswer(i -> new ArrayList<>(people.values()));
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<String, Object>anyMap(),
                org.mockito.ArgumentMatchers.<RowMapper<MktPartnerShipmentService.Fact>>any()))
                .thenAnswer(i -> { queryParameters = i.getArgument(1); return queryRows; });
    }

    @Test void ownSummaryMatchesContractRowsAndKeepsIntegerPrecision() {
        queryRows = List.of(fact(4, 101, 201, 9_007_199_254_740_993L), fact(4, 102, 202, 7));
        var result = report(4, "own", null);
        assertThat(result.get("totalVolume")).isEqualTo("9007199254741000");
        assertThat(result.get("contractCount")).isEqualTo(2L);
        assertThat(result.get("customerCount")).isEqualTo(2L);
        assertThat(result.get("canViewTeam")).isEqualTo(true);
        assertThat(result.get("hasData")).isEqualTo(true);
        assertThat(rows(result, "contracts")).extracting(r -> r.get("volume"))
                .containsExactly("9007199254740993", "7");
        assertThat(rows(result, "members")).isEmpty();
        assertThat(ids()).containsExactly(4L);
        assertThat(queryParameters.get("tenantId")).isEqualTo(1L);
        assertThat(queryParameters.get("startDate")).isEqualTo("2026-10-01");
        assertThat(queryParameters.get("endExclusive")).isEqualTo("2026-11-01");
        assertThat(result.get("dataAsOf")).isEqualTo(LocalDateTime.of(2026, 10, 31, 12, 0));
    }

    @Test void teamIncludesDeepDescendantsOnceAndExcludesSelfOtherTenantsDeletedAndBrokenChains() {
        queryRows = List.of(fact(3, 101, 201, 10), fact(1, 102, 202, 20));
        var result = report(4, "team", null);
        assertThat(ids()).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(result.get("totalVolume")).isEqualTo("30");
        assertThat(result.get("memberCount")).isEqualTo(3);
        assertThat(rows(result, "contracts")).isEmpty();
        var members = rows(result, "members");
        assertThat(members).extracting(r -> r.get("memberId")).containsExactly(1L, 3L, 2L);
        assertThat(members).extracting(r -> r.get("volume")).containsExactly("20", "10", "0");
        assertThat(members.get(2).get("hasData")).isEqualTo(false);
        assertThat(members).allSatisfy(r -> assertThat(r).doesNotContainKeys("phone", "openid", "unionid"));
    }

    @Test void teamDrilldownReturnsOnlyRequestedDescendantsOwnContracts() {
        queryRows = List.of(fact(1, 101, 201, 12), fact(1, 102, 201, 3));
        var result = report(4, "team", 1L);
        assertThat(ids()).containsExactly(1L);
        assertThat(result.get("memberId")).isEqualTo(1L);
        assertThat(result.get("memberName")).isEqualTo("成员1");
        assertThat(result.get("totalVolume")).isEqualTo("15");
        assertThat(result.get("contractCount")).isEqualTo(2L);
        assertThat(result.get("customerCount")).isEqualTo(1L);
        assertThat(rows(result, "contracts")).hasSize(2);
        assertThat(rows(result, "members")).isEmpty();
    }

    @Test void p2AndP3CanViewOnlyTheirOwnDownstreamTeam() {
        report(2, "team", null);
        assertThat(ids()).containsExactly(1L);
        report(3, "team", null);
        assertThat(ids()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test void unrelatedSelfForeignTenantAndDeletedMemberRequestsAreDeniedBeforeQuery() {
        for (long member : new long[] {4, 8, 9, 10, 11, 13, 999})
            assertThatThrownBy(() -> report(4, "team", member)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> report(2, "team", 3L)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> report(4, "own", 1L)).isInstanceOf(BizException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void p1CannotReadTeamEvenForAnExplicitMember() {
        assertThatThrownBy(() -> report(1, "team", null)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> report(1, "team", 1L)).isInstanceOf(BizException.class);
        verifyNoInteractions(jdbc);
        var own = report(1, "own", null);
        assertThat(own.get("canViewTeam")).isEqualTo(false);
        assertThat(ids()).containsExactly(1L);
    }

    @Test void missingInactiveAndDeletedActorsCannotQuery() {
        assertThatThrownBy(() -> report(999, "own", null)).isInstanceOf(BizException.class);
        for (int status : new int[] {0, 2, 3, 4}) {
            people.get(4L).setStatus(status);
            assertThatThrownBy(() -> report(4, "own", null)).isInstanceOf(BizException.class);
        }
        people.get(4L).setStatus(1); people.get(4L).setDeleted(1);
        assertThatThrownBy(() -> report(4, "own", null)).isInstanceOf(BizException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void invalidScopeIsRejectedAndEmptyTeamAvoidsInvalidInQuery() {
        assertThatThrownBy(() -> report(4, "all", null)).isInstanceOf(BizException.class);
        var result = report(8, "team", null);
        assertThat(result.get("totalVolume")).isEqualTo("0");
        assertThat(result.get("hasData")).isEqualTo(false);
        assertThat(result.get("memberCount")).isEqualTo(0);
        assertThat(result.get("dataAsOf")).isNull();
        assertThat(rows(result, "members")).isEmpty();
        verifyNoInteractions(jdbc);
    }

    @Test void confirmedZeroSnapshotIsDistinguishedFromNoAvailableData() {
        queryRows = List.of(fact(1, 101, 201, 0));
        var result = report(1, "own", null);
        assertThat(result.get("totalVolume")).isEqualTo("0");
        assertThat(result.get("hasData")).isEqualTo(true);
        assertThat(result.get("contractCount")).isEqualTo(1L);
    }

    @Test void descendantTraversalStopsOnCyclesAndMissingParents() {
        assertThat(MktPartnerShipmentService.descendant(people.get(1L), 4L, people)).isTrue();
        assertThat(MktPartnerShipmentService.descendant(people.get(4L), 4L, people)).isFalse();
        assertThat(MktPartnerShipmentService.descendant(people.get(11L), 4L, people)).isFalse();
        assertThat(MktPartnerShipmentService.descendant(people.get(13L), 4L, people)).isFalse();
    }

    @Test void datesDefaultToCurrentMonthAndAllowInclusive366DayLeapYear() {
        LocalDate today = LocalDate.of(2025, 1, 9);
        var defaults = MktPartnerShipmentService.dates(null, null, today);
        assertThat(defaults.start()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(defaults.end()).isEqualTo(today);
        var customEnd = MktPartnerShipmentService.dates("", "2024-02-29", today);
        assertThat(customEnd.start()).isEqualTo(LocalDate.of(2024, 2, 1));
        var fullYear = MktPartnerShipmentService.dates("2024-01-01", "2024-12-31", today);
        assertThat(fullYear.start()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(fullYear.end()).isEqualTo(LocalDate.of(2024, 12, 31));
    }

    @Test void futureReversedTooLongAndMalformedDatesAreRejected() {
        LocalDate today = LocalDate.of(2025, 1, 9);
        for (String[] range : List.of(
                new String[] {"2025-01-01", "2025-01-10"},
                new String[] {"2025-01-05", "2025-01-04"},
                new String[] {"2024-01-01", "2025-01-01"},
                new String[] {"2024-02-30", "2024-03-01"},
                new String[] {"2024/01/01", "2024-01-02"},
                new String[] {"2024-01-01' OR 1=1", "2024-01-02"}))
            assertThatThrownBy(() -> MktPartnerShipmentService.dates(range[0], range[1], today))
                    .isInstanceOf(BizException.class);
    }

    private Map<String, Object> report(long actor, String scope, Long member) {
        return service.report(actor, scope, "2026-10-01", "2026-10-31", member);
    }
    private void put(long id, String position, Long parent, long tenant) {
        var p = new MktPromoter(); p.setId(id); p.setName("成员" + id); p.setPositionCode(position);
        p.setParentId(parent); p.setTenantId(tenant); p.setStatus(1); p.setDeleted(0);
        people.put(id, p);
    }
    private MktPartnerShipmentService.Fact fact(long promoter, long contract, long customer, long volume) {
        return new MktPartnerShipmentService.Fact(promoter, contract, "CS-" + contract, customer,
                "客户" + customer, "云仓", volume, LocalDateTime.of(2026, 10, 31, 12, 0));
    }
    @SuppressWarnings("unchecked") private Collection<Long> ids() {
        return (Collection<Long>) queryParameters.get("memberIds");
    }
    @SuppressWarnings("unchecked") private List<Map<String, Object>> rows(Map<String, Object> result, String key) {
        return (List<Map<String, Object>>) result.get(key);
    }
}
