package com.ltpms.controller;

import com.ltpms.common.Result;
import com.ltpms.dto.AvatarRequest;
import com.ltpms.service.AvatarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 用户头像接口：设置内置默认头像 / 上传本地照片作为头像。
 * 教师只能操作自己；管理员可操作任意用户。
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserAvatarController {

    private final AvatarService avatarService;

    /**
     * 设置目标用户的内置默认头像。
     *
     * @param id      目标用户 ID
     * @param request 头像更新请求（preset:avatar-XX）
     * @return 更新后的头像标识
     */
    @PutMapping("/{id}/avatar")
    public Result<Map<String, String>> setAvatar(@PathVariable Long id, @Valid @RequestBody AvatarRequest request) {
        avatarService.setPresetAvatar(id, request.getAvatar(), getCurrentUserId());
        return Result.success(Map.of("avatar", request.getAvatar()));
    }

    /**
     * 上传本地照片作为目标用户头像。
     *
     * @param id   目标用户 ID
     * @param file 图片文件（jpg/png/webp，≤5MB）
     * @return 头像访问 URL
     */
    @PostMapping("/{id}/avatar/upload")
    public Result<Map<String, String>> uploadAvatar(@PathVariable Long id,
                                                    @RequestParam("file") MultipartFile file) {
        String url = avatarService.uploadAvatar(id, file, getCurrentUserId());
        return Result.success(Map.of("avatar", url));
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof Long) {
            return (Long) authentication.getDetails();
        }
        return null;
    }
}
