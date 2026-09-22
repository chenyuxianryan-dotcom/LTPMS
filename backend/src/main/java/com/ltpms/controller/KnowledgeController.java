package com.ltpms.controller;

import com.ltpms.common.Result;
import com.ltpms.service.KnowledgeService;
import com.ltpms.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 知识库接口（锁① 知识域）：制度与流程（管理员维护）+ 课程资料（教师自助上传），
 * 入库即切分向量化，检索侧由 nlq-service 的 knowledge_search 工具消费。
 */
@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    /** 入库文档（multipart：file + title/category/courseId），同步解析切分向量化 */
    @PostMapping("/documents")
    public Result<KnowledgeService.DocumentView> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam String category,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long courseId) {
        return Result.success(knowledgeService.upload(title, category, courseId, file,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }

    /** 文档列表（可按 category / courseId 过滤，可见范围在服务层收口） */
    @GetMapping("/documents")
    public Result<List<KnowledgeService.DocumentView>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long courseId) {
        return Result.success(knowledgeService.list(category, courseId,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }

    /** 删除文档（软删文档 + 物理清理分段，避免残留段落继续被检索） */
    @DeleteMapping("/documents/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id, SecurityUtils.currentUserId(), SecurityUtils.currentRole());
        return Result.success();
    }

    /** 下载原文件；文件丢失 404 专用文案（不进静态放行链路） */
    @GetMapping("/documents/{id}/download")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable Long id) {
        KnowledgeService.Download download = knowledgeService.prepareDownload(id,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole());
        String encoded = URLEncoder.encode(download.originalFilename(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.CONTENT_TYPE, download.contentType())
                .body(download.resource());
    }
}
