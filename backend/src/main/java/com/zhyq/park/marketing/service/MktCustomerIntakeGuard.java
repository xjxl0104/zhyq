package com.zhyq.park.marketing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes name/phone duplicate checks for every intake channel until commit. */
@Service
@RequiredArgsConstructor
public class MktCustomerIntakeGuard {
    private final JdbcTemplate jdbc;
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(Long tenantId) {
        jdbc.update("INSERT IGNORE INTO mkt_customer_intake_guard(tenant_id) VALUES (?)", tenantId);
        jdbc.queryForObject("SELECT tenant_id FROM mkt_customer_intake_guard WHERE tenant_id=? FOR UPDATE", Long.class, tenantId);
    }
}
