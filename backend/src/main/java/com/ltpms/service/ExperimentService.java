package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.dto.ExperimentRequest;
import com.ltpms.entity.Course;
import com.ltpms.entity.Experiment;
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

        for (Experiment prev : previousExperiments) {
            Experiment experiment = new Experiment();
            experiment.setCourseId(courseId);
            experiment.setExperimentName(prev.getExperimentName());
            experiment.setCategory(prev.getCategory());
            experiment.setType(prev.getType());
            experiment.setSubject(prev.getSubject());
            experiment.setRequirement(prev.getRequirement());
            experiment.setExperimenterType(prev.getExperimenterType());
            experiment.setGroupSize(prev.getGroupSize());
            experiment.setHours(prev.getHours());
            experiment.setSortOrder(prev.getSortOrder());
            experiment.setExperimentCode(codeGenerator.generateExperimentCode(currentCourse));
            experimentMapper.insert(experiment);
        }

        return previousExperiments.size();
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

    private void checkPermission(Course course, Long currentUserId, String role) {
        if ("ADMIN".equals(role)) {
            return;
        }
        if (!currentUserId.equals(course.getTeacherId())) {
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
