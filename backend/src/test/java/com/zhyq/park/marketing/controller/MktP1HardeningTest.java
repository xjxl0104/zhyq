package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktContractTemplate;
import com.zhyq.park.marketing.entity.MktCustomerGrade;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktPosition;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktContractTemplateMapper;
import com.zhyq.park.marketing.mapper.MktCustomerGradeMapper;
import com.zhyq.park.marketing.mapper.MktCustomerLockMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktPositionHistoryMapper;
import com.zhyq.park.marketing.mapper.MktPositionMapper;
import com.zhyq.park.marketing.mapper.MktPromoterCommissionMapper;
import com.zhyq.park.marketing.mapper.MktReferralOrderMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseOnboardingMapper;
import com.zhyq.park.marketing.mapper.MktWithdrawalMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktCustomerAssignmentService;
import com.zhyq.park.marketing.service.MktLockService;
import com.zhyq.park.marketing.service.MktPositionReviewService;
import com.zhyq.park.marketing.service.MktPromoterService;
import com.zhyq.park.marketing.service.MktWarehouseOnboardingService;
import com.zhyq.park.marketing.support.WrapperAssert;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1 加固回归:三处实体透传改白名单 + 份额全量校验 + 推荐人状态校验。
 * 每条用例对应 T14 盲审 C2/C3/C7 的一个具体洞。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MktP1HardeningTest {

    @BeforeAll
    static void initMp() {
        MapperBuilderAssistant a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(a, MktContractTemplate.class);
        TableInfoHelper.initTableInfo(a, MktCustomerGrade.class);
        TableInfoHelper.initTableInfo(a, MktPosition.class);
        TableInfoHelper.initTableInfo(a, MktWarehouse.class);
        TableInfoHelper.initTableInfo(a, MktPromoter.class);
        TableInfoHelper.initTableInfo(a, Customer.class);
    }

    /** C2a:合同模板 update 只 set 白名单列,id/tenantId/createBy 不可覆盖。 */
    @Nested
    class ContractTemplateWhitelist {
        @Mock MktContractTemplateMapper mapper;
        @Mock MktAuditService audit;
        MktContractTemplateController controller;

        @Test
        void updateSetsOnlyWhitelistColumnsNeverIdentityOrAudit() {
            controller = new MktContractTemplateController(mapper, audit);
            when(mapper.selectById(1L)).thenReturn(template(1L, "old"));
            when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

            MktContractTemplate req = template(1L, "new");
            req.setTenantId(999L);
            req.setCreateBy("hacker");
            controller.update(req);

            ArgumentCaptor<Wrapper<MktContractTemplate>> cap = ArgumentCaptor.forClass(Wrapper.class);
            verify(mapper).update(isNull(), cap.capture());
            WrapperAssert wa = WrapperAssert.of((LambdaUpdateWrapper<MktContractTemplate>) cap.getValue());
            assertThat(wa.setColumns()).contains("name", "service_type", "tpl_version", "body", "variables", "status", "project_id");
            assertThat(wa.setColumns()).doesNotContain("id", "tenant_id", "create_by", "version", "deleted");
        }

        @Test
        void addIgnoresClientSuppliedIdentityFields() {
            controller = new MktContractTemplateController(mapper, audit);
            // 捕获插入前对象:服务端只能 new 一个干净实体,不容带 id/tenantId/createBy
            java.util.concurrent.atomic.AtomicReference<MktContractTemplate> captured = new java.util.concurrent.atomic.AtomicReference<>();
            when(mapper.insert(any(MktContractTemplate.class))).thenAnswer(i -> {
                MktContractTemplate t = i.getArgument(0);
                captured.set(cloneIdentity(t));
                t.setId(7L);          // 模拟 AUTO 主键回填
                return 1;
            });
            MktContractTemplate req = template(null, "t");
            req.setId(999L);
            req.setTenantId(888L);
            req.setCreateBy("hacker");

            controller.add(req);

            assertThat(captured.get().getId()).isNull();
            assertThat(captured.get().getTenantId()).isNull();
            assertThat(captured.get().getCreateBy()).isNull();
        }

        private static MktContractTemplate cloneIdentity(MktContractTemplate t) {
            MktContractTemplate c = new MktContractTemplate();
            c.setId(t.getId());
            c.setTenantId(t.getTenantId());
            c.setCreateBy(t.getCreateBy());
            return c;
        }

        private MktContractTemplate template(Long id, String name) {
            MktContractTemplate t = new MktContractTemplate();
            t.setId(id);
            t.setName(name);
            t.setServiceType(2);
            t.setTplVersion(1);
            t.setStatus(1);
            return t;
        }
    }

    /** C2b:云仓申请不透传 contactOpenid(否则可填任意 openid 登成该云仓)。 */
    @Nested
    class WarehouseApplyWhitelist {
        @Mock MktWarehouseMapper warehouseMapper;
        @Mock MktWarehouseOnboardingMapper stepMapper;
        @Mock MktWarehouseOnboardingService onboarding;
        @Mock MktAuditService audit;

        @Test
        void applyDoesNotPassContactOpenidToService() {
            MktWarehouse captured = warehouse(1L);
            MktWarehouseController controller = new MktWarehouseController(
                    warehouseMapper, stepMapper, null, onboarding, audit, null);
            when(onboarding.apply(any(MktWarehouse.class))).thenReturn(captured);

            MktWarehouse req = warehouse(null);
            req.setContactOpenid("evil-openid");
            req.setJoinStatus(5);
            req.setErpStatus(2);

            controller.apply(req);

            ArgumentCaptor<MktWarehouse> cap = ArgumentCaptor.forClass(MktWarehouse.class);
            verify(onboarding).apply(cap.capture());
            assertThat(cap.getValue().getContactOpenid()).as("contactOpenid 必须由 WhAuthService 绑定,申请时不可指定").isNull();
            assertThat(cap.getValue().getJoinStatus()).as("joinStatus 由服务端状态机推进").isNull();
            assertThat(cap.getValue().getErpStatus()).as("erpStatus 由服务端状态机推进").isNull();
            assertThat(cap.getValue().getCode()).isEqualTo("WH-1");
            assertThat(cap.getValue().getName()).isEqualTo("仓1");
        }

        private MktWarehouse warehouse(Long id) {
            MktWarehouse w = new MktWarehouse();
            w.setId(id);
            w.setCode("WH-1");
            w.setName("仓1");
            return w;
        }
    }

    /** C2c:评级 update 不允许改 code/sort。 */
    @Nested
    class GradeWhitelist {
        @Mock MktCustomerGradeMapper mapper;
        @Mock MktAuditService audit;

        @Test
        void updateNeverSetsCodeOrSort() {
            MktGradeController controller = new MktGradeController(mapper, audit);
            when(mapper.selectById(1L)).thenReturn(grade(1L, "A"));
            when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

            MktCustomerGrade req = grade(1L, "FORGED");
            req.setSort(99);
            controller.update(List.of(req));

            ArgumentCaptor<Wrapper<MktCustomerGrade>> cap = ArgumentCaptor.forClass(Wrapper.class);
            verify(mapper).update(isNull(), cap.capture());
            WrapperAssert wa = WrapperAssert.of((LambdaUpdateWrapper<MktCustomerGrade>) cap.getValue());
            assertThat(wa.setColumns()).doesNotContain("code", "sort");
            assertThat(wa.setColumns()).contains("lease_commission_months", "erp_total_rate");
        }

        @Test
        void updateValidatesUsingPersistedCodeNotForgedOne() {
            MktGradeController controller = new MktGradeController(mapper, audit);
            when(mapper.selectById(1L)).thenReturn(grade(1L, "A"));
            MktCustomerGrade req = grade(1L, "FORGED");
            req.setLeaseCommissionMonths(new java.math.BigDecimal("9"));   // 越界
            assertThatThrownBy(() -> controller.update(List.of(req)))
                    .isInstanceOf(BizException.class).hasMessageContaining("A");
        }

        private MktCustomerGrade grade(Long id, String code) {
            MktCustomerGrade g = new MktCustomerGrade();
            g.setId(id);
            g.setCode(code);
            g.setLeaseCommissionMonths(new java.math.BigDecimal("1"));
            g.setErpTotalRate(new java.math.BigDecimal("10"));
            return g;
        }
    }

    /** C3:份额校验用 DB 全量,只提子集绕不过去。 */
    @Nested
    class PositionFullSetValidation {
        @Mock MktPositionMapper mapper;
        @Mock MktPositionReviewService review;
        @Mock MktAuditService audit;
        @Mock BizSettings bizSettings;

        @Test
        void subsetSubmissionCannotSkipLowerGrade() {
            MktPositionController controller = new MktPositionController(mapper, review, audit, bizSettings, null);
            // DB 全量:P1=50 P2=85 P3=100;本次只提 P2=100(旧实现会放行,造成跳级)
            when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(pos(1L, "P1", 1, 50), pos(2L, "P2", 2, 85), pos(3L, "P3", 3, 100)));
            MktPosition req = pos(2L, "P2", 2, 100);

            assertThatThrownBy(() -> controller.update(List.of(req)))
                    .isInstanceOf(BizException.class).hasMessageContaining("份额");
        }

        @Test
        void fullValidSetPasses() {
            MktPositionController controller = new MktPositionController(mapper, review, audit, bizSettings, null);
            when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(pos(1L, "P1", 1, 50), pos(2L, "P2", 2, 85), pos(3L, "P3", 3, 100)));
            when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

            // 全量提交且合法
            controller.update(List.of(pos(1L, "P1", 1, 50), pos(2L, "P2", 2, 85), pos(3L, "P3", 3, 100)));

            verify(mapper, org.mockito.Mockito.times(3)).update(isNull(), any(Wrapper.class));
        }

        @Test
        void nullSortDoesNotThrowNpe() {
            MktPositionController controller = new MktPositionController(mapper, review, audit, bizSettings, null);
            MktPosition a = pos(1L, "P1", null, 50);
            MktPosition b = pos(2L, "P2", 2, 100);
            when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(a, b));
            when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

            controller.update(List.of(a, b));   // 旧实现 Integer.compare(a.getSort(),..) 会 NPE
        }

        private MktPosition pos(Long id, String code, Integer sort, Integer share) {
            MktPosition p = new MktPosition();
            p.setId(id);
            p.setCode(code);
            p.setSort(sort);
            p.setSharePct(share);
            p.setShareMinPct(0);
            return p;
        }
    }

    /** C7:设推荐人拒绝非正常状态伙伴。 */
    @Nested
    class ReferrerPromoterStatus {
        @Mock MktCustomerAssignmentService assignmentService;
        @Mock CustomerMapper customerMapper;
        @Mock MktPromoterMapper promoterMapper;
        @Mock MktCustomerGradeMapper gradeMapper;
        @Mock MktServiceContractMapper contractMapper;
        @Mock MktLockService lockService;
        @Mock MktAuditService audit;

        @Test
        void rejectsFrozenPromoter() {
            MktCustomerController controller = new MktCustomerController(
                    customerMapper, promoterMapper, gradeMapper, contractMapper, lockService, audit, assignmentService);
            when(customerMapper.selectForUpdate(1L)).thenReturn(customer(1L));
            when(promoterMapper.selectOne(any(Wrapper.class))).thenReturn(promoter(9L, MktPromoterService.ST_FROZEN));

            assertThatThrownBy(() -> controller.referrer(1L, Map.of("inviteCode", "ABC")))
                    .isInstanceOf(BizException.class).hasMessageContaining("状态");
        }

        @Test
        void acceptsNormalPromoter() {
            MktCustomerController controller = new MktCustomerController(
                    customerMapper, promoterMapper, gradeMapper, contractMapper, lockService, audit, assignmentService);
            when(customerMapper.selectForUpdate(1L)).thenReturn(customer(1L));
            when(promoterMapper.selectOne(any(Wrapper.class))).thenReturn(promoter(9L, MktPromoterService.ST_NORMAL));
            when(lockService.activeLockOf(1L)).thenReturn(null);
            when(customerMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

            controller.referrer(1L, Map.of("inviteCode", "ABC"));
            verify(customerMapper).update(isNull(), any(Wrapper.class));
        }

        private Customer customer(Long id) {
            Customer c = new Customer();
            c.setId(id);
            c.setPhone("13800000000");
            return c;
        }

        private MktPromoter promoter(Long id, int status) {
            MktPromoter p = new MktPromoter();
            p.setId(id);
            p.setPhone("13900000000");
            p.setInviteCode("ABC");
            p.setStatus(status);
            return p;
        }
    }
}
