package com.zhyq.park.space.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.space.entity.SpaceNode;
import com.zhyq.park.space.mapper.SpaceNodeMapper;
import com.zhyq.park.space.service.SpaceSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "空间树")
@RestController
@RequestMapping("/space")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceNodeMapper spaceMapper;
    private final SpaceSyncService syncService;

    @Operation(summary = "完整空间树")
    @GetMapping("/tree")
    public Result<List<SpaceNode>> tree() {
        return Result.ok(spaceMapper.selectList(new LambdaQueryWrapper<SpaceNode>()
                .eq(SpaceNode::getStatus, 1)
                .orderByAsc(SpaceNode::getLevel).orderByAsc(SpaceNode::getSort)));
    }

    @Operation(summary = "子节点")
    @GetMapping("/children")
    public Result<List<SpaceNode>> children(@RequestParam(required = false) Long parentId) {
        LambdaQueryWrapper<SpaceNode> w = new LambdaQueryWrapper<SpaceNode>().orderByAsc(SpaceNode::getSort);
        if (parentId == null) w.isNull(SpaceNode::getParentId); else w.eq(SpaceNode::getParentId, parentId);
        return Result.ok(spaceMapper.selectList(w));
    }

    @Operation(summary = "子树id集合（含自身）")
    @GetMapping("/{id}/subtree-ids")
    public Result<List<Long>> subtreeIds(@PathVariable Long id) {
        SpaceNode self = spaceMapper.selectById(id);
        if (self == null) return Result.ok(new ArrayList<>());
        List<Long> ids = new ArrayList<>();
        for (SpaceNode n : spaceMapper.selectSubtree(self.getPath() + "%")) ids.add(n.getId());
        return Result.ok(ids);
    }

    @Operation(summary = "面包屑路径")
    @GetMapping("/{id}/path")
    public Result<List<SpaceNode>> path(@PathVariable Long id) {
        SpaceNode self = spaceMapper.selectById(id);
        List<SpaceNode> chain = new ArrayList<>();
        if (self == null || self.getPath() == null) return Result.ok(chain);
        for (String seg : self.getPath().split("/")) {
            if (seg.isBlank()) continue;
            SpaceNode n = spaceMapper.selectById(Long.valueOf(seg));
            if (n != null) chain.add(n);
        }
        return Result.ok(chain);
    }

    @Operation(summary = "按旧对象反查空间节点")
    @GetMapping("/by-ref")
    public Result<SpaceNode> byRef(@RequestParam String refType, @RequestParam Long refId) {
        return Result.ok(spaceMapper.selectOne(new LambdaQueryWrapper<SpaceNode>()
                .eq(SpaceNode::getRefType, refType).eq(SpaceNode::getRefId, refId).last("limit 1")));
    }

    @Operation(summary = "全量回填空间树（幂等）")
    @PostMapping("/reconcile")
    public Result<Void> reconcile() {
        syncService.reconcile();
        return Result.ok();
    }
}
