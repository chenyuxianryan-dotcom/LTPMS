package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltpms.entity.ScheduleRevision;
import com.ltpms.entity.User;
import com.ltpms.mapper.ScheduleRevisionMapper;
import com.ltpms.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 修订留痕服务：字段级追加式记录课表/课程的修改历史，供行级标记、明细抽屉与导入冲突提示复用。
 */
@Service
@RequiredArgsConstructor
public class RevisionService {

    public static final String TARGET_SCHEDULE = "SCHEDULE";
    public static final String TARGET_COURSE = "COURSE";

    private final ScheduleRevisionMapper scheduleRevisionMapper;
    private final UserMapper userMapper;

    /**
     * 记录一组字段变更；新旧值相同或均为空的字段自动跳过。
     *
     * @param targetType 目标类型，见 {@link #TARGET_SCHEDULE}/{@link #TARGET_COURSE}
     * @param targetId   目标行 ID
     * @param changes    字段名 → [旧值, 新值]，值为 null 表示原先为空
     * @param sourceRole 操作者角色（ADMIN / TEACHER，服务端判定后传入）
     * @param operatorId 操作者用户 ID
     */
    @Transactional
    public void record(String targetType, Long targetId, Map<String, String[]> changes, String sourceRole, Long operatorId) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String[]> entry : changes.entrySet()) {
            String[] pair = entry.getValue();
            String oldValue = pair[0];
            String newValue = pair[1];
            if (java.util.Objects.equals(oldValue, newValue)) {
                continue;
            }
            ScheduleRevision revision = new ScheduleRevision();
            revision.setTargetType(targetType);
            revision.setTargetId(targetId);
            revision.setFieldName(entry.getKey());
            revision.setOldValue(oldValue);
            revision.setNewValue(newValue);
            revision.setSourceRole(sourceRole);
            revision.setOperatorId(operatorId);
            revision.setCreatedAt(LocalDateTime.now());
            scheduleRevisionMapper.insert(revision);
        }
    }

    /**
     * 查询单个目标的修订明细（含操作人姓名），按时间升序。
     *
     * @param targetType 目标类型
     * @param targetId   目标行 ID
     * @return 修订明细列表
     */
    public List<RevisionView> list(String targetType, Long targetId) {
        List<ScheduleRevision> revisions = scheduleRevisionMapper.selectList(
                new LambdaQueryWrapper<ScheduleRevision>()
                        .eq(ScheduleRevision::getTargetType, targetType)
                        .eq(ScheduleRevision::getTargetId, targetId)
                        .orderByAsc(ScheduleRevision::getCreatedAt)
                        .orderByAsc(ScheduleRevision::getId));
        if (revisions.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> operatorIds = revisions.stream()
                .map(ScheduleRevision::getOperatorId)
                .collect(Collectors.toSet());
        Map<Long, String> nameMap = new HashMap<>();
        for (User user : userMapper.selectBatchIds(operatorIds)) {
            nameMap.put(user.getId(), user.getRealName() != null ? user.getRealName() : user.getUsername());
        }
        return revisions.stream()
                .map(r -> new RevisionView(
                        r.getFieldName(),
                        r.getOldValue(),
                        r.getNewValue(),
                        r.getSourceRole(),
                        nameMap.getOrDefault(r.getOperatorId(), "未知用户"),
                        r.getCreatedAt()))
                .collect(Collectors.toList());
    }

    /**
     * 统计一组目标中各自被修订过的字段条数。
     *
     * @param targetType 目标类型
     * @param targetIds  目标行 ID 集合
     * @return 目标 ID → 修订条数（无修订的不在 map 中）
     */
    public Map<Long, Long> counts(String targetType, Collection<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ScheduleRevision> revisions = scheduleRevisionMapper.selectList(
                new LambdaQueryWrapper<ScheduleRevision>()
                        .eq(ScheduleRevision::getTargetType, targetType)
                        .in(ScheduleRevision::getTargetId, targetIds));
        Map<Long, Long> result = new LinkedHashMap<>();
        for (ScheduleRevision revision : revisions) {
            result.merge(revision.getTargetId(), 1L, Long::sum);
        }
        return result;
    }

    /**
     * 在一组目标中筛出被修订过的目标 ID 集合，供列表接口标注笔形标记。
     *
     * @param targetType 目标类型
     * @param targetIds  目标行 ID 集合
     * @return 被修订过的目标 ID 集合
     */
    public Set<Long> revisedIds(String targetType, Collection<Long> targetIds) {
        return new HashSet<>(counts(targetType, targetIds).keySet());
    }

    /**
     * 修订明细视图（含操作人姓名）。
     */
    public record RevisionView(String fieldName, String oldValue, String newValue,
                               String sourceRole, String operatorName, LocalDateTime createdAt) {
    }
}
