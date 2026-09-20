package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.dto.project.DatasetUpdateRequest;
import com.ltpms.entity.LabDataset;
import com.ltpms.entity.LabDatasetVersion;
import com.ltpms.entity.LabProject;
import com.ltpms.entity.User;
import com.ltpms.mapper.LabDatasetMapper;
import com.ltpms.mapper.LabDatasetVersionMapper;
import com.ltpms.mapper.UserMapper;
import com.ltpms.util.SecurityUtils;
import com.ltpms.util.XlsxStreamPreviewer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 课题数据集服务（本期最小集：建集/版本链/下载/预览，供 S2 交付物挂载与数据 Tab）。
 * 安全基线（r2-B6）：落盘文件名一律服务端 UUID，原名仅存字段；类型按魔数校验不信后缀；
 * 下载 Content-Disposition 编码回原名。预览口径（r2-B5）：CSV 流式截断；
 * xlsx 仅 ≤2 万行流式读，超限提示下载（禁用全量 XSSFWorkbook）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetService {

    /** 预览返回行数 */
    public static final int PREVIEW_ROWS = 200;
    /** xlsx 预览行数硬上限（r2-B5） */
    public static final int XLSX_MAX_ROWS = 20000;

    private static final Set<String> ALLOWED_EXT = Set.of(
            "xlsx", "csv", "txt", "jpg", "jpeg", "png", "webp", "gif", "zip");
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "zip", "application/zip",
            "csv", "text/csv;charset=UTF-8",
            "txt", "text/plain;charset=UTF-8",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "gif", "image/gif");

    private final LabDatasetMapper datasetMapper;
    private final LabDatasetVersionMapper versionMapper;
    private final UserMapper userMapper;
    private final ProjectAuthService authService;

    @Value("${ltpms.upload.lab-file-dir:./uploads/lab}")
    private String labFileDir;

    @Value("${ltpms.upload.lab-max-file-size-mb:50}")
    private long maxFileSizeMb;

    /** 数据集视图 */
    public record DatasetView(Long id, Long projectId, String datasetName, String description,
                              Integer latestVersion, Long createdById, String createdByName,
                              java.time.LocalDateTime createdAt) {
    }

    /** 版本视图 */
    public record VersionView(Long id, Long datasetId, Integer versionNo, String originalFilename,
                              Long fileSize, String fileType, Long uploaderId, String uploaderName,
                              String note, java.time.LocalDateTime createdAt) {
    }

    /** 新建结果：conflict=true 时 existing 为同名既有数据集摘要（r2-C4 前端二选一） */
    public record CreateOutcome(boolean conflict, LabDataset existing, LabDataset created) {
    }

    /** 下载载荷 */
    public record Download(String originalFilename, String contentType, FileSystemResource resource) {
    }

    /** 预览载荷：rows 截断内容；message 为不可预览/超限提示 */
    public record PreviewView(String type, List<List<String>> rows, boolean truncated, String message) {
    }

    // ---------------------------------------------------------------- 查询

    public List<DatasetView> listByProject(Long projectId, Long userId, String role) {
        authService.requireReadable(projectId, userId, role);
        List<LabDataset> datasets = datasetMapper.selectList(
                new LambdaQueryWrapper<LabDataset>()
                        .eq(LabDataset::getProjectId, projectId)
                        .orderByDesc(LabDataset::getId));
        Map<Long, String> names = userNames(datasets.stream()
                .map(LabDataset::getCreatedBy).collect(Collectors.toSet()));
        return datasets.stream()
                .map(d -> new DatasetView(d.getId(), d.getProjectId(), d.getDatasetName(),
                        d.getDescription(), d.getLatestVersion(), d.getCreatedBy(),
                        names.get(d.getCreatedBy()), d.getCreatedAt()))
                .collect(Collectors.toList());
    }

    public List<VersionView> listVersions(Long datasetId, Long userId, String role) {
        LabDataset dataset = getDatasetOrThrow(datasetId);
        authService.requireReadable(dataset.getProjectId(), userId, role);
        List<LabDatasetVersion> versions = versionMapper.selectList(
                new LambdaQueryWrapper<LabDatasetVersion>()
                        .eq(LabDatasetVersion::getDatasetId, datasetId)
                        .orderByDesc(LabDatasetVersion::getVersionNo));
        Map<Long, String> names = userNames(versions.stream()
                .map(LabDatasetVersion::getUploaderId).collect(Collectors.toSet()));
        return versions.stream()
                .map(v -> new VersionView(v.getId(), v.getDatasetId(), v.getVersionNo(),
                        v.getOriginalFilename(), v.getFileSize(), v.getFileType(), v.getUploaderId(),
                        names.get(v.getUploaderId()), v.getNote(), v.getCreatedAt()))
                .collect(Collectors.toList());
    }

    public DatasetView toView(LabDataset dataset) {
        return new DatasetView(dataset.getId(), dataset.getProjectId(), dataset.getDatasetName(),
                dataset.getDescription(), dataset.getLatestVersion(), dataset.getCreatedBy(),
                userNames(java.util.Set.of(dataset.getCreatedBy())).get(dataset.getCreatedBy()),
                dataset.getCreatedAt());
    }

    // ---------------------------------------------------------------- 新建（C4 同名 409）

    /**
     * 新建数据集并落 v1。项目内同名（未删）→ 不静默挂链，返回既有摘要由前端二选一（r2-C4）。
     */
    @Transactional
    public CreateOutcome create(Long projectId, String datasetName, String description,
                                MultipartFile file, Long userId, String role) {
        LabProject project = authService.requireReadable(projectId, userId, role);
        authService.requireActive(project);
        StoredFile stored = storeFile(file);
        String name = StringUtils.hasText(datasetName) ? datasetName.trim()
                : stripExtension(stored.originalFilename());
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "数据集名称不能为空");
        }
        LabDataset sameName = datasetMapper.selectOne(
                new LambdaQueryWrapper<LabDataset>()
                        .eq(LabDataset::getProjectId, projectId)
                        .eq(LabDataset::getDatasetName, name)
                        .last("LIMIT 1"));
        if (sameName != null) {
            deleteQuietly(stored.storedPath());
            return new CreateOutcome(true, sameName, null);
        }
        LabDataset dataset = new LabDataset();
        dataset.setProjectId(projectId);
        dataset.setDatasetName(name);
        dataset.setDescription(description);
        dataset.setLatestVersion(1);
        dataset.setCreatedBy(userId);
        dataset.setDeleted(0);
        datasetMapper.insert(dataset);
        insertVersion(dataset.getId(), 1, stored, userId, null);
        return new CreateOutcome(false, null, dataset);
    }

    /** 上传新版本（全员可传，版本链只增不覆盖） */
    @Transactional
    public VersionView addVersion(Long datasetId, MultipartFile file, String note,
                                  Long userId, String role) {
        LabDataset dataset = getDatasetOrThrow(datasetId);
        LabProject project = authService.requireReadable(dataset.getProjectId(), userId, role);
        authService.requireActive(project);
        StoredFile stored = storeFile(file);
        int nextVersion = dataset.getLatestVersion() + 1;
        LabDatasetVersion version = insertVersion(datasetId, nextVersion, stored, userId, note);
        LabDataset update = new LabDataset();
        update.setId(datasetId);
        update.setLatestVersion(nextVersion);
        datasetMapper.updateById(update);
        return toVersionView(version, userNames(Set.of(userId)).get(userId));
    }

    // ---------------------------------------------------------------- 下载 / 预览

    /** 成员鉴权下载；Content-Disposition 编码回原名由控制器处理 */
    public Download prepareDownload(Long versionId, Long userId, String role) {
        LabDatasetVersion version = getVersionOrThrow(versionId);
        LabDataset dataset = getDatasetOrThrow(version.getDatasetId());
        authService.requireReadable(dataset.getProjectId(), userId, role);
        Path dir = Paths.get(labFileDir).toAbsolutePath().normalize();
        Path path = dir.resolve(version.getStoredPath()).normalize();
        if (!path.startsWith(dir) || !Files.isRegularFile(path)) {
            // 文件在盘上丢失（运维级）→ 404 专用文案，不 500
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件存储丢失，请联系管理员");
        }
        return new Download(version.getOriginalFilename(),
                CONTENT_TYPES.getOrDefault(version.getFileType(), "application/octet-stream"),
                new FileSystemResource(path));
    }

    /** 只读预览（r2-B5 口径） */
    public PreviewView preview(Long versionId, Long userId, String role) {
        LabDatasetVersion version = getVersionOrThrow(versionId);
        LabDataset dataset = getDatasetOrThrow(version.getDatasetId());
        authService.requireReadable(dataset.getProjectId(), userId, role);
        Path path = resolveStoredPath(version);
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件存储丢失，请联系管理员");
        }
        switch (version.getFileType()) {
            case "csv":
            case "txt":
                return previewText(version, path);
            case "xlsx":
                return previewXlsx(path);
            default:
                return new PreviewView(version.getFileType(), List.of(), false,
                        "该文件类型不支持在线预览，请下载后查看");
        }
    }

    // ---------------------------------------------------------------- 改名 / 软删（Q5 + B3）

    /** 数据集改名/描述：创建者本人 + 归属老师/ADMIN */
    @Transactional
    public DatasetView update(Long datasetId, DatasetUpdateRequest request, Long userId, String role) {
        LabDataset dataset = getDatasetOrThrow(datasetId);
        requireManager(dataset, userId, role);
        authService.requireActive(authService.getProjectOrThrow(dataset.getProjectId()));
        if (StringUtils.hasText(request.getDatasetName())) {
            dataset.setDatasetName(request.getDatasetName().trim());
        }
        dataset.setDescription(request.getDescription());
        LabDataset update = new LabDataset();
        update.setId(datasetId);
        update.setDatasetName(dataset.getDatasetName());
        update.setDescription(dataset.getDescription());
        datasetMapper.updateById(update);
        return new DatasetView(dataset.getId(), dataset.getProjectId(), dataset.getDatasetName(),
                dataset.getDescription(), dataset.getLatestVersion(), dataset.getCreatedBy(), null,
                dataset.getCreatedAt());
    }

    /** 数据集软删：创建者本人 + 归属老师/ADMIN */
    @Transactional
    public void deleteDataset(Long datasetId, Long userId, String role) {
        LabDataset dataset = getDatasetOrThrow(datasetId);
        requireManager(dataset, userId, role);
        authService.requireActive(authService.getProjectOrThrow(dataset.getProjectId()));
        datasetMapper.deleteById(datasetId);
    }

    /** 版本软删（r2-B3）：该版本上传者本人 + 归属老师/ADMIN；数据集与其余版本不动 */
    @Transactional
    public void deleteVersion(Long versionId, Long userId, String role) {
        LabDatasetVersion version = getVersionOrThrow(versionId);
        LabDataset dataset = getDatasetOrThrow(version.getDatasetId());
        LabProject project = authService.getProjectOrThrow(dataset.getProjectId());
        authService.requireActive(project);
        boolean privileged = SecurityUtils.ROLE_ADMIN.equals(role)
                || (SecurityUtils.ROLE_TEACHER.equals(role) && project.getTeacherId().equals(userId));
        if (!privileged && !version.getUploaderId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        versionMapper.deleteById(versionId);
    }

    // ---------------------------------------------------------------- 内部

    private record StoredFile(String storedPath, String originalFilename, long size, String fileType) {
    }

    /** 大小/后缀白名单 + 魔数校验 + UUID 落盘（r2-B6） */
    private StoredFile storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择文件");
        }
        if (file.getSize() > maxFileSizeMb * 1024 * 1024) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件大小超过 " + maxFileSizeMb + "MB 上限");
        }
        String original = StringUtils.getFilename(file.getOriginalFilename());
        if (original == null || !original.contains(".")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件缺少扩展名");
        }
        String ext = original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "仅支持 xlsx/csv/txt/图片/zip 格式");
        }
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(16);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取文件失败");
        }
        String detected = detectByMagic(head, ext);
        if (!detected.equals(ext)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件实际类型与扩展名不符，已拒绝");
        }
        try {
            Path dir = Paths.get(labFileDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String filename = UUID.randomUUID().toString().replace("-", "") + "." + detected;
            Path target = dir.resolve(filename);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(filename, original, file.getSize(), detected);
        } catch (IOException e) {
            log.error("Save lab file failed", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败，请稍后重试");
        }
    }

    /** 按文件头魔数识别类型；csv/txt 以“可解码 UTF-8 文本”校验，不信后缀 */
    private String detectByMagic(byte[] head, String declaredExt) {
        if (head.length >= 4 && head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4) {
            return declaredExt.equals("xlsx") ? "xlsx" : "zip";
        }
        if (head.length >= 8 && head[0] == (byte) 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return "png";
        }
        if (head.length >= 3 && head[0] == (byte) 0xFF && head[1] == (byte) 0xD8 && head[2] == (byte) 0xFF) {
            return "jpg";
        }
        if (head.length >= 6 && head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8') {
            return "gif";
        }
        if (head.length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "webp";
        }
        if (declaredExt.equals("csv") || declaredExt.equals("txt")) {
            if (isUtf8Text(head)) {
                return declaredExt;
            }
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件内容非 UTF-8 文本，已拒绝");
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "文件魔数与声明类型不符，已拒绝");
    }

    private boolean isUtf8Text(byte[] head) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(java.nio.ByteBuffer.wrap(head));
        } catch (Exception e) {
            return false;
        }
        for (byte b : head) {
            if (b == 0) {
                return false;
            }
        }
        return true;
    }

    private PreviewView previewText(LabDatasetVersion version, Path path) {
        List<List<String>> rows = new ArrayList<>();
        boolean truncated = false;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (count >= PREVIEW_ROWS) {
                    truncated = true;
                    break;
                }
                rows.add(splitCsvLine(line));
                count++;
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "预览读取失败");
        }
        return new PreviewView(version.getFileType(), rows, truncated, null);
    }

    private PreviewView previewXlsx(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            XlsxStreamPreviewer.Preview result =
                    XlsxStreamPreviewer.preview(in, PREVIEW_ROWS, XLSX_MAX_ROWS);
            if (result.rowLimitExceeded()) {
                return new PreviewView("xlsx", List.of(), false,
                        "超过 " + XLSX_MAX_ROWS + " 行预览上限，请下载后查看");
            }
            return new PreviewView("xlsx", result.rows(), result.truncated(), null);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "预览读取失败");
        }
    }

    /** 轻量 CSV 行拆分（支持引号包裹逗号） */
    private List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        cells.add(cur.toString());
        return cells;
    }

    private LabDatasetVersion insertVersion(Long datasetId, int versionNo, StoredFile stored,
                                            Long userId, String note) {
        LabDatasetVersion version = new LabDatasetVersion();
        version.setDatasetId(datasetId);
        version.setVersionNo(versionNo);
        version.setStoredPath(stored.storedPath());
        version.setOriginalFilename(stored.originalFilename());
        version.setFileSize(stored.size());
        version.setFileType(stored.fileType());
        version.setUploaderId(userId);
        version.setNote(note);
        version.setDeleted(0);
        versionMapper.insert(version);
        return version;
    }

    private VersionView toVersionView(LabDatasetVersion v, String uploaderName) {
        return new VersionView(v.getId(), v.getDatasetId(), v.getVersionNo(), v.getOriginalFilename(),
                v.getFileSize(), v.getFileType(), v.getUploaderId(), uploaderName, v.getNote(),
                v.getCreatedAt());
    }

    private void requireManager(LabDataset dataset, Long userId, String role) {
        LabProject project = authService.getProjectOrThrow(dataset.getProjectId());
        boolean privileged = SecurityUtils.ROLE_ADMIN.equals(role)
                || (SecurityUtils.ROLE_TEACHER.equals(role) && project.getTeacherId().equals(userId));
        if (!privileged && !dataset.getCreatedBy().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅创建者本人与课题老师可操作数据集");
        }
    }

    private LabDataset getDatasetOrThrow(Long id) {
        LabDataset dataset = datasetMapper.selectById(id);
        if (dataset == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在或已删除");
        }
        return dataset;
    }

    private LabDatasetVersion getVersionOrThrow(Long id) {
        LabDatasetVersion version = versionMapper.selectById(id);
        if (version == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "版本不存在或已删除");
        }
        return version;
    }

    private Path resolveStoredPath(LabDatasetVersion version) {
        Path dir = Paths.get(labFileDir).toAbsolutePath().normalize();
        Path path = dir.resolve(version.getStoredPath()).normalize();
        if (!path.startsWith(dir)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        return path;
    }

    private void deleteQuietly(String storedFilename) {
        try {
            Files.deleteIfExists(Paths.get(labFileDir).toAbsolutePath().normalize().resolve(storedFilename));
        } catch (IOException e) {
            log.warn("Cleanup orphan lab file failed: {}", storedFilename, e);
        }
    }

    private Map<Long, String> userNames(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        userMapper.selectBatchIds(userIds).forEach(u -> names.put(u.getId(),
                StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername()));
        return names;
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
