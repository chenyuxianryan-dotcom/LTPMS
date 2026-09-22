package com.ltpms.controller;

import com.ltpms.common.Result;
import com.ltpms.service.NotificationPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notification-preferences")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceService prefService;

    @GetMapping
    public Result<Map<String, Boolean>> get() {
        Long userId = getCurrentUserId();
        return Result.success(prefService.getByUser(userId));
    }

    @PutMapping
    public Result<Void> update(@RequestBody Map<String, Boolean> prefs) {
        Long userId = getCurrentUserId();
        prefService.update(userId, prefs);
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
