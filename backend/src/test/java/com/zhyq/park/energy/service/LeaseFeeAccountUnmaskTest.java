package com.zhyq.park.energy.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LeaseFeeAccountUnmaskTest {

    @Test
    void unmaskTextReplacesMaskedAccountsWithFullPlaintext() {
        Map<String, String> map = Map.of(
                "6600****0348", "660079330348",
                "4405****0277", "44050149010100000277"
        );
        String rent = "广州市昌记供应链有限公司\n开户行：中行广州花都文旅城支行\n账号：6600****0348";
        String prop = "广州数智云仓产业园运营有限公司\n开户行：建行广州设计之都支行\n账号：4405****0277";

        assertThat(LeaseFeeService.hasMaskedMarker(rent)).isTrue();
        assertThat(LeaseFeeService.unmaskText(rent, map))
                .isEqualTo("广州市昌记供应链有限公司\n开户行：中行广州花都文旅城支行\n账号：660079330348");
        assertThat(LeaseFeeService.unmaskText(prop, map))
                .isEqualTo("广州数智云仓产业园运营有限公司\n开户行：建行广州设计之都支行\n账号：44050149010100000277");
        assertThat(LeaseFeeService.unmaskText("账号：660079330348", map))
                .isEqualTo("账号：660079330348");
    }
}
