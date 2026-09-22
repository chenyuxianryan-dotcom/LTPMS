package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltpms.entity.NotificationPreference;
import com.ltpms.mapper.NotificationPreferenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知偏好服务：按用户维护各类型通知的开/关状态，默认全部开启。
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceMapper prefMapper;

    public static final List<String> ALL_TYPES = List.of(
            "MORNING_BRIEF",
            "DEADLINE_WARNING",
            "STAGNATION_ALERT",
            "REVIEW_PENDING",
            "SUBMISSION_ARRIVED",
            "DATASET_NEW_VERSION");

    /** 查询用户偏好；首次访问自动初始化（全部 ON）。 */
    @Transactional
    public Map<String, Boolean> getByUser(Long userId) {
        List<NotificationPreference> rows = prefMapper.selectList(
                new LambdaQueryWrapper<NotificationPreference>()
                        .eq(NotificationPreference::getUserId, userId));
        if (rows.isEmpty()) {
            ensureDefaults(userId);
            Map<String, Boolean> map = new LinkedHashMap<>();
            ALL_TYPES.forEach(t -> map.put(t, true));
            return map;
        }
        Map<String, Boolean> map = new LinkedHashMap<>();
        ALL_TYPES.forEach(t -> map.put(t, true));
        rows.forEach(r -> map.put(r.getNotificationType(), r.getEnabled() == 1));
        return map;
    }

    /** 更新用户偏好（upsert）。 */
    @Transactional
    public void update(Long userId, Map<String, Boolean> prefs) {
        for (Map.Entry<String, Boolean> e : prefs.entrySet()) {
            String type = e.getKey();
            if (!ALL_TYPES.contains(type)) {
                continue;
            }
            int enabled = Boolean.TRUE.equals(e.getValue()) ? 1 : 0;
            NotificationPreference existing = prefMapper.selectOne(
                    new LambdaQueryWrapper<NotificationPreference>()
                            .eq(NotificationPreference::getUserId, userId)
                            .eq(NotificationPreference::getNotificationType, type));
            if (existing == null) {
                NotificationPreference row = new NotificationPreference();
                row.setUserId(userId);
                row.setNotificationType(type);
                row.setEnabled(enabled);
                row.setCreatedAt(LocalDateTime.now());
                row.setUpdatedAt(LocalDateTime.now());
                prefMapper.insert(row);
            } else {
                existing.setEnabled(enabled);
                existing.setUpdatedAt(LocalDateTime.now());
                prefMapper.updateById(existing);
            }
        }
    }

    /** 检查某类型是否开启；无行时默认 ON。 */
    public boolean isEnabled(Long userId, String notificationType) {
        NotificationPreference row = prefMapper.selectOne(
                new LambdaQueryWrapper<NotificationPreference>()
                        .eq(NotificationPreference::getUserId, userId)
                        .eq(NotificationPreference::getNotificationType, notificationType));
        return row == null || row.getEnabled() == 1;
    }

    /** 为用户初始化全部默认行（INSERT IGNORE 语义）。 */
    private void ensureDefaults(Long userId) {
        for (String type : ALL_TYPES) {
            NotificationPreference row = new NotificationPreference();
            row.setUserId(userId);
            row.setNotificationType(type);
            row.setEnabled(1);
            row.setCreatedAt(LocalDateTime.now());
            row.setUpdatedAt(LocalDateTime.now());
            prefMapper.insert(row);
        }
    }
}
