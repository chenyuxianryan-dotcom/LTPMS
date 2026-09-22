package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.entity.Course;
import com.ltpms.entity.KnowledgeChunk;
import com.ltpms.entity.KnowledgeDocument;
import com.ltpms.entity.User;
import com.ltpms.mapper.CourseMapper;
import com.ltpms.mapper.KnowledgeChunkMapper;
import com.ltpms.mapper.KnowledgeDocumentMapper;
import com.ltpms.mapper.UserMapper;
import com.ltpms.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库服务（锁① 知识域）：制度与流程（管理员维护）+ 课程资料（教师自助上传）。
 * 入库链路：落盘 → 解析 → 切分 → 向量化 → 落库；向量化失败即整体失败并清理落盘文件，
 * 不留「有文档无向量」的半成品（检索侧只读 knowledge_chunk，不补齐）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    /** 单文档切分段数上限（控住向量化耗时与单文档检索噪声） */
    public static final int MAX_CHUNKS = 400;

    private static final Set<String> ALLOWED_EXT = Set.of("docx", "pdf", "txt", "md");
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "pdf", "application/pdf",
            "txt", "text/plain;charset=UTF-8",
            "md", "text/markdown;charset=UTF-8");

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final CourseMapper courseMapper;
    private final UserMapper userMapper;
    private final EmbeddingClient embeddingClient;

    @Value("${ltpms.upload.kb-file-dir:./uploads/kb}")
    private String kbFileDir;

    @Value("${ltpms.upload.kb-max-file-size-mb:20}")
    private long maxFileSizeMb;

    /** 文档视图 */
    public record DocumentView(Long id, String title, String category, Long courseId, String courseName,
                               String originalFilename, String fileType, Long fileSize, Integer chunkCount,
                               Long uploaderId, String uploaderName, LocalDateTime createdAt) {
    }

    /** 下载载荷 */
    public record Download(String originalFilename, String contentType, FileSystemResource resource) {
    }

    // ---------------------------------------------------------------- 上传

    /**
     * 上传并入库。制度与流程仅 ADMIN 可写（红线「ADMIN 全局只读」的知识库窄例外，锁① 定稿）；
     * 课程资料仅该课程授课教师可写。
     */
    @Transactional
    public DocumentView upload(String title, String category, Long courseId, MultipartFile file,
                               Long userId, String role) {
        String normalized = normalizeCategory(category);
        Course course = null;
        if (KnowledgeDocument.CATEGORY_POLICY.equals(normalized)) {
            if (!SecurityUtils.ROLE_ADMIN.equals(role)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "「制度与流程」由管理员维护，教师请上传课程资料");
            }
        } else {
            if (!SecurityUtils.ROLE_TEACHER.equals(role)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "课程资料仅授课教师可上传");
            }
            course = requireOwnCourse(courseId, userId);
        }

        StoredFile stored = storeFile(file);
        try {
            List<String> paragraphs = DocumentParser.parse(resolveStoredPath(stored.storedPath()), stored.fileType());
            List<TextChunker.Chunk> chunks = TextChunker.chunk(paragraphs);
            if (chunks.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "文档切分后无有效内容");
            }
            if (chunks.size() > MAX_CHUNKS) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "文档过长（切分后 " + chunks.size() + " 段，上限 " + MAX_CHUNKS + " 段），请拆分后上传");
            }
            List<byte[]> vectors = embeddingClient.embedDocuments(
                    chunks.stream().map(KnowledgeService::embedText).collect(Collectors.toList()));

            KnowledgeDocument document = new KnowledgeDocument();
            document.setTitle(resolveTitle(title, stored.originalFilename()));
            document.setCategory(normalized);
            document.setCourseId(course == null ? null : course.getId());
            document.setOriginalFilename(stored.originalFilename());
            document.setStoredPath(stored.storedPath());
            document.setFileType(stored.fileType());
            document.setFileSize(stored.size());
            document.setChunkCount(chunks.size());
            document.setUploaderId(userId);
            document.setDeleted(0);
            documentMapper.insert(document);

            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocumentId(document.getId());
                chunk.setSeq(i + 1);
                chunk.setHeading(chunks.get(i).heading());
                chunk.setContent(chunks.get(i).content());
                chunk.setEmbedding(vectors.get(i));
                chunkMapper.insert(chunk);
            }
            return toView(document, course, userNames(Set.of(userId)).get(userId));
        } catch (RuntimeException e) {
            deleteFileQuietly(stored.storedPath());
            throw e;
        }
    }

    // ---------------------------------------------------------------- 查询 / 下载 / 删除

    /** 文档列表：制度类全员可见；课程资料教师仅见本人任教课程（ADMIN 全量） */
    public List<DocumentView> list(String category, Long courseId, Long userId, String role) {
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(category)) {
            wrapper.eq(KnowledgeDocument::getCategory, normalizeCategory(category));
        }
        if (courseId != null) {
            wrapper.eq(KnowledgeDocument::getCourseId, courseId);
        }
        if (!SecurityUtils.ROLE_ADMIN.equals(role)) {
            List<Long> ownCourseIds = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                            .eq(Course::getTeacherId, userId))
                    .stream().map(Course::getId).toList();
            wrapper.and(w -> {
                w.eq(KnowledgeDocument::getCategory, KnowledgeDocument.CATEGORY_POLICY);
                if (!ownCourseIds.isEmpty()) {
                    w.or(o -> o.eq(KnowledgeDocument::getCategory, KnowledgeDocument.CATEGORY_COURSE)
                            .in(KnowledgeDocument::getCourseId, ownCourseIds));
                }
            });
        }
        wrapper.orderByDesc(KnowledgeDocument::getId);
        List<KnowledgeDocument> documents = documentMapper.selectList(wrapper);

        Map<Long, String> courseNames = courseNames(documents.stream()
                .map(KnowledgeDocument::getCourseId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<Long, String> names = userNames(documents.stream()
                .map(KnowledgeDocument::getUploaderId).collect(Collectors.toSet()));
        return documents.stream()
                .map(d -> toView(d, null, names.get(d.getUploaderId()), courseNames))
                .collect(Collectors.toList());
    }

    /** 鉴权下载（原名回写由控制器处理） */
    public Download prepareDownload(Long id, Long userId, String role) {
        KnowledgeDocument document = getOrThrow(id);
        requireVisible(document, userId, role);
        Path path = resolveStoredPath(document.getStoredPath());
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件存储丢失，请联系管理员");
        }
        return new Download(document.getOriginalFilename(),
                CONTENT_TYPES.getOrDefault(document.getFileType(), "application/octet-stream"),
                new FileSystemResource(path));
    }

    /** 软删文档 + 物理清理分段（分段无逻辑删除列，留着会继续被检索到） */
    @Transactional
    public void delete(Long id, Long userId, String role) {
        KnowledgeDocument document = getOrThrow(id);
        requireVisible(document, userId, role);
        boolean policy = KnowledgeDocument.CATEGORY_POLICY.equals(document.getCategory());
        if (policy) {
            if (!SecurityUtils.ROLE_ADMIN.equals(role)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "「制度与流程」仅管理员可删除");
            }
        } else {
            Course course = document.getCourseId() == null ? null
                    : courseMapper.selectById(document.getCourseId());
            boolean teacherOfCourse = course != null && userId.equals(course.getTeacherId());
            if (!teacherOfCourse && !document.getUploaderId().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "仅上传者本人与课程授课教师可删除该资料");
            }
        }
        documentMapper.deleteById(id);
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, id));
        deleteFileQuietly(document.getStoredPath());
    }

    // ---------------------------------------------------------------- 内部

    private record StoredFile(String storedPath, String originalFilename, long size, String fileType) {
    }

    private String normalizeCategory(String category) {
        String value = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        if (!KnowledgeDocument.CATEGORY_POLICY.equals(value)
                && !KnowledgeDocument.CATEGORY_COURSE.equals(value)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分类只能是 POLICY（制度与流程）或 COURSE（课程资料）");
        }
        return value;
    }

    /** 课程资料写入边界：课程存在且当前教师是其授课教师 */
    private Course requireOwnCourse(Long courseId, Long userId) {
        if (courseId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择资料归属课程");
        }
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在或已删除");
        }
        if (!userId.equals(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅可上传本人任教课程的资料");
        }
        return course;
    }

    /** 可见性断言：制度类全员；课程资料为 ADMIN 全量、授课教师与上传者本人 */
    private void requireVisible(KnowledgeDocument document, Long userId, String role) {
        if (KnowledgeDocument.CATEGORY_POLICY.equals(document.getCategory())
                || SecurityUtils.ROLE_ADMIN.equals(role)) {
            return;
        }
        Course course = document.getCourseId() == null ? null : courseMapper.selectById(document.getCourseId());
        boolean teacherOfCourse = course != null && userId.equals(course.getTeacherId());
        if (!teacherOfCourse && !userId.equals(document.getUploaderId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该资料");
        }
    }

    /** 大小/后缀白名单 + 魔数校验 + UUID 落盘 */
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
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 docx/pdf/txt/md 格式");
        }
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(4096);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取文件失败");
        }
        if (!detectByMagic(head, ext)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件实际类型与扩展名不符，已拒绝");
        }
        try {
            Path dir = Paths.get(kbFileDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(filename, original, file.getSize(), ext);
        } catch (IOException e) {
            log.error("Save knowledge file failed", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败，请稍后重试");
        }
    }

    private boolean detectByMagic(byte[] head, String ext) {
        if (ext.equals("docx")) {
            return head.length >= 4 && head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4;
        }
        if (ext.equals("pdf")) {
            return head.length >= 4 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F';
        }
        return isUtf8Text(head);
    }

    /** 文件头按 UTF-8 解码试探；endOfInput=false 使「尾部多字节字符被截断」不算错误 */
    private boolean isUtf8Text(byte[] head) {
        for (byte b : head) {
            if (b == 0) {
                return false;
            }
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(java.nio.ByteBuffer.wrap(head),
                    java.nio.CharBuffer.allocate(head.length), false);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private String resolveTitle(String title, String originalFilename) {
        String value = StringUtils.hasText(title) ? title.trim() : stripExtension(originalFilename);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文档标题不能为空");
        }
        return value.length() > 200 ? value.substring(0, 200) : value;
    }

    /** 入库文本 = 标题 + 正文：标题参与向量，保证「标题问句」也能命中 */
    private static String embedText(TextChunker.Chunk chunk) {
        return StringUtils.hasText(chunk.heading())
                ? chunk.heading() + "\n" + chunk.content()
                : chunk.content();
    }

    private KnowledgeDocument getOrThrow(Long id) {
        KnowledgeDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在或已删除");
        }
        return document;
    }

    private Path resolveStoredPath(String storedFilename) {
        Path dir = Paths.get(kbFileDir).toAbsolutePath().normalize();
        Path path = dir.resolve(storedFilename).normalize();
        if (!path.startsWith(dir)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        return path;
    }

    private void deleteFileQuietly(String storedFilename) {
        try {
            Files.deleteIfExists(resolveStoredPath(storedFilename));
        } catch (IOException e) {
            log.warn("Cleanup knowledge file failed: {}", storedFilename, e);
        }
    }

    private DocumentView toView(KnowledgeDocument d, Course course, String uploaderName) {
        return toView(d, course, uploaderName, Map.of());
    }

    private DocumentView toView(KnowledgeDocument d, Course course, String uploaderName,
                                Map<Long, String> courseNames) {
        String courseName = course != null ? course.getCourseName()
                : (d.getCourseId() == null ? null : courseNames.get(d.getCourseId()));
        return new DocumentView(d.getId(), d.getTitle(), d.getCategory(), d.getCourseId(), courseName,
                d.getOriginalFilename(), d.getFileType(), d.getFileSize(), d.getChunkCount(),
                d.getUploaderId(), uploaderName, d.getCreatedAt());
    }

    private Map<Long, String> courseNames(Collection<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        courseMapper.selectBatchIds(courseIds).forEach(c -> names.put(c.getId(), c.getCourseName()));
        return names;
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
