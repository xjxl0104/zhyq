package com.zhyq.park.suggestion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.suggestion.entity.Suggestion;
import com.zhyq.park.suggestion.entity.SuggestionImage;
import com.zhyq.park.suggestion.entity.SuggestionLog;
import com.zhyq.park.suggestion.mapper.SuggestionImageMapper;
import com.zhyq.park.suggestion.mapper.SuggestionLogMapper;
import com.zhyq.park.suggestion.mapper.SuggestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final SuggestionMapper suggestionMapper;
    private final SuggestionImageMapper imageMapper;
    private final SuggestionLogMapper logMapper;

    private static final Map<Integer, Set<Integer>> TRANSITIONS = Map.of(
            1, Set.of(2, 6),
            2, Set.of(3, 6),
            3, Set.of(4, 5, 6),
            4, Set.of(6),
            5, Set.of(6),
            6, Set.of()
    );

    @Transactional
    public Suggestion create(Suggestion s, List<Long> fileIds) {
        s.setStatus(1);
        if (s.getPriority() == null) s.setPriority(0);
        suggestionMapper.insert(s);

        if (fileIds != null) {
            if (fileIds.size() > 5) {
                throw new BizException("最多上传5张图片");
            }
            for (int i = 0; i < fileIds.size(); i++) {
                SuggestionImage img = new SuggestionImage();
                img.setSuggestionId(s.getId());
                img.setFileId(fileIds.get(i));
                img.setSortOrder(i);
                img.setCreatedAt(LocalDateTime.now());
                imageMapper.insert(img);
            }
        }

        writeLog(s.getId(), "created", null, 1, s.getUserId(), null, null);
        return s;
    }

    public Page<Suggestion> myList(Long userId, int page, int size) {
        return suggestionMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Suggestion>()
                        .eq(Suggestion::getUserId, userId)
                        .orderByDesc(Suggestion::getCreateTime));
    }

    public Page<Suggestion> manageList(Integer status, Integer type, String module,
                                       Long deptId, int page, int size) {
        LambdaQueryWrapper<Suggestion> q = new LambdaQueryWrapper<Suggestion>()
                .eq(status != null, Suggestion::getStatus, status)
                .eq(type != null, Suggestion::getType, type)
                .eq(module != null, Suggestion::getModule, module)
                .eq(deptId != null, Suggestion::getDeptId, deptId)
                .orderByDesc(Suggestion::getCreateTime);
        return suggestionMapper.selectPage(new Page<>(page, size), q);
    }

    public Suggestion getDetail(Long id) {
        Suggestion s = suggestionMapper.selectById(id);
        if (s == null) throw new BizException("建议不存在");
        return s;
    }

    public List<SuggestionImage> getImages(Long suggestionId) {
        return imageMapper.selectList(new LambdaQueryWrapper<SuggestionImage>()
                .eq(SuggestionImage::getSuggestionId, suggestionId)
                .orderByAsc(SuggestionImage::getSortOrder));
    }

    public List<SuggestionLog> getLogs(Long suggestionId) {
        return logMapper.selectList(new LambdaQueryWrapper<SuggestionLog>()
                .eq(SuggestionLog::getSuggestionId, suggestionId)
                .orderByAsc(SuggestionLog::getCreatedAt));
    }

    @Transactional
    public void changeStatus(Long id, Integer toStatus, Long operatorId,
                             String operatorName, String remark) {
        Suggestion s = getDetail(id);
        Integer fromStatus = s.getStatus();

        Set<Integer> allowed = TRANSITIONS.getOrDefault(fromStatus, Set.of());
        if (!allowed.contains(toStatus)) {
            throw new BizException("非法状态流转: " + fromStatus + " → " + toStatus);
        }

        if (toStatus == 5 && s.getType() != 2) {
            throw new BizException("仅建议类型可标记为已采纳");
        }

        if (toStatus == 6 && (remark == null || remark.isBlank())) {
            throw new BizException("关闭必须填写原因");
        }

        s.setStatus(toStatus);
        if (toStatus == 4 || toStatus == 5) {
            s.setResolvedAt(LocalDateTime.now());
        }
        if (toStatus == 6) {
            s.setCloseReason(remark);
        }
        suggestionMapper.updateById(s);

        writeLog(id, "status_changed", fromStatus, toStatus, operatorId, operatorName, remark);
    }

    @Transactional
    public void assign(Long id, Long assigneeId, Long operatorId, String operatorName) {
        Suggestion s = getDetail(id);
        if (s.getStatus() > 3) {
            throw new BizException("当前状态不可指派");
        }
        s.setAssigneeId(assigneeId);
        suggestionMapper.updateById(s);

        writeLog(id, "assigned", null, null, operatorId, operatorName,
                "指派给用户ID: " + assigneeId);
    }

    private void writeLog(Long suggestionId, String action, Integer from, Integer to,
                          Long operatorId, String operatorName, String remark) {
        SuggestionLog log = new SuggestionLog();
        log.setSuggestionId(suggestionId);
        log.setAction(action);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName != null ? operatorName : "system");
        log.setRemark(remark);
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }
}
