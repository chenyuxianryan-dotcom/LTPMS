package com.ltpms.controller;

import com.ltpms.service.AvatarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件访问接口：提供上传头像等文件的读取（公开访问，仅限图片资源）。
 * <img> 标签无法携带 Authorization 头，因此该路径在 SecurityConfig 中放行。
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final AvatarService avatarService;

    /**
     * 读取上传的头像文件。
     *
     * @param filename 文件名（UUID + 扩展名，防路径穿越）
     * @return 图片字节流
     */
    @GetMapping("/avatar/{filename:.+}")
    public ResponseEntity<byte[]> avatar(@PathVariable String filename) {
        byte[] data = avatarService.readAvatarFile(filename);
        MediaType type = MediaType.parseMediaType(avatarService.resolveContentType(filename));
        return ResponseEntity.ok()
                .contentType(type)
                .body(data);
    }
}
