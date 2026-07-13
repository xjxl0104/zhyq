package com.zhyq.park.hui.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.hui.entity.ForumPost;
import com.zhyq.park.hui.entity.ForumReply;
import com.zhyq.park.hui.mapper.ForumPostMapper;
import com.zhyq.park.hui.mapper.ForumReplyMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 园区论坛(含内容审核,规格书 §12)
 * 帖子状态:1待审核 2已发布 3已下架
 */
@Tag(name = "惠企服务-园区论坛")
@RestController
@RequestMapping("/service/forum")
@RequiredArgsConstructor
public class ForumController {

    /** 状态:待审核 */
    public static final int ST_PENDING = 1;
    /** 状态:已发布 */
    public static final int ST_PUBLISHED = 2;
    /** 状态:已下架 */
    public static final int ST_OFFLINE = 3;

    /** 敏感词库(命中即拒绝发帖) */
    private static final String[] SENSITIVE_WORDS = {
        "广告", "代开发票", "赌", "博彩", "贷款", "刷单", "色情", "违禁"
    };

    private final ForumPostMapper forumPostMapper;
    private final ForumReplyMapper forumReplyMapper;

    @Operation(summary = "分页查询帖子")
    @GetMapping("/page")
    public Result<PageResult<ForumPost>> page(@RequestParam(defaultValue = "1") int pageNo,
                                              @RequestParam(defaultValue = "10") int pageSize,
                                              @RequestParam(required = false) String category,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(required = false) String title) {
        LambdaQueryWrapper<ForumPost> qw = new LambdaQueryWrapper<>();
        qw.eq(StringUtils.hasText(category), ForumPost::getCategory, category)
          .eq(status != null, ForumPost::getStatus, status)
          .like(StringUtils.hasText(title), ForumPost::getTitle, title)
          .orderByDesc(ForumPost::getId);
        IPage<ForumPost> p = forumPostMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "帖子详情(含回复列表)")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        ForumPost post = forumPostMapper.selectById(id);
        LambdaQueryWrapper<ForumReply> qw = new LambdaQueryWrapper<>();
        qw.eq(ForumReply::getPostId, id).orderByAsc(ForumReply::getId);
        List<ForumReply> replies = forumReplyMapper.selectList(qw);
        Map<String, Object> data = new HashMap<>();
        data.put("post", post);
        data.put("replies", replies);
        return Result.ok(data);
    }

    @Operation(summary = "发帖(敏感词校验,通过则待审核)")
    @PostMapping
    public Result<Long> add(@RequestBody ForumPost post) {
        checkSensitive(post.getTitle());
        checkSensitive(post.getContent());
        post.setStatus(ST_PENDING);
        post.setReplyCount(0);
        post.setLikeCount(0);
        forumPostMapper.insert(post);
        return Result.ok(post.getId());
    }

    @Operation(summary = "修改帖子")
    @PutMapping
    public Result<Void> update(@RequestBody ForumPost post) {
        forumPostMapper.updateById(post);
        return Result.ok();
    }

    @Operation(summary = "删除帖子(级联删回复)")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        LambdaQueryWrapper<ForumReply> qw = new LambdaQueryWrapper<>();
        qw.eq(ForumReply::getPostId, id);
        forumReplyMapper.delete(qw);
        forumPostMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "审核通过(状态→2已发布)")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        ForumPost post = forumPostMapper.selectById(id);
        if (post != null) {
            post.setStatus(ST_PUBLISHED);
            forumPostMapper.updateById(post);
        }
        return Result.ok();
    }

    @Operation(summary = "下架(状态→3已下架)")
    @PostMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        ForumPost post = forumPostMapper.selectById(id);
        if (post != null) {
            post.setStatus(ST_OFFLINE);
            String reason = body == null ? null : body.get("reason");
            if (StringUtils.hasText(reason)) {
                String content = post.getContent() == null ? "" : post.getContent();
                post.setContent(content + "\n【下架原因】" + reason);
            }
            forumPostMapper.updateById(post);
        }
        return Result.ok();
    }

    @Operation(summary = "回复帖子(仅已发布帖可回复)")
    @PostMapping("/{id}/reply")
    public Result<Long> reply(@PathVariable Long id, @RequestBody ForumReply reply) {
        ForumPost post = forumPostMapper.selectById(id);
        if (post == null) {
            throw new BizException("帖子不存在");
        }
        if (post.getStatus() == null || post.getStatus() != ST_PUBLISHED) {
            throw new BizException("仅已发布的帖子可回复");
        }
        reply.setPostId(id);
        forumReplyMapper.insert(reply);
        LambdaUpdateWrapper<ForumPost> uw = new LambdaUpdateWrapper<>();
        uw.eq(ForumPost::getId, id).setSql("reply_count = reply_count + 1");
        forumPostMapper.update(null, uw);
        return Result.ok(reply.getId());
    }

    @Operation(summary = "点赞")
    @PostMapping("/{id}/like")
    public Result<Void> like(@PathVariable Long id) {
        LambdaUpdateWrapper<ForumPost> uw = new LambdaUpdateWrapper<>();
        uw.eq(ForumPost::getId, id).setSql("like_count = like_count + 1");
        forumPostMapper.update(null, uw);
        return Result.ok();
    }

    @Operation(summary = "论坛统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new HashMap<>();
        data.put("total", forumPostMapper.selectCount(null));
        data.put("pending", forumPostMapper.selectCount(
            new LambdaQueryWrapper<ForumPost>().eq(ForumPost::getStatus, ST_PENDING)));
        data.put("published", forumPostMapper.selectCount(
            new LambdaQueryWrapper<ForumPost>().eq(ForumPost::getStatus, ST_PUBLISHED)));
        data.put("offline", forumPostMapper.selectCount(
            new LambdaQueryWrapper<ForumPost>().eq(ForumPost::getStatus, ST_OFFLINE)));
        return Result.ok(data);
    }

    /** 敏感词校验,命中直接抛业务异常 */
    private void checkSensitive(String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        for (String word : SENSITIVE_WORDS) {
            if (text.contains(word)) {
                throw new BizException("内容包含敏感词,请修改后发布");
            }
        }
    }
}
