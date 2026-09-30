package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.dto.ExperimentRequest;
import com.ltpms.entity.Course;
import com.ltpms.entity.Experiment;
import com.ltpms.entity.LabProject;
import com.ltpms.entity.Semester;
import com.ltpms.mapper.CourseMapper;
import com.ltpms.mapper.ExperimentMapper;
import com.ltpms.mapper.SemesterMapper;
import com.ltpms.util.CodeGenerator;
import com.ltpms.util.ExcelUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 实验项目服务，提供实验项目的查询、增删改、跨学期复制、Excel 导入与模板下载等能力，
 * 并对编辑操作进行权限与学期有效性校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExperimentService {

    private final ExperimentMapper experimentMapper;
    private final CourseMapper courseMapper;
    private final SemesterMapper semesterMapper;
    private final CodeGenerator codeGenerator;
    private final ProjectAuthService projectAuthService;
    private final ProjectGroupService projectGroupService;

    /**
     * 容器实验目录（M-Q2/M-Q8）：读权限单路收口 ProjectAuthService（H2），
     * 目录内容 = 容器课程的 experiment 行（学生只读可见，写仍走 /experiments 教师端点）。
     *
     * @param projectId     容器课题 ID
     * @param currentUserId 当前用户 ID
     * @param role          当前角色
     * @return 该课程实验目录
     */
    public List<Experiment> listByContainer(Long projectId, Long currentUserId, String role) {
        LabProject project = projectAuthService.requireReadable(projectId, currentUserId, role);
        if (project.getCourseId() == null) {
            return new ArrayList<>();
        }
        return experimentMapper.selectList(new LambdaQueryWrapper<Experiment>()
                .eq(Experiment::getCourseId, project.getCourseId())
                .orderByAsc(Experiment::getSortOrder));
    }

    /**
     * 查询实验项目列表，可按课程筛选；教师角色仅返回本人课程的实验项目。
     *
     * @param courseId      课程 ID，为空时不按课程筛选
     * @param currentUserId 当前登录用户 ID
     * @param role          当前用户角色
     * @return 实验项目列表
     */
    public List<Experiment> list(Long courseId, Long currentUserId, String role) {
        LambdaQueryWrapper<Experiment> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            wrapper.eq(Experiment::getCourseId, courseId);
        }
        if ("TEACHER".equals(role)) {
            // 仅查询当前教师课程的实验项目
            List<Long> courseIds = courseMapper.selectList(
                            new LambdaQueryWrapper<Course>()
                                    .eq(Course::getTeacherId, currentUserId))
                    .stream().map(Course::getId).toList();
            if (courseIds.isEmpty()) {
                return new ArrayList<>();
            }
            wrapper.in(Experiment::getCourseId, courseIds);
        }
        wrapper.orderByAsc(Experiment::getSortOrder);
        return experimentMapper.selectList(wrapper);
    }

    /**
     * 新增实验项目，自动生成实验编号并校验权限与学期有效性。
     *
     * @param request       实验项目信息
     * @param currentUserId 当前登录用户 ID
     * @param role          当前用户角色
     * @throws BusinessException 当课程不存在、无权限或非当前学期时抛出
     */
    @Transactional
    public void create(ExperimentRequest request, Long currentUserId, String role) {
        Course course = courseMapper.selectById(request.getCourseId());
        if (course == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        checkPermission(course, currentUserId, role);
        checkCurrentSemester(course.getSemesterId());

        Experiment experiment = toEntity(request);
        experiment.setExperimentCode(codeGenerator.generateExperimentCode(course));
        experimentMapper.insert(experiment);
    }

    /**
     * 更新指定实验项目，并校验权限与学期有效性。
     *
     * @param id            实验项目 ID
     * @param request       实验项目更新信息
     * @param currentUserId 当前登录用户 ID
     * @param role          当前用户角色
     * @throws BusinessException 当实验项目不存在、无权限或非当前学期时抛出
     */
    @Transactional
    public void update(Long id, ExperimentRequest request, Long currentUserId, String role) {
        Experiment experiment = experimentMapper.selectById(id);
        if (experiment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "实验项目不存在");
        }
        Course course = courseMapper.selectById(experiment.getCourseId());
        checkPermission(course, currentUserId, role);
        checkCurrentSemester(course.getSemesterId());

        // 归属课程不可变更（2026-09-20 收口）：权限按"实验当前所属课程"判，若落库用请求体的 courseId，
        // 归属教师可把自己的实验搬进他人课程；且编号前缀绑课程，搬移即失配
        if (!experiment.getCourseId().equals(request.getCourseId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "实验项目归属课程不可变更");
        }

        Experiment update = toEntity(request);
        update.setId(id);
        update.setExperimentCode(experiment.getExperimentCode());
        experimentMapper.updateById(update);
    }

    /**
     * 删除指定实验项目，并校验权限与学期有效性。
     *
     * @param id            实验项目 ID
     * @param currentUserId 当前登录用户 ID
     * @param role          当前用户角色
     * @throws BusinessException 当实验项目不存在、无权限或非当前学期时抛出
     */
    @Transactional
    public void delete(Long id, Long currentUserId, String role) {
        Experiment experiment = experimentMapper.selectById(id);
        if (experiment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "实验项目不存在");
        }
        Course course = courseMapper.selectById(experiment.getCourseId());
        checkPermission(course, currentUserId, role);
        checkCurrentSemester(course.getSemesterId());
        experimentMapper.deleteById(id);
        projectGroupService.onExperimentDeleted(id);
    }

    /**
     * 从上学期同课程号的课程复制实验项目到当前课程。
     *
     * @param courseId      目标课程 ID
     * @param currentUserId 当前登录用户 ID
     * @param role          当前用户角色
     * @return 复制成功的实验项目数量
     * @throws BusinessException 当课程不存在、无上学期实验项目或非当前学期时抛出
     */
    @Transactional
    public int copyFromPrevious(Long courseId, Long currentUserId, String role) {
        Course currentCourse = courseMapper.selectById(courseId);
        if (currentCourse == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        checkPermission(currentCourse, currentUserId, role);
        checkCurrentSemester(currentCourse.getSemesterId());

        // 找到上学期同课程号的课程
        Semester currentSemester = semesterMapper.selectById(currentCourse.getSemesterId());
        Course previousCourse = findPreviousCourse(currentCourse.getCourseCode(), currentSemester);
        if (previousCourse == null) {
            throw new BusinessException(ErrorCode.NO_PREVIOUS_EXPERIMENTS);
        }

        List<Experiment> previousExperiments = experimentMapper.selectList(
                new LambdaQueryWrapper<Experiment>()
                        .eq(Experiment::getCourseId, previousCourse.getId())
                        .orderByAsc(Experiment::getSortOrder));

        if (previousExperiments.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_PREVIOUS_EXPERIMENTS);
        }

        return copyExperiments(previousCourse.getId(), currentCourse.getId());
    }

    /**
     * 「从上学期复制」可行性探针（R3：容器目录 Tab 按钮置灰依据，不落库）。
     * 仅回答"是否有上学期同课程号目录"，动作本身仍沿用 copyFromPrevious 的 43001 兜底。
     * 读侧放行 ADMIN（2026-09-20 权限边界：只读可查，前端亦不渲染按钮）；写侧由 copyFromPrevious 拦截。
     *
     * @param courseId      当前课程 ID
     * @param currentUserId 当前用户 ID
     * @param role          当前角色
     * @return available + 上学期名 + 可复制条数
     */
    public CopyAvailability copyAvailability(Long courseId, Long currentUserId, String role) {
        Course currentCourse = courseMapper.selectById(courseId);
        if (currentCourse == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        // 只读探针（2026-09-20 权限边界）：ADMIN 只读可查；写侧动作仍由 copyFromPrevious 的 checkPermission 拦截
        if (!"ADMIN".equals(role)) {
            checkPermission(currentCourse, currentUserId, role);
        }
        Semester currentSemester = currentCourse.getSemesterId() == null
                ? null : semesterMapper.selectById(currentCourse.getSemesterId());
        if (currentSemester == null) {
            return new CopyAvailability(false, null, 0);
        }
        Course previousCourse = findPreviousCourse(currentCourse.getCourseCode(), currentSemester);
        if (previousCourse == null) {
            return new CopyAvailability(false, null, 0);
        }
        long count = experimentMapper.selectCount(
                new LambdaQueryWrapper<Experiment>().eq(Experiment::getCourseId, previousCourse.getId()));
        Semester previousSemester = semesterMapper.selectById(previousCourse.getSemesterId());
        return new CopyAvailability(count > 0,
                previousSemester == null ? null : previousSemester.getSemesterName(), (int) count);
    }

    /** 上学期复制可行性（预览用，不落库）。 */
    public record CopyAvailability(boolean available, String previousSemesterName, int previousExperimentCount) {
    }

    /**
     * 上学期智能迁移：预览目标学期各课程可从上学期复制的实验项目（不落库）。
     *
     * @param semesterId    目标学期 ID，为空时取当前学期
     * @param currentUserId 当前用户 ID
     * @param role          当前角色
     * @return 迁移草稿列表（每门可迁移课程一条）
     */
    public List<MigrationDraft> previewMigration(Long semesterId, Long currentUserId, String role) {
        Semester target = semesterId != null ? semesterMapper.selectById(semesterId) : getCurrentSemester();
        if (target == null) {
            return new ArrayList<>();
        }

        List<Course> targetCourses = courseMapper.selectList(
                new LambdaQueryWrapper<Course>().eq(Course::getSemesterId, target.getId()));
        if ("TEACHER".equals(role)) {
            targetCourses = targetCourses.stream()
                    .filter(c -> currentUserId.equals(c.getTeacherId()))
                    .collect(java.util.stream.Collectors.toList());
        }

        List<MigrationDraft> drafts = new ArrayList<>();
        for (Course course : targetCourses) {
            Course previousCourse = findPreviousCourse(course.getCourseCode(), target);
            if (previousCourse == null) {
                continue;
            }
            List<Experiment> previousExperiments = experimentMapper.selectList(
                    new LambdaQueryWrapper<Experiment>()
                            .eq(Experiment::getCourseId, previousCourse.getId())
                            .orderByAsc(Experiment::getSortOrder));
            if (previousExperiments.isEmpty()) {
                continue;
            }
            Semester previousSemester = semesterMapper.selectById(previousCourse.getSemesterId());
            drafts.add(new MigrationDraft(
                    course.getId(), course.getCourseCode(), course.getCourseName(),
                    previousCourse.getId(), previousSemester != null ? previousSemester.getSemesterName() : "",
                    previousExperiments.size(),
                    previousExperiments.stream().map(Experiment::getExperimentName).toList()));
        }
        return drafts;
    }

    /**
     * 上学期智能迁移：批量把上学期实验项目复制到目标学期各课程，返回迁移结果。
     *
     * @param semesterId    目标学期 ID，为空时取当前学期
     * @param currentUserId 当前用户 ID
     * @param role          当前角色
     * @return 迁移结果（成功课程数 + 成功实验项目总数）
     */
    @Transactional
    public MigrationResult batchCopyFromPrevious(Long semesterId, Long currentUserId, String role) {
        List<MigrationDraft> drafts = previewMigration(semesterId, currentUserId, role);
        int courseCount = 0;
        int experimentCount = 0;
        for (MigrationDraft draft : drafts) {
            Course course = courseMapper.selectById(draft.courseId());
            if (course == null) {
                continue;
            }
            checkPermission(course, currentUserId, role);
            checkCurrentSemester(course.getSemesterId());
            experimentCount += copyExperiments(draft.previousCourseId(), draft.courseId());
            courseCount++;
        }
        return new MigrationResult(courseCount, experimentCount);
    }

    /** 复制一门课的实验项目到另一门课，返回复制的实验项目数。 */
    private int copyExperiments(Long fromCourseId, Long toCourseId) {
        List<Experiment> previousExperiments = experimentMapper.selectList(
                new LambdaQueryWrapper<Experiment>()
                        .eq(Experiment::getCourseId, fromCourseId)
                        .orderByAsc(Experiment::getSortOrder));
        Course targetCourse = courseMapper.selectById(toCourseId);
        if (previousExperiments.isEmpty() || targetCourse == null) {
            return 0;
        }
        for (Experiment prev : previousExperiments) {
            Experiment experiment = new Experiment();
            experiment.setCourseId(toCourseId);
            experiment.setExperimentName(prev.getExperimentName());
            experiment.setCategory(prev.getCategory());
            experiment.setType(prev.getType());
            experiment.setSubject(prev.getSubject());
            experiment.setRequirement(prev.getRequirement());
            experiment.setExperimenterType(prev.getExperimenterType());
            experiment.setGroupSize(prev.getGroupSize());
            experiment.setHours(prev.getHours());
            experiment.setSortOrder(prev.getSortOrder());
            experiment.setExperimentCode(codeGenerator.generateExperimentCode(targetCourse));
            experimentMapper.insert(experiment);
        }
        return previousExperiments.size();
    }

    /** 上学期迁移草稿（预览用，不落库）。 */
    public record MigrationDraft(
            Long courseId, String courseCode, String courseName,
            Long previousCourseId, String previousSemesterName,
            int previousExperimentCount, List<String> experimentNames) {
    }

    /** 上学期迁移结果（确认落库后返回）。 */
    public record MigrationResult(int courseCount, int experimentCount) {
    }

    private Semester getCurrentSemester() {
        List<Semester> all = semesterMapper.selectList(null);
        LocalDate today = LocalDate.now();
        for (Semester semester : all) {
            if (!today.isBefore(semester.getStartDate()) && !today.isAfter(semester.getEndDate())) {
                return semester;
            }
        }
        return all.isEmpty() ? null : all.get(0);
    }

    private Course findPreviousCourse(String courseCode, Semester currentSemester) {
        // 简单逻辑：找相同课程号、ID小于当前学期的课程
        // 实际应比较学年学期，此处按创建时间/ID降序取第一条
        List<Course> courses = courseMapper.selectList(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getCourseCode, courseCode)
                        .ne(Course::getSemesterId, currentSemester.getId())
                        .orderByDesc(Course::getCreatedAt)
                        .last("LIMIT 1"));
        return courses.isEmpty() ? null : courses.get(0);
    }

    /**
     * 课程级写权限（2026-09-20 权限边界收口）：仅课程归属教师。
     * ADMIN 全局只读——实验目录的新增/编辑/删除/导入/复制/探针一律 403（读侧 list 不受影响）。
     */
    private void checkPermission(Course course, Long currentUserId, String role) {
        if ("ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "管理员为只读视角，实验目录由课程教师维护");
        }
        if (!"TEACHER".equals(role) || !currentUserId.equals(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void checkCurrentSemester(Long semesterId) {
        Semester semester = semesterMapper.selectById(semesterId);
        if (semester == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学期不存在");
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(semester.getStartDate()) || today.isAfter(semester.getEndDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅当前学期可编辑实验项目");
        }
    }

    private Experiment toEntity(ExperimentRequest request) {
        Experiment experiment = new Experiment();
        experiment.setCourseId(request.getCourseId());
        experiment.setExperimentName(request.getExperimentName());
        experiment.setCategory(request.getCategory());
        experiment.setType(request.getType());
        experiment.setSubject(request.getSubject());
        experiment.setRequirement(request.getRequirement());
        experiment.setExperimenterType(request.getExperimenterType());
        experiment.setGroupSize(request.getGroupSize());
        experiment.setHours(request.getHours());
        experiment.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        return experiment;
    }

    /**
     * 从 Excel 文件批量导入实验项目，逐行校验并插入，返回导入结果。
     *
     * @param courseId      归属课程 ID
     * @param file          上传的 Excel 文件
     * @param currentUserId 当前登录用户 ID
     * @param role          当前用户角色
     * @return 导入结果，含成功数量、失败数量与错误明细
     * @throws BusinessException 当课程不存在、无权限或非当前学期时抛出
     * @throws IOException      读取文件失败时抛出
     */
    @Transactional
    public ImportResult importFromExcel(
            Long courseId, MultipartFile file, Long currentUserId, String role) throws IOException {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        checkPermission(course, currentUserId, role);
        checkCurrentSemester(course.getSemesterId());

        List<ExcelRowError> errors = new ArrayList<>();
        int successCount = 0;

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String name = ExcelUtil.getStringValue(row.getCell(0));
                if (!StringUtils.isNotBlank(name)) {
                    errors.add(new ExcelRowError(i + 1, "实验名称不能为空"));
                    continue;
                }

                Experiment experiment = new Experiment();
                experiment.setCourseId(courseId);
                experiment.setExperimentName(name);
                experiment.setCategory(ExcelUtil.getStringValue(row.getCell(1)));
                experiment.setType(ExcelUtil.getStringValue(row.getCell(2)));
                experiment.setSubject(ExcelUtil.getStringValue(row.getCell(3)));
                experiment.setRequirement(ExcelUtil.getStringValue(row.getCell(4)));
                experiment.setExperimenterType(ExcelUtil.getStringValue(row.getCell(5)));
                experiment.setGroupSize(ExcelUtil.getIntValue(row.getCell(6)));
                Double hours = ExcelUtil.getDoubleValue(row.getCell(7));
                experiment.setHours(hours != null ? BigDecimal.valueOf(hours) : BigDecimal.ZERO);
                experiment.setSortOrder(i);
                experiment.setExperimentCode(codeGenerator.generateExperimentCode(course));
                experimentMapper.insert(experiment);
                successCount++;
            }
        }

        return new ImportResult(successCount, errors.size(), errors);
    }

    /**
     * 生成实验项目 Excel 导入模板的字节内容。
     *
     * @return 模板文件字节数组
     * @throws IOException 生成模板失败时抛出
     */
    public byte[] downloadTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("实验项目导入模板");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("实验名称");
            header.createCell(1).setCellValue("实验类别");
            header.createCell(2).setCellValue("实验类型");
            header.createCell(3).setCellValue("所属学科");
            header.createCell(4).setCellValue("实验要求");
            header.createCell(5).setCellValue("实验者类别");
            header.createCell(6).setCellValue("每组人数");
            header.createCell(7).setCellValue("学时数");

            for (int i = 0; i < 8; i++) {
                sheet.setColumnWidth(i, 16);
            }

            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    /**
     * 实验项目导入结果，包含成功数量、失败数量与逐行错误明细。
     */
    public record ImportResult(int successCount, int failedCount, List<ExcelRowError> errors) {
    }

    /**
     * Excel 单条行导入错误，包含行号与错误信息。
     */
    public record ExcelRowError(int rowNum, String message) {
    }
}
