package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.common.PageResult;
import com.ltpms.dto.ScheduleImportRow;
import com.ltpms.entity.*;
import com.ltpms.mapper.*;
import com.ltpms.util.ExcelUtil;
import com.ltpms.util.MajorExtractor;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * 课表服务，提供课表分页查询、Excel 导入（含校验与课程生成）、批量删除及模板下载等能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleMapper scheduleMapper;
    private final ScheduleTimeMapper scheduleTimeMapper;
    private final CourseMapper courseMapper;
    private final UserMapper userMapper;
    private final LaboratoryMapper laboratoryMapper;

    /**
     * 分页查询课表列表，可按学期筛选。
     *
     * @param semesterId 学期 ID，可选
     * @param page       页码，从 1 开始
     * @param size       每页大小
     * @return 分页后的课表列表
     */
    public PageResult<Schedule> list(Long semesterId, int page, int size) {
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<>();
        if (semesterId != null) {
            wrapper.eq(Schedule::getSemesterId, semesterId);
        }
        wrapper.orderByDesc(Schedule::getCreatedAt);
        Page<Schedule> result = scheduleMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result);
    }

    /**
     * 从 Excel 导入课表：解析、逐行校验、检查教师冲突，成功后覆盖旧课表并生成课程记录。
     * <p>教师调用时（role=TEACHER）仅导入本人课表：文件行必须为本人（教师工号留空或等于本人账号），
     * 归属教师强制为当前登录教师，且只覆盖该学期本人已有课表，避免误删他人数据。
     *
     * @param semesterId   归属学期 ID
     * @param file         上传的 Excel 文件
     * @param currentUserId 当前登录用户 ID
     * @param role         当前登录用户角色（ADMIN / TEACHER）
     * @return 导入结果，含成功数量、失败数量与错误明细
     * @throws IOException 读取文件失败时抛出
     */
    @Transactional
    public ImportResult importSchedule(Long semesterId, MultipartFile file, Long currentUserId, String role) throws IOException {
        return doImport(semesterId, parseExcel(file), currentUserId, role);
    }

    /**
     * 课表照片识别草稿落库：接收结构化行，复用 Excel 导入的校验与落库逻辑。
     *
     * @param semesterId    归属学期 ID
     * @param rows          结构化课表行（来自 GLM-4V 识别）
     * @param currentUserId 当前登录用户 ID
     * @param role          当前登录用户角色
     * @return 导入结果
     */
    @Transactional
    public ImportResult importFromRows(Long semesterId, List<ScheduleImportRow> rows, Long currentUserId, String role) {
        return doImport(semesterId, rows, currentUserId, role);
    }

    private ImportResult doImport(Long semesterId, List<ScheduleImportRow> rows, Long currentUserId, String role) {
        boolean forceOwn = "TEACHER".equals(role);

        List<RowError> errors = new ArrayList<>();

        // 教师导入：核对归属，强制注入本人 teacherId
        if (forceOwn) {
            User self = currentUserId == null ? null : userMapper.selectById(currentUserId);
            if (self == null) {
                return new ImportResult(0, rows.size(), List.of(new RowError(-1, "无法识别当前登录教师，请重新登录")));
            }
            for (int i = 0; i < rows.size(); i++) {
                ScheduleImportRow row = rows.get(i);
                boolean blankNo = row.getTeacherNo() == null || row.getTeacherNo().isEmpty();
                if (blankNo || currentUserId.equals(row.getTeacherId())) {
                    row.setTeacherNo(self.getUsername());
                    row.setTeacherId(currentUserId);
                } else {
                    return new ImportResult(0, rows.size(), List.of(new RowError(-1,
                            "文件包含非本人课表行（第 " + (i + 2) + " 行教师工号："
                                    + row.getTeacherNo() + "），请仅上传本人授课课表")));
                }
            }
        }

        // 逐行校验
        for (int i = 0; i < rows.size(); i++) {
            ScheduleImportRow row = rows.get(i);
            validateRow(row, i + 2, errors);
        }

        // 按 course_code 分组检查教师冲突
        Map<String, List<ScheduleImportRow>> grouped = rows.stream()
                .filter(r -> r.getTeacherId() != null)
                .collect(Collectors.groupingBy(ScheduleImportRow::getCourseCode));

        for (Map.Entry<String, List<ScheduleImportRow>> entry : grouped.entrySet()) {
            Set<Long> teacherIds = entry.getValue().stream()
                    .map(ScheduleImportRow::getTeacherId)
                    .collect(Collectors.toSet());
            if (teacherIds.size() > 1) {
                errors.add(new RowError(-1, "课程 " + entry.getKey() + " 存在多位授课教师，请人工核实课表"));
            }
        }

        if (!errors.isEmpty()) {
            return new ImportResult(0, errors.size(), errors);
        }

        // 覆盖旧课表：管理员整学期覆盖；教师仅覆盖本人课表
        if (forceOwn) {
            deleteByTeacherAndSemester(semesterId, currentUserId);
        } else {
            deleteBySemester(semesterId);
        }

        // 保存 schedule
        for (ScheduleImportRow row : rows) {
            Schedule schedule = new Schedule();
            schedule.setSemesterId(semesterId);
            schedule.setCourseCode(row.getCourseCode());
            schedule.setCourseName(row.getCourseName());
            schedule.setTotalHours(row.getTotalHours() != null ? BigDecimal.valueOf(row.getTotalHours()) : BigDecimal.ZERO);
            schedule.setTeacherId(row.getTeacherId());
            schedule.setLabCode(row.getLabCode());
            schedule.setClassNames(row.getClassNames());
            schedule.setStudentCount(row.getStudentCount());
            schedule.setRawScheduleText(row.getRawScheduleText());
            scheduleMapper.insert(schedule);
        }

        // 生成 course 记录
        generateCourses(semesterId, grouped);

        return new ImportResult(rows.size(), 0, Collections.emptyList());
    }

    private void validateRow(ScheduleImportRow row, int rowNum, List<RowError> errors) {
        if (row.getCourseCode() == null || row.getCourseCode().isEmpty()) {
            errors.add(new RowError(rowNum, "课程号不能为空"));
        }
        if (row.getCourseName() == null || row.getCourseName().isEmpty()) {
            errors.add(new RowError(rowNum, "课程名不能为空"));
        }
        if (row.getTeacherId() == null) {
            errors.add(new RowError(rowNum, "教师工号不存在：" + row.getTeacherNo()));
        }
        if (row.getLabCode() == null || row.getLabCode().isEmpty()) {
            errors.add(new RowError(rowNum, "实验室编号不能为空"));
        } else if (!labExists(row.getLabCode())) {
            errors.add(new RowError(rowNum, "实验室编号不存在：" + row.getLabCode()));
        }
        if (row.getStudentCount() == null || row.getStudentCount() <= 0) {
            errors.add(new RowError(rowNum, "选课人数必须大于0"));
        }
    }

    private int generateCourses(Long semesterId, Map<String, List<ScheduleImportRow>> grouped) {
        int count = 0;
        for (Map.Entry<String, List<ScheduleImportRow>> entry : grouped.entrySet()) {
            String courseCode = entry.getKey();
            List<ScheduleImportRow> rows = entry.getValue();

            Long existing = courseMapper.selectCount(
                    new LambdaQueryWrapper<Course>()
                            .eq(Course::getCourseCode, courseCode)
                            .eq(Course::getSemesterId, semesterId));
            if (existing > 0) {
                continue; // 存在即跳过
            }

            ScheduleImportRow first = rows.get(0);
            int totalStudentCount = rows.stream().mapToInt(r -> r.getStudentCount() == null ? 0 : r.getStudentCount()).sum();

            Set<String> majors = new LinkedHashSet<>();
            for (ScheduleImportRow row : rows) {
                String major = MajorExtractor.extract(row.getClassNames());
                if (major != null) {
                    majors.addAll(Arrays.asList(major.split(",")));
                }
            }

            Course course = new Course();
            course.setCourseCode(courseCode);
            course.setCourseName(first.getCourseName());
            course.setSemesterId(semesterId);
            course.setTeacherId(first.getTeacherId());
            course.setTotalHours(first.getTotalHours() != null ? BigDecimal.valueOf(first.getTotalHours()) : BigDecimal.ZERO);
            course.setMajor(majors.isEmpty() ? null : String.join(",", majors));
            courseMapper.insert(course);
            count++;
        }
        return count;
    }

    private List<ScheduleImportRow> parseExcel(MultipartFile file) throws IOException {
        List<ScheduleImportRow> rows = new ArrayList<>();
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String teacherNo = ExcelUtil.getStringValue(row.getCell(3));
                Long teacherId = findTeacherId(teacherNo);

                ScheduleImportRow importRow = new ScheduleImportRow();
                importRow.setCourseCode(ExcelUtil.getStringValue(row.getCell(0)));
                importRow.setCourseName(ExcelUtil.getStringValue(row.getCell(1)));
                importRow.setTotalHours(ExcelUtil.getDoubleValue(row.getCell(2)));
                importRow.setTeacherNo(teacherNo);
                importRow.setTeacherId(teacherId);
                importRow.setLabCode(ExcelUtil.getStringValue(row.getCell(4)));
                importRow.setClassNames(ExcelUtil.getStringValue(row.getCell(5)));
                importRow.setStudentCount(ExcelUtil.getIntValue(row.getCell(6)));
                importRow.setRawScheduleText(ExcelUtil.getStringValue(row.getCell(7)));
                rows.add(importRow);
            }
        }
        return rows;
    }

    private Long findTeacherId(String teacherNo) {
        if (teacherNo == null || teacherNo.isEmpty()) return null;
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, teacherNo)
                        .eq(User::getRole, "TEACHER"));
        return user == null ? null : user.getId();
    }

    private boolean labExists(String labCode) {
        return laboratoryMapper.selectCount(
                new LambdaQueryWrapper<Laboratory>().eq(Laboratory::getLabCode, labCode)) > 0;
    }

    private void deleteBySemester(Long semesterId) {
        List<Schedule> schedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<Schedule>().eq(Schedule::getSemesterId, semesterId));
        for (Schedule schedule : schedules) {
            scheduleTimeMapper.delete(
                    new LambdaQueryWrapper<ScheduleTime>().eq(ScheduleTime::getScheduleId, schedule.getId()));
            scheduleMapper.deleteById(schedule.getId());
        }
    }

    /** 删除某学期指定教师的课表记录（教师本人覆盖导入用）。 */
    private void deleteByTeacherAndSemester(Long semesterId, Long teacherId) {
        List<Schedule> schedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<Schedule>()
                        .eq(Schedule::getSemesterId, semesterId)
                        .eq(Schedule::getTeacherId, teacherId));
        for (Schedule schedule : schedules) {
            scheduleTimeMapper.delete(
                    new LambdaQueryWrapper<ScheduleTime>().eq(ScheduleTime::getScheduleId, schedule.getId()));
            scheduleMapper.deleteById(schedule.getId());
        }
    }

    /**
     * 批量删除课表记录，并级联删除其关联的上课时间段数据。
     *
     * @param ids 待删除的课表 ID 列表
     */
    @Transactional
    public void batchDelete(List<Long> ids) {
        for (Long id : ids) {
            scheduleTimeMapper.delete(
                    new LambdaQueryWrapper<ScheduleTime>().eq(ScheduleTime::getScheduleId, id));
            scheduleMapper.deleteById(id);
        }
    }

    /**
     * 生成课表 Excel 导入模板的字节内容。
     *
     * @return 模板文件字节数组
     * @throws IOException 生成模板失败时抛出
     */
    public byte[] downloadTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("课表导入模板");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("课程号");
            header.createCell(1).setCellValue("课程名");
            header.createCell(2).setCellValue("总学时");
            header.createCell(3).setCellValue("教师工号");
            header.createCell(4).setCellValue("实验室编号");
            header.createCell(5).setCellValue("上课班级");
            header.createCell(6).setCellValue("选课人数");
            header.createCell(7).setCellValue("上课时间");

            for (int i = 0; i < 8; i++) {
                sheet.setColumnWidth(i, 18);
            }

            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    /**
     * 课表导入结果，包含成功数量、失败数量与逐行错误明细。
     */
    public record ImportResult(int successCount, int failedCount, List<RowError> errors) {
    }

    /**
     * Excel 单条行导入错误，包含行号与错误信息。
     */
    public record RowError(int rowNum, String message) {
    }
}
