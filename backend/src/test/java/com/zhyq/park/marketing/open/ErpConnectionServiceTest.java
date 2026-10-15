package com.zhyq.park.marketing.open;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseErp;
import com.zhyq.park.marketing.mapper.MktWarehouseErpMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ErpConnectionServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 10, 0);
    @Mock MktWarehouseMapper warehouses;
    @Mock MktWarehouseErpMapper erps;

    @BeforeAll static void initTables() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MktWarehouse.class);
        TableInfoHelper.initTableInfo(assistant, MktWarehouseErp.class);
    }

    @Test void liveHeartbeatWithoutRecentOrdersRemainsConnected() {
        MktWarehouseErp credential = credential();
        credential.setLastSyncAt(NOW.minusDays(10));
        credential.setLastHeartbeatAt(NOW.minusMinutes(30));
        assertThat(ErpConnectionService.statusOf(credential, NOW)).isEqualTo(2);
        credential.setLastSyncAt(null);
        assertThat(ErpConnectionService.statusOf(credential, NOW)).isEqualTo(2);
    }

    @Test void recentOrdersCannotSubstituteForExpiredOrMissingSignedHeartbeat() {
        MktWarehouseErp credential = credential(); credential.setLastSyncAt(NOW);
        assertThat(ErpConnectionService.statusOf(credential, NOW)).isZero();
        credential.setLastHeartbeatAt(NOW.minusHours(6).minusSeconds(1));
        assertThat(ErpConnectionService.statusOf(credential, NOW)).isEqualTo(3);
    }

    @Test void disabledCredentialHasNoLiveConnectionEvenWithFreshHeartbeat() {
        MktWarehouseErp credential = credential();
        credential.setLastHeartbeatAt(NOW); credential.setStatus(0);
        assertThat(ErpConnectionService.statusOf(credential, NOW)).isZero();
    }

    @Test void firstAuthenticatedRequestConnectsToTheConfiguredEnvironment() {
        MktWarehouseErp credential = credential(); credential.setEnv(1);
        MktWarehouse warehouse = warehouse(0);
        when(warehouses.selectForUpdate(11L)).thenReturn(warehouse);
        when(erps.selectById(1L)).thenReturn(credential);
        assertThat(service().recordHeartbeat(credential, NOW)).isTrue();
        assertThat(credential.getLastHeartbeatAt()).isEqualTo(NOW);
        assertThat(credential.getLastSyncAt()).isNull();
        assertThat(ErpConnectionService.statusOf(credential, NOW)).isEqualTo(1);
        verify(warehouses).update(any(), any());
    }

    @Test void requestSignedBeforeKeyResetCannotReconnect() {
        MktWarehouseErp verified = credential();
        MktWarehouseErp current = credential(); current.setSecretEnc("new-encrypted-secret");
        assertRejectedAfterConfigurationChange(verified, current);
    }

    @Test void requestSignedBeforeEnvironmentSwitchCannotReconnect() {
        MktWarehouseErp verified = credential();
        MktWarehouseErp current = credential(); current.setEnv(1);
        assertRejectedAfterConfigurationChange(verified, current);
    }

    @Test void requestSignedBeforeDisableAndEnableCannotReconnectEvenWhenValuesMatchAgain() {
        MktWarehouseErp verified = credential();
        MktWarehouseErp current = credential(); current.setConnectionRevision(2);
        assertRejectedAfterConfigurationChange(verified, current);
    }

    @Test void scheduledRefreshReReadsUnderLockInsteadOfApplyingItsStaleSnapshot() {
        MktWarehouseErp snapshot = credential(); snapshot.setLastHeartbeatAt(NOW.minusDays(1));
        MktWarehouseErp current = credential(); current.setLastHeartbeatAt(NOW);
        when(warehouses.selectForUpdate(11L)).thenReturn(warehouse(2));
        when(erps.selectById(1L)).thenReturn(current);
        assertThat(service().refresh(snapshot, NOW)).isZero();
        assertThat(snapshot.getLastHeartbeatAt()).isEqualTo(NOW);
        verify(warehouses, never()).update(any(), any());
    }

    @Test void configurationChangeClearsHeartbeatAndAdvancesRevisionWhilePreservingLastOrder() {
        MktWarehouseErp credential = credential(); credential.setLastHeartbeatAt(NOW);
        credential.setLastSyncAt(NOW.minusDays(1));
        when(warehouses.selectForUpdate(11L)).thenReturn(warehouse(2));
        when(erps.selectById(1L)).thenReturn(credential);
        service().invalidate(credential);
        assertThat(credential.getConnectionRevision()).isEqualTo(1);
        assertThat(credential.getLastHeartbeatAt()).isNull();
        assertThat(credential.getLastSyncAt()).isEqualTo(NOW.minusDays(1));
        assertThat(ErpConnectionService.statusOf(credential, NOW)).isZero();
        verify(erps).update(any(), any());
        verify(warehouses).update(any(), any());
    }

    private void assertRejectedAfterConfigurationChange(MktWarehouseErp verified, MktWarehouseErp current) {
        when(warehouses.selectForUpdate(11L)).thenReturn(warehouse(0));
        when(erps.selectById(1L)).thenReturn(current);
        assertThat(service().recordHeartbeat(verified, NOW)).isFalse();
        verify(erps, never()).update(any(), any());
        verify(warehouses, never()).update(any(), any());
    }

    private ErpConnectionService service() { return new ErpConnectionService(warehouses, erps); }
    private static MktWarehouse warehouse(int status) {
        MktWarehouse warehouse = new MktWarehouse(); warehouse.setId(11L); warehouse.setErpStatus(status); return warehouse;
    }
    private static MktWarehouseErp credential() {
        MktWarehouseErp credential = new MktWarehouseErp(); credential.setId(1L); credential.setWarehouseId(11L);
        credential.setAppId("wh-test"); credential.setSecretEnc("encrypted-secret"); credential.setStatus(1);
        credential.setEnv(2); credential.setConnectionRevision(0); return credential;
    }
}
