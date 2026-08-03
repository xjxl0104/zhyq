package com.zhyq.park.receivable.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhyq.park.building.entity.Building;
import com.zhyq.park.building.entity.Floor;
import com.zhyq.park.building.entity.Project;
import com.zhyq.park.building.entity.Room;
import com.zhyq.park.building.mapper.BuildingMapper;
import com.zhyq.park.building.mapper.FloorMapper;
import com.zhyq.park.building.mapper.ProjectMapper;
import com.zhyq.park.building.mapper.RoomMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.importing.entity.ImportRow;
import com.zhyq.park.importing.mapper.ImportRowMapper;
import com.zhyq.park.receivable.dto.ReceivableProvisionPreview;
import com.zhyq.park.receivable.dto.ReceivableProvisionRequest;
import com.zhyq.park.space.service.SpaceSyncService;
import com.zhyq.park.tenant.entity.BizTenant;
import com.zhyq.park.tenant.mapper.BizTenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应收导入·预览确认式自动建档核心:租户去重(按清洗后名字)、合成项目/楼栋、
 * 每个不同楼层串建/复用楼层+房间并依序同步空间树、回填各行 binding 并置 VALID。
 */
@Service
@RequiredArgsConstructor
public class ReceivableProvisionService {

    private static final String ROW_VALID = "VALID";
    private static final String ROW_METADATA = "METADATA";
    private static final String SYNTHETIC_PROJECT = "DIPARK";
    private static final String SYNTHETIC_BUILDING = "DIPARK主楼";
    private static final int TENANT_TYPE_COMPANY = 1;
    private static final int TENANT_TYPE_PERSON = 2;

    private final ImportRowMapper rowMapper;
    private final ObjectMapper objectMapper;
    private final BizTenantMapper tenantMapper;
    private final ProjectMapper projectMapper;
    private final BuildingMapper buildingMapper;
    private final FloorMapper floorMapper;
    private final RoomMapper roomMapper;
    private final SpaceSyncService spaceSyncService;

    /** 读批次各业务行的解析数据,去重产出待建/可复用租户与空间清单。 */
    public ReceivableProvisionPreview preview(Long batchId) {
        List<ImportRow> rows = rowMapper.selectList(new LambdaQueryWrapper<ImportRow>()
                .eq(ImportRow::getBatchId, batchId).orderByAsc(ImportRow::getRowNo));
        Map<String, ReceivableProvisionPreview.TenantItem> tenantByClean = new LinkedHashMap<>();
        Map<String, ReceivableProvisionPreview.SpaceItem> spaceByRaw = new LinkedHashMap<>();
        for (ImportRow r : rows) {
            if (ROW_METADATA.equals(r.getStatus())) {
                continue;
            }
            RowFields f = readRow(r);
            if (isNotBlank(f.tenantRaw)) {
                String clean = TenantNameCleaner.clean(f.tenantRaw);
                tenantByClean.computeIfAbsent(clean, c -> {
                    BizTenant existing = findTenantByName(c);
                    return new ReceivableProvisionPreview.TenantItem(
                            f.tenantRaw, c, guessType(c),
                            existing == null ? null : existing.getId());
                });
            }
            if (isNotBlank(f.floorRaw)) {
                spaceByRaw.computeIfAbsent(f.floorRaw, raw -> {
                    Room existing = findRoomByRoomNo(raw);
                    return new ReceivableProvisionPreview.SpaceItem(
                            raw, SYNTHETIC_PROJECT, SYNTHETIC_BUILDING, raw, raw,
                            existing == null ? null : existing.getId());
                });
            }
        }
        return new ReceivableProvisionPreview(batchId,
                new ArrayList<>(tenantByClean.values()), new ArrayList<>(spaceByRaw.values()));
    }

    /** 建/复用租户 + 合成空间/房源 + 同步空间树 + 回填各行 binding(转 VALID)。 */
    @Transactional(rollbackFor = Exception.class)
    public void provision(Long batchId, ReceivableProvisionRequest req) {
        if (req == null) {
            throw new BizException("建档请求不能为空");
        }
        // 1) 建/复用租户:按 finalName 去重
        Map<String, Long> tenantIdByRaw = new HashMap<>();
        Map<String, Long> tenantIdByFinal = new HashMap<>();
        if (req.tenants() != null) {
            for (ReceivableProvisionRequest.TenantDecision d : req.tenants()) {
                String key = d.finalName();
                if (key == null || key.isBlank()) {
                    throw new BizException("待建租户名不能为空");
                }
                Long id = tenantIdByFinal.get(key);
                if (id == null) {
                    id = resolveTenantId(d, key);
                    tenantIdByFinal.put(key, id);
                }
                if (d.rawName() != null) {
                    tenantIdByRaw.put(d.rawName(), id);
                }
            }
        }

        // 2) 合成项目/楼栋(复用优先),先父后子同步空间树
        Long projectId = ensureProject(SYNTHETIC_PROJECT);
        Long buildingId = ensureBuilding(projectId, SYNTHETIC_BUILDING);
        spaceSyncService.sync("project", projectId);
        spaceSyncService.sync("building", buildingId);

        // 3) 每个楼层串建/复用 floor+room 并依序同步空间树
        Map<String, Long> roomIdByRaw = new HashMap<>();
        if (req.spaces() != null) {
            for (ReceivableProvisionRequest.SpaceDecision s : req.spaces()) {
                String rawFloor = s.rawFloor();
                if (rawFloor == null || rawFloor.isBlank()) {
                    throw new BizException("待建空间楼层串不能为空");
                }
                Long roomId = s.reuseRoomId();
                if (roomId == null) {
                    Room existing = findRoomByRoomNo(rawFloor);
                    if (existing != null) {
                        roomId = existing.getId();
                    } else {
                        Long floorId = ensureFloor(buildingId, projectId, rawFloor);
                        spaceSyncService.sync("floor", floorId);
                        roomId = ensureRoom(floorId, buildingId, projectId, rawFloor);
                        spaceSyncService.sync("room", roomId);
                    }
                }
                roomIdByRaw.put(rawFloor, roomId);
            }
        }

        // 4) 回填各行 binding(tenantRefId + roomId),行转 ROW_VALID
        backfillBindings(batchId, tenantIdByRaw, tenantIdByFinal, roomIdByRaw);
    }

    private Long resolveTenantId(ReceivableProvisionRequest.TenantDecision d, String finalName) {
        if (d.reuseTenantId() != null) {
            return d.reuseTenantId();
        }
        BizTenant existing = findTenantByName(finalName);
        if (existing != null) {
            return existing.getId();
        }
        BizTenant t = new BizTenant();
        t.setName(finalName);
        t.setTenantType(d.tenantType() == null ? TENANT_TYPE_COMPANY : d.tenantType());
        t.setStatus(1);
        t.setCode(uniqueCode("AUTO-T"));
        tenantMapper.insert(t);
        return t.getId();
    }

    private Long ensureProject(String name) {
        Project existing = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getName, name).last("limit 1"));
        if (existing != null) {
            return existing.getId();
        }
        Project p = new Project();
        p.setName(name);
        p.setCode(uniqueCode("AUTO-P"));
        p.setStatus(1);
        projectMapper.insert(p);
        return p.getId();
    }

    private Long ensureBuilding(Long projectId, String name) {
        Building existing = buildingMapper.selectOne(new LambdaQueryWrapper<Building>()
                .eq(Building::getProjectId, projectId).eq(Building::getName, name).last("limit 1"));
        if (existing != null) {
            return existing.getId();
        }
        Building b = new Building();
        b.setProjectId(projectId);
        b.setName(name);
        b.setCode(uniqueCode("AUTO-B"));
        b.setStatus(1);
        buildingMapper.insert(b);
        return b.getId();
    }

    private Long ensureFloor(Long buildingId, Long projectId, String name) {
        Floor existing = floorMapper.selectOne(new LambdaQueryWrapper<Floor>()
                .eq(Floor::getBuildingId, buildingId).eq(Floor::getName, name).last("limit 1"));
        if (existing != null) {
            return existing.getId();
        }
        Floor f = new Floor();
        f.setBuildingId(buildingId);
        f.setProjectId(projectId);
        f.setName(name);
        floorMapper.insert(f);
        return f.getId();
    }

    private Long ensureRoom(Long floorId, Long buildingId, Long projectId, String roomNo) {
        Room r = new Room();
        r.setFloorId(floorId);
        r.setBuildingId(buildingId);
        r.setProjectId(projectId);
        r.setRoomNo(roomNo);
        r.setCode(uniqueCode("AUTO-R"));
        r.setStatus(1);
        roomMapper.insert(r);
        return r.getId();
    }

    /** 遍历该批次业务行,按行的租户名/楼层串查出 id,写回 binding 并置 VALID。 */
    private void backfillBindings(Long batchId, Map<String, Long> tenantIdByRaw,
                                  Map<String, Long> tenantIdByFinal, Map<String, Long> roomIdByRaw) {
        List<ImportRow> rows = rowMapper.selectList(new LambdaQueryWrapper<ImportRow>()
                .eq(ImportRow::getBatchId, batchId).orderByAsc(ImportRow::getRowNo));
        for (ImportRow row : rows) {
            if (ROW_METADATA.equals(row.getStatus())) {
                continue;
            }
            RowFields f = readRow(row);
            Long tenantRefId = resolveTenantRefId(f.tenantRaw, tenantIdByRaw, tenantIdByFinal);
            Long roomId = f.floorRaw == null ? null : roomIdByRaw.get(f.floorRaw);
            if (tenantRefId == null || roomId == null) {
                // 该行缺少建档结果(未在请求清单中),保持原状态不动。
                continue;
            }
            ObjectNode normalized = readObject(row.getNormalizedJson());
            ObjectNode binding = normalized.with("binding");
            binding.put("tenantRefId", tenantRefId);
            binding.putNull("spaceId");
            binding.put("roomId", roomId);
            binding.putNull("contractId");

            List<String> errors = jsonStrings(normalized.path("validationErrors"));
            row.setStatus(errors.isEmpty() ? ROW_VALID : row.getStatus());
            if (errors.isEmpty()) {
                row.setErrorMessage(null);
            }
            row.setNormalizedJson(write(normalized));
            if (rowMapper.updateById(row) != 1) {
                throw new BizException("回填导入行绑定失败,请刷新后重试");
            }
        }
    }

    private Long resolveTenantRefId(String tenantRaw, Map<String, Long> tenantIdByRaw,
                                    Map<String, Long> tenantIdByFinal) {
        if (tenantRaw == null) {
            return null;
        }
        Long byRaw = tenantIdByRaw.get(tenantRaw);
        if (byRaw != null) {
            return byRaw;
        }
        return tenantIdByFinal.get(TenantNameCleaner.clean(tenantRaw));
    }

    private BizTenant findTenantByName(String name) {
        if (name == null) {
            return null;
        }
        return tenantMapper.selectOne(new LambdaQueryWrapper<BizTenant>()
                .eq(BizTenant::getName, name).last("limit 1"));
    }

    private Room findRoomByRoomNo(String roomNo) {
        if (roomNo == null) {
            return null;
        }
        return roomMapper.selectOne(new LambdaQueryWrapper<Room>()
                .eq(Room::getRoomNo, roomNo).last("limit 1"));
    }

    private Integer guessType(String name) {
        if (name == null) {
            return TENANT_TYPE_COMPANY;
        }
        String[] companyTokens = {"公司", "有限", "企业", "厂", "中心", "店", "合作社"};
        for (String token : companyTokens) {
            if (name.contains(token)) {
                return TENANT_TYPE_COMPANY;
            }
        }
        return TENANT_TYPE_PERSON;
    }

    /** 从 ImportRow 的 normalizedJson.rowData 取租户名(tenantNameRaw)与楼层串(spaceNameRaw)。 */
    private RowFields readRow(ImportRow row) {
        ObjectNode normalized = readObject(row.getNormalizedJson());
        JsonNode data = normalized.path("rowData");
        return new RowFields(text(data, "tenantNameRaw"), text(data, "spaceNameRaw"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static List<String> jsonStrings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(v -> values.add(v.asText()));
        }
        return values;
    }

    private String uniqueCode(String prefix) {
        return prefix + "-" + System.currentTimeMillis() + "-" + Math.abs(java.util.UUID.randomUUID().hashCode());
    }

    private ObjectNode readObject(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node instanceof ObjectNode object) {
                return object;
            }
            throw new BizException("导入行结构不正确");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BizException("导入行 JSON 无法读取");
        }
    }

    private String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BizException("导入审计数据序列化失败");
        }
    }

    private static final class RowFields {
        private final String tenantRaw;
        private final String floorRaw;

        private RowFields(String tenantRaw, String floorRaw) {
            this.tenantRaw = tenantRaw;
            this.floorRaw = floorRaw;
        }
    }
}
