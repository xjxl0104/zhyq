package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.marketing.entity.MktCustomerLock;
import com.zhyq.park.marketing.entity.MktPosition;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseOnboarding;
import com.zhyq.park.marketing.mapper.MktCustomerLockMapper;
import com.zhyq.park.marketing.mapper.MktPositionMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseOnboardingMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 云仓加盟状态机 + 锁客状态机:每条迁移合法/非法各一个 case。 */
class MktOnboardingAndLockServiceTest {

    @BeforeAll
    static void initMp() {
        MapperBuilderAssistant a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(a, MktWarehouse.class);
        TableInfoHelper.initTableInfo(a, MktWarehouseOnboarding.class);
        TableInfoHelper.initTableInfo(a, MktCustomerLock.class);
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Onboarding {
        @Mock MktWarehouseMapper warehouseMapper;
        @Mock MktWarehouseOnboardingMapper stepMapper;
        @Mock MktAuditService auditService;
        @InjectMocks MktWarehouseOnboardingService service;

        @Test
        void applyCreatesFiveStepsAndMovesToQualifying() {
            MktWarehouse w = new MktWarehouse(); w.setCode("WH-1"); w.setName("杭州仓"); w.setFeeModel("");
            when(warehouseMapper.insert(any(MktWarehouse.class))).thenAnswer(inv -> { w.setId(7L); return 1; });
            when(warehouseMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

            service.apply(w);

            assertThat(w.getJoinStatus()).isEqualTo(MktWarehouseOnboardingService.JS_APPLIED);
            assertThat(w.getFeeModel()).isNull();
            ArgumentCaptor<MktWarehouseOnboarding> cap = ArgumentCaptor.forClass(MktWarehouseOnboarding.class);
            verify(stepMapper, times(5)).insert(cap.capture());
            assertThat(cap.getAllValues()).extracting(MktWarehouseOnboarding::getStep).containsExactly(1, 2, 3, 4, 5);
            assertThat(cap.getAllValues().get(0).getStatus()).isEqualTo(2); // 申请步直接通过
            assertThat(cap.getAllValues().get(1).getStatus()).isEqualTo(1); // 资质审核进行中
            verify(auditService).log(eq("warehouse.apply"), any(), eq(7L), isNull());
        }

        @Test
        void applyRequiresCodeAndName() {
            assertThatThrownBy(() -> service.apply(new MktWarehouse())).isInstanceOf(BizException.class);
        }

        @Test
        void passQualificationMovesToErpConnecting() {
            when(warehouseMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
            service.passQualification(7L);
            verify(auditService).log(eq("warehouse.qualify.pass"), any(), eq(7L), isNull());
            verify(stepMapper, times(2)).update(isNull(), any(Wrapper.class)); // 步骤2通过 + 步骤3开始
        }

        @Test
        void rejectQualificationNeedsReason() {
            assertThatThrownBy(() -> service.rejectQualification(7L, null)).isInstanceOf(BizException.class);
        }

        @Test
        void markErpConnectedFromWrongStateThrows() {
            when(warehouseMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
            assertThatThrownBy(() -> service.markErpConnected(7L, "ops")).isInstanceOf(BizException.class);
        }

        @Test
        void markErpConnectedMovesToPendingAgreement() {
            when(warehouseMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
            service.markErpConnected(7L, "ops");
            verify(auditService).log(eq("warehouse.erp.mark"), any(), eq(7L), any());
        }

        @Test
        void signAgreementRequiresFileAndGoesOnline() {
            assertThatThrownBy(() -> service.signAgreement(7L, "")).isInstanceOf(BizException.class);
            when(warehouseMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
            service.signAgreement(7L, "/files/agreement.pdf");
            verify(auditService).log(eq("warehouse.online"), any(), eq(7L), any());
        }

        @Test
        void resumeRequiresErpConnected() {
            MktWarehouse w = new MktWarehouse(); w.setId(7L); w.setJoinStatus(6); w.setErpStatus(3);
            when(warehouseMapper.selectById(7L)).thenReturn(w);
            assertThatThrownBy(() -> service.resume(7L)).isInstanceOf(BizException.class).hasMessageContaining("ERP");
        }

        @Test
        void pauseAndExitNeedReason() {
            assertThatThrownBy(() -> service.pause(7L, "")).isInstanceOf(BizException.class);
            assertThatThrownBy(() -> service.exit(7L, null)).isInstanceOf(BizException.class);
        }

        @Test
        void canAcceptCustomersOnlyWhenOnlineAndConnected() {
            MktWarehouse w = new MktWarehouse(); w.setJoinStatus(5); w.setErpStatus(1);
            assertThat(service.canAcceptCustomers(w)).isTrue();
            w.setErpStatus(0);
            assertThat(service.canAcceptCustomers(w)).isFalse();
            w.setErpStatus(2); w.setJoinStatus(6);
            assertThat(service.canAcceptCustomers(w)).isFalse();
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Lock {
        @Mock MktCustomerLockMapper lockMapper;
        @Mock MktPromoterMapper promoterMapper;
        @Mock MktPositionMapper positionMapper;
        @Mock BizSettings bizSettings;
        @Mock MktAuditService auditService;
        @Mock ApplicationEventPublisher eventPublisher;
        @InjectMocks MktLockService service;

        private MktPromoter normalPromoter() {
            MktPromoter p = new MktPromoter(); p.setId(9L); p.setStatus(1); p.setPositionCode("P1");
            return p;
        }

        private void capOf(int cap, long used) {
            MktPosition pos = new MktPosition(); pos.setCode("P1"); pos.setLockCap(cap);
            lenient().when(positionMapper.selectOne(any(Wrapper.class))).thenReturn(pos);
            lenient().when(lockMapper.selectCount(any(Wrapper.class))).thenReturn(used);
        }

        @Test
        void prelockCreatesRowWithPrelockDeadline() {
            when(promoterMapper.selectById(9L)).thenReturn(normalPromoter());
            capOf(50, 0L);
            when(bizSettings.getInt(eq("marketing"), eq("prelock_days"), anyInt())).thenReturn(7);
            when(bizSettings.getInt(eq("marketing"), eq("lock_cooldown_days"), anyInt())).thenReturn(30);

            MktCustomerLock lock = service.prelock(5L, 9L);

            assertThat(lock.getStatus()).isEqualTo(MktLockService.LS_PRELOCK);
            assertThat(lock.getPrelockUntil()).isAfter(LocalDateTime.now().plusDays(6));
            verify(lockMapper).insert(lock);
            verify(eventPublisher).publishEvent(any(DomainEvent.CustomerLockChanged.class));
        }

        @Test
        void prelockRejectsFrozenPromoter() {
            MktPromoter p = normalPromoter(); p.setStatus(2);
            when(promoterMapper.selectById(9L)).thenReturn(p);
            assertThatThrownBy(() -> service.prelock(5L, 9L)).isInstanceOf(BizException.class);
        }

        @Test
        void prelockRejectsWhenCapReached() {
            when(promoterMapper.selectById(9L)).thenReturn(normalPromoter());
            capOf(50, 50L);
            assertThatThrownBy(() -> service.prelock(5L, 9L)).isInstanceOf(BizException.class).hasMessageContaining("上限");
        }

        @Test
        void prelockTranslatesDuplicateKeyIntoAlreadyReported() {
            when(promoterMapper.selectById(9L)).thenReturn(normalPromoter());
            capOf(50, 0L);
            when(bizSettings.getInt(eq("marketing"), eq("prelock_days"), anyInt())).thenReturn(7);
            when(bizSettings.getInt(eq("marketing"), eq("lock_cooldown_days"), anyInt())).thenReturn(30);
            when(lockMapper.insert(any(MktCustomerLock.class))).thenThrow(new DuplicateKeyException("uk_lock_active"));

            assertThatThrownBy(() -> service.prelock(5L, 9L)).isInstanceOf(BizException.class).hasMessageContaining("已被报备");
        }

        @Test
        void confirmMovesPrelockToLocked() {
            when(bizSettings.getInt(eq("marketing"), eq("lock_days"), anyInt())).thenReturn(180);
            when(lockMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
            MktCustomerLock l = new MktCustomerLock(); l.setId(1L); l.setStatus(2);
            when(lockMapper.selectById(1L)).thenReturn(l);
            service.confirm(1L, "专员甲");
            verify(auditService).log(eq("lock.confirm"), any(), eq(1L), isNull());
        }

        @Test
        void confirmFromWrongStateThrows() {
            when(bizSettings.getInt(eq("marketing"), eq("lock_days"), anyInt())).thenReturn(180);
            when(lockMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
            assertThatThrownBy(() -> service.confirm(1L, "x")).isInstanceOf(BizException.class);
        }

        @Test
        void extendOnlyOnce() {
            MktCustomerLock l = new MktCustomerLock(); l.setId(1L); l.setStatus(2); l.setExtendedCount(1);
            when(lockMapper.selectById(1L)).thenReturn(l);
            assertThatThrownBy(() -> service.extend(1L, "有到访")).isInstanceOf(BizException.class).hasMessageContaining("延期过");
        }

        @Test
        void extendAddsDays() {
            MktCustomerLock l = new MktCustomerLock(); l.setId(1L); l.setStatus(2); l.setExtendedCount(0);
            l.setLockUntil(LocalDateTime.of(2027, 1, 1, 0, 0));
            when(lockMapper.selectById(1L)).thenReturn(l);
            when(bizSettings.getInt(eq("marketing"), eq("lock_extend_days"), anyInt())).thenReturn(90);
            when(lockMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
            service.extend(1L, "近 60 天有方案");
            verify(auditService).log(eq("lock.extend"), any(), eq(1L), eq("近 60 天有方案"));
        }

        @Test
        void releaseNeedsReason() {
            assertThatThrownBy(() -> service.release(1L, "", "ops")).isInstanceOf(BizException.class);
        }

        @Test
        void markDealIsNoopWithoutActiveLock() {
            when(lockMapper.selectOne(any(Wrapper.class))).thenReturn(null);
            service.markDeal(5L);
            verify(lockMapper, never()).update(isNull(), any(Wrapper.class));
        }

        @Test
        void markDealRejectsContractPartnerDifferentFromLockedPartner() {
            MktCustomerLock active = new MktCustomerLock();
            active.setId(1L); active.setCustomerId(5L); active.setPromoterId(9L); active.setStatus(2);
            when(lockMapper.selectOne(any(Wrapper.class))).thenReturn(active);
            assertThatThrownBy(() -> service.markDeal(5L, 8L))
                    .isInstanceOf(BizException.class).hasMessageContaining("不一致");
            verify(lockMapper, never()).update(isNull(), any(Wrapper.class));
        }

        @Test
        void expireReleasesDueLocks() {
            MktCustomerLock a = new MktCustomerLock(); a.setId(1L); a.setStatus(1);
            MktCustomerLock b = new MktCustomerLock(); b.setId(2L); b.setStatus(2);
            when(lockMapper.selectList(any(Wrapper.class))).thenReturn(List.of(a, b));
            when(lockMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
            when(lockMapper.selectById(any())).thenReturn(a);

            int n = service.expire(LocalDateTime.now());

            assertThat(n).isEqualTo(2);
            verify(eventPublisher, times(2)).publishEvent(any(DomainEvent.CustomerLockChanged.class));
        }
    }
}
