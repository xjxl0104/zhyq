package com.zhyq.park.marketing.yunshan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class YunshanConnectionSeederTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final FieldEncryptionService encryption = new FieldEncryptionService(Base64.getEncoder().encodeToString(new byte[32]));

    @Test void registersTheServerConfiguredInterfaceAsEachExistingWarehouseOwnInterface() {
        var shared = new YunshanClient(new ObjectMapper(), "https://qcy.example/api/", "shared-app-3ee", "public-key");
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("id", 7L, "tenant_id", 1L, "project_id", 2L)));
        assertThat(new YunshanConnectionSeeder(jdbc, shared, encryption).seed()).isEqualTo(1);
        var params = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(anyString(), params.capture(), params.capture(), params.capture(), params.capture(), params.capture(), params.capture());
        var values = params.getAllValues();
        assertThat(values.get(0)).isEqualTo(7L);
        assertThat(values.get(1)).isEqualTo("https://qcy.example/api");
        assertThat(values.get(2).toString()).startsWith("v1:").doesNotContain("shared-app-3ee");
        assertThat(encryption.decrypt(values.get(3).toString())).isEqualTo("public-key");
    }

    @Test void doesNothingWhenNoServerInterfaceIsConfigured() {
        var empty = new YunshanClient(new ObjectMapper(), "", "", "");
        assertThat(new YunshanConnectionSeeder(jdbc, empty, encryption).seed()).isZero();
        verifyNoInteractions(jdbc);
    }
}
