package com.ltpms.controller;

import com.ltpms.common.Result;
import com.ltpms.dto.project.DatasetUpdateRequest;
import com.ltpms.service.DatasetService;
import com.ltpms.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 课题数据集接口：版本链上传/查看、成员鉴权下载（Content-Disposition 编码回原名）、
 * 流式预览（r2-B5）、软删（Q5/B3 权限粒度）。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;

    /** 数据集版本链（成员可见） */
    @GetMapping("/datasets/{id}/versions")
    public Result<List<DatasetService.VersionView>> versions(@PathVariable Long id) {
        return Result.success(datasetService.listVersions(id,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }

    /** 上传新版本（项目成员全员可传，只增不覆盖） */
    @PostMapping("/datasets/{id}/versions")
    public Result<DatasetService.VersionView> addVersion(@PathVariable Long id,
                                                         @RequestParam("file") MultipartFile file,
                                                         @RequestParam(required = false) String note) {
        return Result.success(datasetService.addVersion(id, file, note,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }

    /** 数据集改名/描述：创建者+老师 */
    @PutMapping("/datasets/{id}")
    public Result<DatasetService.DatasetView> update(@PathVariable Long id,
                                                     @RequestBody DatasetUpdateRequest request) {
        return Result.success(datasetService.update(id, request,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }

    /** 数据集软删：创建者+老师 */
    @DeleteMapping("/datasets/{id}")
    public Result<Void> deleteDataset(@PathVariable Long id) {
        datasetService.deleteDataset(id, SecurityUtils.currentUserId(), SecurityUtils.currentRole());
        return Result.success();
    }

    /** 版本软删（r2-B3）：该版本上传者本人+老师 */
    @DeleteMapping("/dataset-versions/{vid}")
    public Result<Void> deleteVersion(@PathVariable Long vid) {
        datasetService.deleteVersion(vid, SecurityUtils.currentUserId(), SecurityUtils.currentRole());
        return Result.success();
    }

    /** 成员鉴权下载；文件丢失 404 专用文案（不进静态放行链路） */
    @GetMapping("/dataset-versions/{vid}/download")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable Long vid) {
        DatasetService.Download download = datasetService.prepareDownload(vid,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole());
        String encoded = URLEncoder.encode(download.originalFilename(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, download.contentType())
                .body(download.resource());
    }

    /** 预览：CSV 流式截断 / xlsx ≤2 万行流式读，超限"请下载"（r2-B5） */
    @GetMapping("/dataset-versions/{vid}/preview")
    public Result<DatasetService.PreviewView> preview(@PathVariable Long vid) {
        return Result.success(datasetService.preview(vid,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }
}
