package com.ltpms.controller;

import com.ltpms.common.PageResult;
import com.ltpms.common.Result;
import com.ltpms.entity.Notification;
import com.ltpms.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 通知消息接口，提供当前用户通知的查询、未读数统计及已读标记等功能。
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 分页查询当前用户的通知列表，可按已读/未读状态筛选。
     *
     * @param isRead 是否已读过滤，可选；为空时返回全部
     * @param page   页码，默认 1
     * @param size   每页大小，默认 20
     * @return 分页后的通知列表
     */
    @GetMapping
    public Result<PageResult<Notification>> list(
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(notificationService.list(getCurrentUserId(), isRead, page, size));
    }

    /**
     * 获取当前用户的未读通知数量。
     *
     * @return 未读通知数量
     */
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.success(notificationService.unreadCount(getCurrentUserId()));
    }

    /**
     * 将指定通知标记为已读。
     *
     * @param id 通知 ID
     * @return 操作结果
     */
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id, getCurrentUserId());
        return Result.success();
    }

    /**
     * 将当前用户的所有通知标记为已读。
     *
     * @return 操作结果
     */
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllRead(getCurrentUserId());
        return Result.success();
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Long) {
            return (Long) auth.getDetails();
        }
        return null;
    }
}
