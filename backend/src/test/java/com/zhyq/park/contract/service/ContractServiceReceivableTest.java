package com.zhyq.park.contract.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.contract.entity.Contract;
import com.zhyq.park.contract.mapper.ApprovalRefMapper;
import com.zhyq.park.contract.mapper.ContractMapper;
import com.zhyq.park.contract.mapper.ContractRoomMapper;
import com.zhyq.park.contract.mapper.ContractVersionMapper;
import com.zhyq.park.contract.mapper.RoomRefMapper;
import com.zhyq.park.receivable.dto.ReceivableGenerateResult;
import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.mapper.ReceivableRegisterMapper;
import com.zhyq.park.receivable.service.ReceivablePlanService;
import com.zhyq.park.workflow.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractServiceReceivableTest {
    @Mock private ContractMapper contractMapper;
    @Mock private ContractRoomMapper contractRoomMapper;
    @Mock private ContractVersionMapper contractVersionMapper;
    @Mock private RoomRefMapper roomRefMapper;
    @Mock private ApprovalRefMapper approvalRefMapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private WorkflowService workflowService;
    @Mock private ReceivableRegisterMapper registerMapper;
    @Mock private ReceivablePlanService planService;

    private ContractService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "contract-test"), Contract.class);
        service = new ContractService(
                contractMapper, contractRoomMapper, contractVersionMapper,
                roomRefMapper, approvalRefMapper, eventPublisher, workflowService,
                registerMapper, planService);
    }

    @Test
    void approvalIsBlockedBeforeStateChangeWhenConfirmedRegisterIsMissing() {
        when(contractMapper.selectById(50L)).thenReturn(contract());
        when(registerMapper.selectList(any())).thenReturn(List.of());

        assertThrows(BizException.class, () -> service.approve(50L));

        verify(contractMapper, never()).update(any(), any());
        verify(planService, never()).generate(any(Long.class));
    }

    @Test
    void approvalGeneratesPlansForEveryConfirmedRegister() {
        when(contractMapper.selectById(50L)).thenReturn(contract());
        ReceivableRegister first = register(7L);
        ReceivableRegister second = register(8L);
        when(registerMapper.selectList(any())).thenReturn(List.of(first, second));
        when(contractMapper.update(any(), any())).thenReturn(1);
        when(contractRoomMapper.selectList(any())).thenReturn(List.of());
        when(planService.generate(any(Long.class))).thenReturn(new ReceivableGenerateResult(1, 1, 0));

        service.approve(50L);

        verify(planService).generate(7L);
        verify(planService).generate(8L);
    }

    private static Contract contract() {
        Contract contract = new Contract();
        contract.setId(50L);
        contract.setCode("HT-50");
        contract.setTenantRefId(20L);
        contract.setStartDate(LocalDate.of(2026, 5, 1));
        contract.setEndDate(LocalDate.of(2027, 4, 30));
        return contract;
    }

    private static ReceivableRegister register(long id) {
        ReceivableRegister register = new ReceivableRegister();
        register.setId(id);
        register.setStatus("CONFIRMED");
        return register;
    }
}
