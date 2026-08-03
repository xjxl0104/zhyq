package com.zhyq.park.receivable.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.building.entity.Room;
import com.zhyq.park.building.mapper.RoomMapper;
import com.zhyq.park.common.base.BaseEntity;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.contract.entity.Contract;
import com.zhyq.park.contract.entity.ContractRoom;
import com.zhyq.park.contract.mapper.ContractMapper;
import com.zhyq.park.contract.mapper.ContractRoomMapper;
import com.zhyq.park.receivable.dto.ReceivableBindRequest;
import com.zhyq.park.space.entity.SpaceNode;
import com.zhyq.park.space.mapper.SpaceNodeMapper;
import com.zhyq.park.tenant.entity.BizTenant;
import com.zhyq.park.tenant.mapper.BizTenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ReceivableBindingValidator {
    private final BizTenantMapper tenantMapper;
    private final ContractMapper contractMapper;
    private final ContractRoomMapper contractRoomMapper;
    private final RoomMapper roomMapper;
    private final SpaceNodeMapper spaceMapper;

    public void validate(Long systemTenantId, ReceivableBindRequest request) {
        if (request == null || request.tenantRefId() == null
                || (request.spaceId() == null && request.roomId() == null)) {
            throw new BizException("请绑定租户及空间/房间");
        }
        BizTenant tenant = tenantMapper.selectById(request.tenantRefId());
        requireSameSystemTenant(tenant, systemTenantId, "租户不存在或不属于当前园区");

        Contract contract = null;
        if (request.contractId() != null) {
            contract = contractMapper.selectById(request.contractId());
            requireSameSystemTenant(contract, systemTenantId, "合同不存在或不属于当前园区");
            if (!Objects.equals(contract.getTenantRefId(), request.tenantRefId())) {
                throw new BizException("合同与所选租户不一致");
            }
        }

        SpaceNode selectedSpace = request.spaceId() == null ? null : spaceMapper.selectById(request.spaceId());
        if (request.spaceId() != null) {
            requireUsableSpace(selectedSpace, systemTenantId, "空间不存在、已停用或不属于当前园区");
        }

        if (request.roomId() != null) {
            validateRoom(systemTenantId, request, selectedSpace);
        } else if (contract != null) {
            validateProjectSpace(contract, selectedSpace, systemTenantId);
        }
    }

    private void validateRoom(Long systemTenantId, ReceivableBindRequest request,
                              SpaceNode selectedSpace) {
        Room room = roomMapper.selectById(request.roomId());
        requireSameSystemTenant(room, systemTenantId, "房间不存在或不属于当前园区");
        Long linkCount = contractRoomMapper.selectCount(new LambdaQueryWrapper<ContractRoom>()
                .eq(ContractRoom::getContractId, request.contractId())
                .eq(ContractRoom::getRoomId, request.roomId()));
        if (linkCount == null || linkCount == 0) {
            throw new BizException("所选房间不属于该合同");
        }
        if (selectedSpace == null) return;
        SpaceNode roomSpace = findSpace("room", request.roomId());
        requireUsableSpace(roomSpace, systemTenantId, "房间尚未同步到统一空间树");
        if (!isSameOrDescendant(roomSpace, selectedSpace)) {
            throw new BizException("所选空间与房间不一致");
        }
    }

    private void validateProjectSpace(Contract contract, SpaceNode selectedSpace, Long systemTenantId) {
        if (selectedSpace == null || contract.getProjectId() == null) {
            throw new BizException("合同或空间缺少项目归属");
        }
        SpaceNode projectSpace = findSpace("project", contract.getProjectId());
        requireUsableSpace(projectSpace, systemTenantId, "合同项目尚未同步到统一空间树");
        if (!isSameOrDescendant(selectedSpace, projectSpace)) {
            throw new BizException("所选空间不属于合同项目");
        }
    }

    private SpaceNode findSpace(String refType, Long refId) {
        return spaceMapper.selectOne(new LambdaQueryWrapper<SpaceNode>()
                .eq(SpaceNode::getRefType, refType).eq(SpaceNode::getRefId, refId));
    }

    private static boolean isSameOrDescendant(SpaceNode candidate, SpaceNode ancestor) {
        if (Objects.equals(candidate.getId(), ancestor.getId())) return true;
        return candidate.getPath() != null && ancestor.getPath() != null
                && candidate.getPath().startsWith(ancestor.getPath());
    }

    private static void requireUsableSpace(SpaceNode space, Long tenantId, String message) {
        requireSameSystemTenant(space, tenantId, message);
        if (space.getStatus() != null && space.getStatus() == 0) throw new BizException(message);
    }

    private static void requireSameSystemTenant(BaseEntity entity, Long tenantId, String message) {
        if (entity == null || (entity.getTenantId() != null && !Objects.equals(entity.getTenantId(), tenantId))) {
            throw new BizException(message);
        }
    }
}
