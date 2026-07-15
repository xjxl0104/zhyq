package com.zhyq.park.space.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.building.entity.*;
import com.zhyq.park.building.mapper.*;
import com.zhyq.park.space.entity.SpaceNode;
import com.zhyq.park.space.mapper.SpaceNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpaceSyncService {

    private final SpaceNodeMapper spaceMapper;
    private final ProjectMapper projectMapper;
    private final BuildingMapper buildingMapper;
    private final FloorMapper floorMapper;
    private final RoomMapper roomMapper;

    /** 全量回填：先父后子，保证 parent/path 正确。幂等。 */
    @Transactional
    public void reconcile() {
        for (Project p : projectMapper.selectList(null)) syncProject(p);
        for (Building b : buildingMapper.selectList(null)) syncBuilding(b);
        for (Floor f : floorMapper.selectList(null)) syncFloor(f);
        for (Room r : roomMapper.selectList(null)) syncRoom(r);
        log.info("[space] reconcile done");
    }

    public void sync(String refType, Long refId) {
        switch (refType) {
            case "project"  -> syncProject(projectMapper.selectById(refId));
            case "building" -> syncBuilding(buildingMapper.selectById(refId));
            case "floor"    -> syncFloor(floorMapper.selectById(refId));
            case "room"     -> syncRoom(roomMapper.selectById(refId));
            default -> log.warn("[space] unknown refType {}", refType);
        }
    }

    public void remove(String refType, Long refId) {
        SpaceNode n = findByRef(refType, refId);
        if (n != null) spaceMapper.deleteById(n.getId());   // 逻辑删除
    }

    public Long resolveSpaceId(String refType, Long refId) {
        SpaceNode n = findByRef(refType, refId);
        return n == null ? null : n.getId();
    }

    private void syncProject(Project p) {
        if (p == null) return;
        upsert("project", p.getId(), null, "PROJECT", SpaceCodec.PREFIX_PROJECT, p.getCode(), p.getName());
    }

    private void syncBuilding(Building b) {
        if (b == null) return;
        SpaceNode parent = findByRef("project", b.getProjectId());
        upsert("building", b.getId(), parent, "BUILDING", SpaceCodec.PREFIX_BUILDING, b.getCode(), b.getName());
    }

    private void syncFloor(Floor f) {
        if (f == null) return;
        SpaceNode parent = findByRef("building", f.getBuildingId());
        String rawCode = f.getFloorNo() != null ? String.valueOf(f.getFloorNo()) : null;
        upsert("floor", f.getId(), parent, "FLOOR", SpaceCodec.PREFIX_FLOOR, rawCode, floorName(f));
    }

    private void syncRoom(Room r) {
        if (r == null) return;
        SpaceNode parent = findByRef("floor", r.getFloorId());
        upsert("room", r.getId(), parent, "ROOM", SpaceCodec.PREFIX_ROOM, r.getCode(), r.getRoomNo());
    }

    private String floorName(Floor f) {
        return f.getName() != null ? f.getName() : ("F#" + f.getId());
    }

    private void upsert(String refType, Long refId, SpaceNode parent,
                        String type, String prefix, String rawCode, String name) {
        SpaceNode existing = findByRef(refType, refId);
        String parentCode = parent == null ? null : parent.getCode();
        String parentPath = parent == null ? null : parent.getPath();
        Long parentId = parent == null ? null : parent.getId();
        int level = parent == null ? 1 : parent.getLevel() + 1;

        SpaceNode node = existing != null ? existing : new SpaceNode();
        node.setParentId(parentId);
        node.setLevel(level);
        node.setType(type);
        node.setCode(SpaceCodec.childCode(parentCode, prefix, rawCode, refId));
        node.setName(name == null ? "" : name);
        node.setRefType(refType);
        node.setRefId(refId);
        if (node.getStatus() == null) node.setStatus(1);
        if (node.getSort() == null) node.setSort(0);

        if (existing == null) {
            spaceMapper.insert(node);
            node.setPath(SpaceCodec.buildPath(parentPath, node.getId()));
            spaceMapper.updateById(node);
        } else {
            node.setPath(SpaceCodec.buildPath(parentPath, node.getId()));
            spaceMapper.updateById(node);
        }
    }

    private SpaceNode findByRef(String refType, Long refId) {
        if (refType == null || refId == null) return null;
        return spaceMapper.selectOne(new LambdaQueryWrapper<SpaceNode>()
                .eq(SpaceNode::getRefType, refType)
                .eq(SpaceNode::getRefId, refId)
                .last("limit 1"));
    }
}
