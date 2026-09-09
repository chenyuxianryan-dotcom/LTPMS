package com.ltpms.service;

import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.entity.User;
import com.ltpms.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 头像服务：内置默认头像（preset）设置、本地照片上传、上传文件存储。
 * 权限规则：本人或管理员可修改目标用户头像。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarService {

    private final UserMapper userMapper;

    /** 内置默认头像白名单（与前端 src/assets/avatars 下的文件一一对应） */
    public static final Set<String> PRESET_AVATARS = Set.of(
            "preset:avatar-01", "preset:avatar-02", "preset:avatar-03", "preset:avatar-04", "preset:avatar-05",
            "preset:avatar-06", "preset:avatar-07", "preset:avatar-08", "preset:avatar-09", "preset:avatar-10");

    /** 上传文件允许的扩展名 → Content-Type */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp");

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L;

    @Value("${ltpms.upload.avatar-dir:./uploads/avatar}")
    private String avatarDir;

    /**
     * 设置内置默认头像（preset）。
     *
     * @param targetUserId 目标用户 ID
     * @param avatar       头像标识（preset:avatar-XX）
     * @param operatorId   操作人 ID（用于权限校验：本人或管理员）
     */
    public void setPresetAvatar(Long targetUserId, String avatar, Long operatorId) {
        checkPermission(targetUserId, operatorId);
        if (!PRESET_AVATARS.contains(avatar)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "未知的内置头像");
        }
        updateAvatarColumn(targetUserId, avatar);
    }

    /**
     * 上传本地照片作为头像，存储后写回 user.avatar。
     *
     * @param targetUserId 目标用户 ID
     * @param file         上传的图片文件（jpg/png/webp，≤5MB）
     * @param operatorId   操作人 ID（用于权限校验：本人或管理员）
     * @return 头像访问 URL（/api/v1/files/avatar/xxx.png）
     */
    public String uploadAvatar(Long targetUserId, MultipartFile file, Long operatorId) {
        checkPermission(targetUserId, operatorId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择头像图片文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "头像图片不能超过 5MB");
        }

        String original = StringUtils.getFilename(file.getOriginalFilename());
        String ext = original == null ? "" : original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String contentType = ALLOWED_TYPES.get(ext);
        if (contentType == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 JPG / PNG / WebP 格式图片");
        }

        try {
            Path dir = Paths.get(avatarDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path target = dir.resolve(filename);
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            String url = "/api/v1/files/avatar/" + filename;
            updateAvatarColumn(targetUserId, url);
            log.info("User {} avatar uploaded by {}: {}", targetUserId, operatorId, filename);
            return url;
        } catch (IOException e) {
            log.error("Save avatar file failed", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "头像保存失败，请稍后重试");
        }
    }

    /**
     * 读取上传的头像文件内容。
     *
     * @param filename 文件名（仅允许字母数字下划线点横线）
     * @return 文件字节数组
     */
    public byte[] readAvatarFile(String filename) {
        if (!filename.matches("[a-zA-Z0-9._-]+") || filename.contains("..")) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        Path path = Paths.get(avatarDir).toAbsolutePath().normalize().resolve(filename);
        if (!path.startsWith(Paths.get(avatarDir).toAbsolutePath().normalize()) || !Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            log.error("Read avatar file failed: {}", filename, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件读取失败");
        }
    }

    /** 根据扩展名推断 Content-Type */
    public String resolveContentType(String filename) {
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return ALLOWED_TYPES.getOrDefault(ext, "application/octet-stream");
    }

    private void checkPermission(Long targetUserId, Long operatorId) {
        if (operatorId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!operatorId.equals(targetUserId)) {
            User operator = userMapper.selectById(operatorId);
            if (operator == null || !"ADMIN".equals(operator.getRole())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只能修改自己的头像");
            }
        }
    }

    private void updateAvatarColumn(Long userId, String avatar) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        User update = new User();
        update.setId(userId);
        update.setAvatar(avatar);
        userMapper.updateById(update);
    }
}
