package com.zhyq.park.contract.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.contract.entity.*;
import com.zhyq.park.contract.mapper.*;
import com.zhyq.park.receivable.mapper.ReceivableRegisterMapper;
import com.zhyq.park.receivable.service.ReceivablePlanService;
import com.zhyq.park.workflow.service.WorkflowService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ContractDraftAndCheckoutTest {
    @Test void editingCannotSkipApprovalOrChangeRunningTerms() {
        var mapper=mock(ContractMapper.class);var service=service(mapper,mock(ContractRoomMapper.class),mock(RoomRefMapper.class));
        var current=new Contract();current.setId(1L);current.setStatus(1);current.setVersion(1);when(mapper.selectById(1L)).thenReturn(current);
        var request=new Contract();request.setId(1L);request.setStatus(5);
        assertThatThrownBy(()->service.updateDraft(request)).isInstanceOf(BizException.class).hasMessageContaining("流程");
        current.setStatus(5);request.setStatus(null);
        assertThatThrownBy(()->service.updateDraft(request)).isInstanceOf(BizException.class).hasMessageContaining("草稿");
        verify(mapper,never()).update(any(),any(Wrapper.class));
    }
    @Test void expiredLeaseCanCheckoutAndReleaseItsRooms() {
        var assistant=new MapperBuilderAssistant(new MybatisConfiguration(),"");
        for(var type:List.of(Contract.class,ContractRoom.class,ContractVersion.class))TableInfoHelper.initTableInfo(assistant,type);
        var mapper=mock(ContractMapper.class);var rooms=mock(ContractRoomMapper.class);var roomMapper=mock(RoomRefMapper.class);
        var current=new Contract();current.setId(1L);current.setStatus(8);when(mapper.selectById(1L)).thenReturn(current);
        when(mapper.update(isNull(),any(Wrapper.class))).thenAnswer(call->{
            var update=(LambdaUpdateWrapper<Contract>)call.getArgument(1);update.getSqlSegment();
            assertThat(update.getParamNameValuePairs().values()).contains(8,5,9);return 1;
        });
        var room=new ContractRoom();room.setRoomId(7L);when(rooms.selectList(any(Wrapper.class))).thenReturn(List.of(room));
        service(mapper,rooms,roomMapper).terminate(1L);
        verify(roomMapper).updateById(org.mockito.ArgumentMatchers.<RoomRef>argThat(r->r.getId().equals(7L)&&r.getStatus()==1));
    }
    private ContractService service(ContractMapper mapper, ContractRoomMapper rooms,RoomRefMapper roomMapper){return new ContractService(mapper,rooms,mock(ContractVersionMapper.class),roomMapper,mock(ApprovalRefMapper.class),mock(ApplicationEventPublisher.class),mock(WorkflowService.class),mock(ReceivableRegisterMapper.class),mock(ReceivablePlanService.class));}
}
