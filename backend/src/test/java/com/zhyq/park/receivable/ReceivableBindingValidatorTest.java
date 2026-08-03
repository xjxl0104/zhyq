package com.zhyq.park.receivable;

import com.zhyq.park.building.entity.Room;
import com.zhyq.park.building.mapper.RoomMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.contract.entity.Contract;
import com.zhyq.park.contract.mapper.ContractMapper;
import com.zhyq.park.contract.mapper.ContractRoomMapper;
import com.zhyq.park.receivable.dto.ReceivableBindRequest;
import com.zhyq.park.receivable.service.ReceivableBindingValidator;
import com.zhyq.park.space.entity.SpaceNode;
import com.zhyq.park.space.mapper.SpaceNodeMapper;
import com.zhyq.park.tenant.entity.BizTenant;
import com.zhyq.park.tenant.mapper.BizTenantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReceivableBindingValidatorTest {
    private final BizTenantMapper tenants = mock(BizTenantMapper.class);
    private final ContractMapper contracts = mock(ContractMapper.class);
    private final ContractRoomMapper contractRooms = mock(ContractRoomMapper.class);
    private final RoomMapper rooms = mock(RoomMapper.class);
    private final SpaceNodeMapper spaces = mock(SpaceNodeMapper.class);
    private ReceivableBindingValidator validator;
    private Contract contract;

    @BeforeEach
    void setUp() {
        validator = new ReceivableBindingValidator(tenants, contracts, contractRooms, rooms, spaces);
        BizTenant tenant = new BizTenant();
        tenant.setId(11L);
        tenant.setTenantId(1L);
        when(tenants.selectById(11L)).thenReturn(tenant);

        contract = new Contract();
        contract.setId(14L);
        contract.setTenantId(1L);
        contract.setTenantRefId(11L);
        contract.setProjectId(7L);
        when(contracts.selectById(14L)).thenReturn(contract);

        SpaceNode selected = space(12L, "/70/12/", "floor", 22L);
        when(spaces.selectById(12L)).thenReturn(selected);
        when(spaces.selectOne(any())).thenReturn(space(70L, "/70/", "project", 7L));
    }

    @Test
    void acceptsSpaceWithinTheContractsProject() {
        assertDoesNotThrow(() -> validator.validate(1L,
                new ReceivableBindRequest(100L, 11L, 12L, null, 14L)));
    }

    @Test
    void rejectsContractBelongingToAnotherTenant() {
        contract.setTenantRefId(99L);
        assertThrows(BizException.class, () -> validator.validate(1L,
                new ReceivableBindRequest(100L, 11L, 12L, null, 14L)));
    }

    @Test
    void requiresRoomToBelongToContractAndSelectedSpace() {
        Room room = new Room();
        room.setId(13L);
        room.setTenantId(1L);
        when(rooms.selectById(13L)).thenReturn(room);
        when(contractRooms.selectCount(any())).thenReturn(0L);

        assertThrows(BizException.class, () -> validator.validate(1L,
                new ReceivableBindRequest(100L, 11L, 12L, 13L, 14L)));
    }

    @Test
    void acceptsContractRoomInsideSelectedSpace() {
        Room room = new Room();
        room.setId(13L);
        room.setTenantId(1L);
        when(rooms.selectById(13L)).thenReturn(room);
        when(contractRooms.selectCount(any())).thenReturn(1L);
        when(spaces.selectOne(any())).thenReturn(space(130L, "/70/12/130/", "room", 13L));

        assertDoesNotThrow(() -> validator.validate(1L,
                new ReceivableBindRequest(100L, 11L, 12L, 13L, 14L)));
    }

    private static SpaceNode space(Long id, String path, String refType, Long refId) {
        SpaceNode node = new SpaceNode();
        node.setId(id);
        node.setTenantId(1L);
        node.setPath(path);
        node.setRefType(refType);
        node.setRefId(refId);
        node.setStatus(1);
        return node;
    }
}
