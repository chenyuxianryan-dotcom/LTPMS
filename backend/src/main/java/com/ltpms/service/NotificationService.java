package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.common.PageResult;
import com.ltpms.entity.Notification;
import com.ltpms.entity.User;
import com.ltpms.mapper.NotificationMapper;
import com.ltpms.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知服务，提供通知的分页查询、未读数统计、已读标记及新学期提醒创建等能力。
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;

    /**
     * 分页查询通知列表，包含指向当前用户的通知与全员广播（userId 为 null）通知，
     * 可按已读/未读状态过滤。
     *
     * @param userId 当前用户 ID
     * @param isRead 是否已读过滤，可选
     * @param page   页码，从 1 开始
     * @param size   每页大小
     * @return 分页后的通知列表
     */
    public PageResult<Notification> list(Long userId, Boolean isRead, int page, int size) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(Notification::getUserId, userId).or().isNull(Notification::getUserId));
        if (isRead != null) {
            wrapper.eq(Notification::getIsRead, isRead ? 1 : 0);
        }
        wrapper.orderByDesc(Notification::getCreatedAt);
        Page<Notification> result = notificationMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result);
    }

    /**
     * 统计当前用户的未读通知数量（含全员广播通知）。
     *
     * @param userId 当前用户 ID
     * @return 未读通知数量
     */
    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getIsRead, 0)
                        .and(w -> w.eq(Notification::getUserId, userId).or().isNull(Notification::getUserId)));
    }

    /**
     * 将指定通知标记为已读，仅允许操作本人通知。
     *
     * @param id     通知 ID
     * @param userId 当前用户 ID
     * @throws BusinessException 当通知不存在或非本人通知时抛出
     */
    @Transactional
    public void markRead(Long id, Long userId) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "通知不存在");
        }
        if (notification.getUserId() != null && !notification.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Notification update = new Notification();
        update.setId(id);
        update.setIsRead(1);
        notificationMapper.updateById(update);
    }

    /**
     * 将当前用户的全部未读通知（含全员广播）标记为已读。
     *
     * @param userId 当前用户 ID
     */
    @Transactional
    public void markAllRead(Long userId) {
        Notification update = new Notification();
        update.setIsRead(1);
        notificationMapper.update(update,
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getIsRead, 0)
                        .and(w -> w.eq(Notification::getUserId, userId).or().isNull(Notification::getUserId)));
    }

    /**
     * 为所有管理员创建新学期提醒通知。
     */
    @Transactional
    public void createSemesterRemind() {
        List<User> admins = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getRole, "ADMIN"));
        for (User admin : admins) {
            Notification notification = new Notification();
            notification.setUserId(admin.getId());
            notification.setTitle("新学期提醒");
            notification.setContent("新学期即将开始，请及时创建新学期并导入课表。");
            notification.setType("SEMESTER_REMIND");
            notification.setRefUrl("/semesters");
            notification.setIsRead(0);
            notification.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(notification);
        }
    }
}
