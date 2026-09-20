package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.common.PageResult;
import com.ltpms.dto.ScheduleEditRequest;
import com.ltpms.dto.ScheduleImportRow;
import com.ltpms.entity.*;
import com.ltpms.mapper.*;
import com.ltpms.util.ExcelUtil;
import com.ltpms.util.MajorExtractor;
import com.ltpms.util.ScheduleTimeParser;
import com.ltpms.util.WeekMath;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 课表服务，提供课表分页查询、教师课表导入（含校验、冲突检查与课程生成）、
 * 行编辑与修订留痕、周视图查询、上传进度统计及模板下载等能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private static final Map<String, String> FIELD_LABELS = Map.of(
            "labCode", "上课地点",
            "classNames", "上课班级",
            "studentCount", "选课人数",
            "rawScheduleText", "上课时间");

    private final ScheduleMapper scheduleMapper;
    private final ScheduleTimeMapper scheduleTimeMapper;
    private final CourseMapper courseMapper;
    private final UserMapper userMapper;
    private final LaboratoryMapper laboratoryMapper;
    private final SemesterMapper semesterMapper;
    private final RevisionService revisionService;
    private final NotificationService notificationService;

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
        enrichTeacherNames(result.getRecords());
        return PageResult.of(result);
    }

    /** 列表响应补充授课教师姓名（实体无此表字段，前端列展示用） */
    private void enrichTeacherNames(List<Schedule> records) {
        Set<Long> teacherIds = records.stream()
                .map(Schedule::getTeacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (teacherIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = new HashMap<>();
        for (User user : userMapper.selectBatchIds(teacherIds)) {
            nameMap.put(user.getId(), user.getRealName() != null ? user.getRealName() : user.getUsername());
        }
        for (Schedule record : records) {
            if (record.getTeacherId() != null) {
                record.setTeacherName(nameMap.get(record.getTeacherId()));
            }
        }
    }

    /**
     * 从 Excel 导入课表（仅教师端）：解析、逐行校验、检查修订冲突，
     * 成功后覆盖该学期本人旧课表、内联生成 schedule_time 并生成课程记录。
     * <p>文件行必须为本人（教师工号留空或等于本人账号），归属教师强制为当前登录教师。
     *
     * @param semesterId      归属学期 ID
     * @param file            上传的 Excel 文件
     * @param currentUserId   当前登录用户 ID
     * @param confirmOverwrite 是否确认覆盖含修订留痕的旧记录
     * @return 导入结果，含成功数量、失败数量与错误明细
     * @throws IOException 读取文件失败时抛出
     */
    @Transactional
    public ImportResult importSchedule(Long semesterId, MultipartFile file, Long currentUserId, boolean confirmOverwrite) throws IOException {
        return doImport(semesterId, parseExcel(file), currentUserId, confirmOverwrite);
    }

    /**
     * 课表照片识别草稿落库：接收结构化行，复用 Excel 导入的校验与落库逻辑。
     *
     * @param semesterId       归属学期 ID
     * @param rows             结构化课表行（来自照片识别草稿）
     * @param currentUserId    当前登录用户 ID
     * @param confirmOverwrite 是否确认覆盖含修订留痕的旧记录
     * @return 导入结果
     */
    @Transactional
    public ImportResult importFromRows(Long semesterId, List<ScheduleImportRow> rows, Long currentUserId, boolean confirmOverwrite) {
        return doImport(semesterId, rows, currentUserId, confirmOverwrite);
    }

    /**
     * 导入解析预览（不落库）：复用与正式导入一致的校验口径，返回有效行数、
     * 逐行错误及将被覆盖的已修订记录数量，供前端在导入前提示确认。
     *
     * @param semesterId    归属学期 ID
     * @param file          上传的 Excel 文件
     * @param currentUserId 当前登录用户 ID
     * @return 解析预览
     * @throws IOException 读取文件失败时抛出
     */
    public ImportPreview previewImport(Long semesterId, MultipartFile file, Long currentUserId) throws IOException {
        return buildPreview(semesterId, parseExcel(file), currentUserId);
    }

    /**
     * 课表照片识别草稿的解析预览（不落库）。
     *
     * @param semesterId    归属学期 ID
     * @param rows          结构化课表行
     * @param currentUserId 当前登录用户 ID
     * @return 解析预览
     */
    public ImportPreview previewRows(Long semesterId, List<ScheduleImportRow> rows, Long currentUserId) {
        return buildPreview(semesterId, rows, currentUserId);
    }

    private ImportResult doImport(Long semesterId, List<ScheduleImportRow> rows, Long currentUserId, boolean confirmOverwrite) {
        List<RowError> errors = new ArrayList<>();

        User self = currentUserId == null ? null : userMapper.selectById(currentUserId);
        if (self == null) {
            return new ImportResult(0, rows.size(), List.of(new RowError(-1, "无法识别当前登录教师，请重新登录")));
        }
        if (!injectSelfOwnership(rows, self, errors)) {
            return new ImportResult(0, rows.size(), errors);
        }

        for (int i = 0; i < rows.size(); i++) {
            validateRow(rows.get(i), i + 2, errors);
        }
        checkCourseTeacherConflicts(rows, errors);
        if (!errors.isEmpty()) {
            return new ImportResult(0, errors.size(), errors);
        }

        // 覆盖旧课表前：检查本人本学期已有记录中是否含修订留痕
        List<Schedule> existing = listByTeacher(semesterId, currentUserId);
        Map<Long, Long> revisionCounts = revisionService.counts(
                RevisionService.TARGET_SCHEDULE,
                existing.stream().map(Schedule::getId).collect(Collectors.toList()));
        if (!revisionCounts.isEmpty() && !confirmOverwrite) {
            throw new BusinessException(ErrorCode.SCHEDULE_REVISION_CONFLICT,
                    "本学期已有 " + revisionCounts.size() + " 条课表记录含修订留痕，重新导入将覆盖这些修订，请确认后再试");
        }

        deleteByTeacherAndSemester(semesterId, currentUserId);

        // 保存 schedule 并内联生成结构化时间槽
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
            replaceScheduleTime(schedule.getId(), row.getRawScheduleText());
        }

        // 生成 course 记录
        generateCourses(semesterId, groupByCourse(rows));

        // 确认覆盖了含修订留痕的记录：通知全体管理员知悉
        if (!revisionCounts.isEmpty()) {
            List<String> courses = existing.stream()
                    .filter(s -> revisionCounts.containsKey(s.getId()))
                    .map(Schedule::getCourseName)
                    .distinct()
                    .limit(5)
                    .collect(Collectors.toList());
            notificationService.createRevisionOverwritten(displayName(self), revisionCounts.size(), String.join("、", courses));
        }

        return new ImportResult(rows.size(), 0, Collections.emptyList());
    }

    private ImportPreview buildPreview(Long semesterId, List<ScheduleImportRow> rows, Long currentUserId) {
        List<RowError> errors = new ArrayList<>();
        User self = currentUserId == null ? null : userMapper.selectById(currentUserId);
        boolean ownershipOk = false;
        if (self == null) {
            errors.add(new RowError(-1, "无法识别当前登录教师，请重新登录"));
        } else {
            ownershipOk = injectSelfOwnership(rows, self, errors);
        }
        if (ownershipOk) {
            for (int i = 0; i < rows.size(); i++) {
                validateRow(rows.get(i), i + 2, errors);
            }
            checkCourseTeacherConflicts(rows, errors);
        }

        Set<Integer> errorRows = errors.stream()
                .map(RowError::rowNum)
                .filter(num -> num > 0)
                .collect(Collectors.toSet());
        int validCount = Math.max(0, rows.size() - errorRows.size());

        List<Schedule> existing = listByTeacher(semesterId, currentUserId);
        Map<Long, Long> revisionCounts = revisionService.counts(
                RevisionService.TARGET_SCHEDULE,
                existing.stream().map(Schedule::getId).collect(Collectors.toList()));
        List<String> conflictCourses = existing.stream()
                .filter(s -> revisionCounts.containsKey(s.getId()))
                .map(Schedule::getCourseName)
                .distinct()
                .limit(10)
                .collect(Collectors.toList());

        return new ImportPreview(rows.size(), validCount, errors, revisionCounts.size(), conflictCourses);
    }

    /**
     * 教师归属核对：教师工号留空、等于本人账号或 teacherId 已解析为本人时强制注入本人 teacherId；
     * 发现他人课表行时记录错误并返回 false。
     */
    private boolean injectSelfOwnership(List<ScheduleImportRow> rows, User self, List<RowError> errors) {
        for (int i = 0; i < rows.size(); i++) {
            ScheduleImportRow row = rows.get(i);
            boolean blankNo = row.getTeacherNo() == null || row.getTeacherNo().isEmpty();
            boolean own = blankNo
                    || self.getId().equals(row.getTeacherId())
                    || self.getUsername().equals(row.getTeacherNo());
            if (own) {
                row.setTeacherNo(self.getUsername());
                row.setTeacherId(self.getId());
            } else {
                errors.add(new RowError(-1, "文件包含非本人课表行（第 " + (i + 2) + " 行教师工号："
                        + row.getTeacherNo() + "），请仅上传本人授课课表"));
                return false;
            }
        }
        return true;
    }

    private void checkCourseTeacherConflicts(List<ScheduleImportRow> rows, List<RowError> errors) {
        Map<String, List<ScheduleImportRow>> grouped = groupByCourse(rows);
        for (Map.Entry<String, List<ScheduleImportRow>> entry : grouped.entrySet()) {
            Set<Long> teacherIds = entry.getValue().stream()
                    .map(ScheduleImportRow::getTeacherId)
                    .collect(Collectors.toSet());
            if (teacherIds.size() > 1) {
                errors.add(new RowError(-1, "课程 " + entry.getKey() + " 存在多位授课教师，请人工核实课表"));
            }
        }
    }

    private Map<String, List<ScheduleImportRow>> groupByCourse(List<ScheduleImportRow> rows) {
        return rows.stream()
                .filter(r -> r.getTeacherId() != null)
                .collect(Collectors.groupingBy(ScheduleImportRow::getCourseCode));
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

    /**
     * 查询某教师某学期的课表（周课表/卡片墙数据源）。
     * <p>教师固定查本人；管理员必须指定 teacherId 以查看任一教师。
     *
     * @param semesterId      学期 ID
     * @param week            周次过滤，可选；传入时仅返回该周有课的项目
     * @param currentUserId   当前登录用户 ID
     * @param role            当前登录用户角色
     * @param queryTeacherId  管理员指定查看的教师 ID
     * @return 教师课表项列表（含时间槽、修订标记与实验室名称）
     */
    public List<MyScheduleItem> mine(Long semesterId, Integer week, Long currentUserId, String role, Long queryTeacherId) {
        Long targetTeacherId;
        if ("ADMIN".equals(role)) {
            if (queryTeacherId == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "管理员查询需指定教师");
            }
            targetTeacherId = queryTeacherId;
        } else {
            targetTeacherId = currentUserId;
        }

        List<Schedule> schedules = listByTeacher(semesterId, targetTeacherId);
        if (schedules.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> scheduleIds = schedules.stream().map(Schedule::getId).collect(Collectors.toList());
        Map<Long, List<ScheduleTime>> timesBySchedule = scheduleTimeMapper.selectList(
                        new LambdaQueryWrapper<ScheduleTime>().in(ScheduleTime::getScheduleId, scheduleIds))
                .stream()
                .collect(Collectors.groupingBy(ScheduleTime::getScheduleId));

        Map<String, String> labNames = laboratoryMapper.selectList(
                        new LambdaQueryWrapper<Laboratory>().in(Laboratory::getLabCode, schedules.stream()
                                .map(Schedule::getLabCode).filter(Objects::nonNull).collect(Collectors.toSet())))
                .stream()
                .collect(Collectors.toMap(Laboratory::getLabCode, Laboratory::getLabName, (a, b) -> a));

        Map<Long, Long> revisionCounts = revisionService.counts(RevisionService.TARGET_SCHEDULE, scheduleIds);

        User teacher = userMapper.selectById(targetTeacherId);
        String teacherName = teacher == null ? null : displayName(teacher);

        List<MyScheduleItem> items = new ArrayList<>();
        for (Schedule schedule : schedules) {
            List<SlotView> slots = buildSlotViews(schedule, timesBySchedule.getOrDefault(schedule.getId(), Collections.emptyList()), week);
            if (week != null && slots.isEmpty()) {
                continue;
            }
            items.add(new MyScheduleItem(
                    schedule.getId(), schedule.getCourseCode(), schedule.getCourseName(), schedule.getTotalHours(),
                    schedule.getTeacherId(), teacherName, schedule.getLabCode(), labNames.get(schedule.getLabCode()),
                    schedule.getClassNames(), schedule.getStudentCount(), schedule.getRawScheduleText(),
                    revisionCounts.getOrDefault(schedule.getId(), 0L), slots, schedule.getCreatedAt()));
        }
        return items;
    }

    /**
     * 构建课表行的时间槽视图：优先取 schedule_time 结构化数据，
     * 缺失时回退解析 raw_schedule_text（不落库）。
     */
    private List<SlotView> buildSlotViews(Schedule schedule, List<ScheduleTime> times, Integer week) {
        List<SlotView> views = new ArrayList<>();
        if (!times.isEmpty()) {
            for (ScheduleTime time : times) {
                Set<Integer> weeks = WeekMath.parseWeeks(time.getWeekRange(), time.getWeekType());
                if (week != null && !weeks.contains(week)) {
                    continue;
                }
                views.add(new SlotView(time.getDayOfWeek(), time.getStartSection(), time.getEndSection(),
                        time.getWeekRange(), time.getWeekType(), new ArrayList<>(weeks)));
            }
            return views;
        }
        String raw = schedule.getRawScheduleText();
        if (raw == null || raw.isBlank()) {
            return views;
        }
        Set<Integer> weeks = WeekMath.parseWeeksFromRawText(raw);
        if (weeks.isEmpty()) {
            String sniffedType = raw.contains("单周") ? "ODD" : raw.contains("双周") ? "EVEN" : null;
            weeks = WeekMath.parseWeeks("1-16", sniffedType);
        }
        if (week != null && !weeks.contains(week)) {
            return views;
        }
        ScheduleTimeParser.Slot slot = ScheduleTimeParser.parse(raw);
        if (slot == null) {
            return views;
        }
        views.add(new SlotView(slot.dayOfWeek(), slot.startSection(), slot.endSection(),
                slot.weekRange(), slot.weekType(), new ArrayList<>(weeks)));
        return views;
    }

    /**
     * 单条新增课表记录（不走整表导入）：教师强制归属本人、管理员可指定教师，
     * 校验口径与导入行一致，并内联解析时间槽、按需生成课程。
     *
     * @param semesterId    归属学期 ID
     * @param row           课表单行（课程号/课程名/实验室/班级/人数/上课时间）
     * @param currentUserId 当前登录用户 ID
     * @param role          当前登录用户角色
     * @return 新增的课表记录
     */
    @Transactional
    public Schedule createOne(Long semesterId, ScheduleImportRow row, Long currentUserId, String role) {
        if (semesterId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请指定学期");
        }
        if (row == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少课程信息");
        }
        if ("TEACHER".equals(role)) {
            User self = userMapper.selectById(currentUserId);
            if (self == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无法识别当前登录教师，请重新登录");
            }
            row.setTeacherId(self.getId());
            row.setTeacherNo(self.getUsername());
        } else {
            if (row.getTeacherId() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择授课教师");
            }
            User teacher = userMapper.selectById(row.getTeacherId());
            if (teacher == null || !"TEACHER".equals(teacher.getRole())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "授课教师不存在或不是教师账号");
            }
            row.setTeacherNo(teacher.getUsername());
        }

        List<RowError> errors = new ArrayList<>();
        validateRow(row, 1, errors);
        if (ScheduleTimeParser.parse(row.getRawScheduleText()) == null) {
            errors.add(new RowError(1, "上课时间无法识别，请按「周一第1-2节(1-16周)」格式填写"));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, errors.get(0).message());
        }

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
        replaceScheduleTime(schedule.getId(), row.getRawScheduleText());
        generateCourses(semesterId, groupByCourse(List.of(row)));
        return schedule;
    }

    /**
     * 单条删除课表记录（教师限本人、管理员不限），级联删除其结构化时间槽。
     *
     * @param id            课表记录 ID
     * @param currentUserId 当前登录用户 ID
     * @param role          当前登录用户角色
     */
    @Transactional
    public void deleteOne(Long id, Long currentUserId, String role) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课表记录不存在");
        }
        if ("TEACHER".equals(role) && !schedule.getTeacherId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除本人的课表记录");
        }
        scheduleTimeMapper.delete(new LambdaQueryWrapper<ScheduleTime>().eq(ScheduleTime::getScheduleId, id));
        scheduleMapper.deleteById(id);
    }

    /**
     * 编辑课表行（教师限本人、管理员不限）：字段级 diff 落修订留痕，
     * 上课时间变更时重建结构化时间槽；管理员修订会通知对应教师。
     *
     * @param id            课表记录 ID
     * @param request       编辑请求，字段为 null 表示不修改
     * @param currentUserId 当前登录用户 ID
     * @param role          当前登录用户角色
     */
    @Transactional
    public void updateSchedule(Long id, ScheduleEditRequest request, Long currentUserId, String role) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课表记录不存在");
        }
        if ("TEACHER".equals(role) && !schedule.getTeacherId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能修改本人的课表记录");
        }

        Map<String, String[]> changes = new LinkedHashMap<>();
        Schedule update = new Schedule();
        update.setId(id);
        boolean rawChanged = false;

        if (request.getLabCode() != null && !request.getLabCode().equals(schedule.getLabCode())) {
            if (request.getLabCode().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "实验室编号不能为空");
            }
            if (!labExists(request.getLabCode())) {
                throw new BusinessException(ErrorCode.SCHEDULE_LAB_NOT_FOUND, "实验室编号不存在：" + request.getLabCode());
            }
            changes.put("labCode", new String[]{schedule.getLabCode(), request.getLabCode()});
            update.setLabCode(request.getLabCode());
        }
        if (request.getClassNames() != null && !request.getClassNames().equals(schedule.getClassNames())) {
            changes.put("classNames", new String[]{schedule.getClassNames(), request.getClassNames()});
            update.setClassNames(request.getClassNames());
        }
        if (request.getStudentCount() != null && !request.getStudentCount().equals(schedule.getStudentCount())) {
            if (request.getStudentCount() <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "选课人数必须大于0");
            }
            changes.put("studentCount", new String[]{String.valueOf(schedule.getStudentCount()), String.valueOf(request.getStudentCount())});
            update.setStudentCount(request.getStudentCount());
        }
        if (request.getRawScheduleText() != null && !request.getRawScheduleText().equals(schedule.getRawScheduleText())) {
            if (request.getRawScheduleText().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "上课时间不能为空");
            }
            changes.put("rawScheduleText", new String[]{schedule.getRawScheduleText(), request.getRawScheduleText()});
            update.setRawScheduleText(request.getRawScheduleText());
            rawChanged = true;
        }

        if (changes.isEmpty()) {
            return;
        }

        scheduleMapper.updateById(update);
        if (rawChanged) {
            replaceScheduleTime(id, request.getRawScheduleText());
        }
        revisionService.record(RevisionService.TARGET_SCHEDULE, id, changes, role, currentUserId);

        if ("ADMIN".equals(role)) {
            notificationService.createScheduleRevised(schedule.getTeacherId(), summarizeChanges(changes));
        }
    }

    /**
     * 查询某课表行的修订明细（教师限本人、管理员不限）。
     *
     * @param id            课表记录 ID
     * @param currentUserId 当前登录用户 ID
     * @param role          当前登录用户角色
     * @return 修订明细列表（按时间升序）
     */
    public List<RevisionService.RevisionView> listRevisions(Long id, Long currentUserId, String role) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课表记录不存在");
        }
        if ("TEACHER".equals(role) && !schedule.getTeacherId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能查看本人课表的修订记录");
        }
        return revisionService.list(RevisionService.TARGET_SCHEDULE, id);
    }

    /**
     * 批量查询课表行修订条数（行级笔形标记数据源）；教师仅能查询本人记录。
     *
     * @param ids           课表记录 ID 列表
     * @param currentUserId 当前登录用户 ID
     * @param role          当前登录用户角色
     * @return 课表记录 ID → 修订条数（无修订的不在结果中）
     */
    public Map<Long, Long> revisionFlags(List<Long> ids, Long currentUserId, String role) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        if ("TEACHER".equals(role)) {
            Set<Long> ownIds = listByTeacher(null, currentUserId).stream()
                    .map(Schedule::getId)
                    .collect(Collectors.toSet());
            ids = ids.stream().filter(ownIds::contains).collect(Collectors.toList());
        }
        return revisionService.counts(RevisionService.TARGET_SCHEDULE, ids);
    }

    /**
     * 统计各教师本学期课表上传进度（管理员审校页用）。
     *
     * @param semesterId 学期 ID
     * @return 教师上传状态列表
     */
    public List<TeacherUploadStatus> uploadProgress(Long semesterId) {
        List<User> teachers = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getRole, "TEACHER"));
        List<Schedule> semesterSchedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<Schedule>().eq(Schedule::getSemesterId, semesterId));
        Map<Long, List<Schedule>> byTeacher = semesterSchedules.stream()
                .filter(s -> s.getTeacherId() != null)
                .collect(Collectors.groupingBy(Schedule::getTeacherId));

        // 教师维度修订统计 = 本人课表行的行级修订 + 本人课程的课程级修订
        Map<Long, Long> scheduleRevisionCounts = revisionService.counts(
                RevisionService.TARGET_SCHEDULE,
                semesterSchedules.stream().map(Schedule::getId).collect(Collectors.toList()));
        List<Course> semesterCourses = courseMapper.selectList(
                new LambdaQueryWrapper<Course>().eq(Course::getSemesterId, semesterId));
        Map<Long, Long> courseRevisionCounts = revisionService.counts(
                RevisionService.TARGET_COURSE,
                semesterCourses.stream().map(Course::getId).collect(Collectors.toList()));
        Map<Long, Long> courseRevisionsByTeacher = new HashMap<>();
        for (Course course : semesterCourses) {
            if (course.getTeacherId() != null) {
                courseRevisionsByTeacher.merge(course.getTeacherId(),
                        courseRevisionCounts.getOrDefault(course.getId(), 0L), Long::sum);
            }
        }

        List<TeacherUploadStatus> result = new ArrayList<>();
        for (User teacher : teachers) {
            List<Schedule> own = byTeacher.getOrDefault(teacher.getId(), Collections.emptyList());
            LocalDateTime latest = own.stream()
                    .map(Schedule::getCreatedAt)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            long revisionCount = own.stream()
                    .mapToLong(s -> scheduleRevisionCounts.getOrDefault(s.getId(), 0L))
                    .sum() + courseRevisionsByTeacher.getOrDefault(teacher.getId(), 0L);
            result.add(new TeacherUploadStatus(teacher.getId(), teacher.getUsername(), teacher.getRealName(),
                    !own.isEmpty(), own.size(), latest, revisionCount));
        }
        return result;
    }

    /**
     * 向指定教师发送课表上传催办通知（管理员）。
     *
     * @param semesterId 学期 ID
     * @param teacherIds 教师用户 ID 列表
     * @return 实际发送的催办数量
     */
    @Transactional
    public int uploadRemind(Long semesterId, List<Long> teacherIds) {
        if (teacherIds == null || teacherIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要催办的教师");
        }
        Semester semester = semesterMapper.selectById(semesterId);
        String semesterName = semester == null ? "本学期"
                : (semester.getSemesterName() != null ? semester.getSemesterName() : semester.getAcademicYear());
        List<User> teachers = userMapper.selectList(
                new LambdaQueryWrapper<User>().in(User::getId, teacherIds).eq(User::getRole, "TEACHER"));
        for (User teacher : teachers) {
            notificationService.createUploadRemind(teacher.getId(), semesterName);
        }
        return teachers.size();
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

    private List<Schedule> listByTeacher(Long semesterId, Long teacherId) {
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<>();
        if (semesterId != null) {
            wrapper.eq(Schedule::getSemesterId, semesterId);
        }
        if (teacherId != null) {
            wrapper.eq(Schedule::getTeacherId, teacherId);
        }
        return scheduleMapper.selectList(wrapper);
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

    /** 重建课表行的结构化时间槽：先删旧行，再按 raw_schedule_text 解析写入（解析失败则留空）。 */
    private void replaceScheduleTime(Long scheduleId, String rawText) {
        scheduleTimeMapper.delete(
                new LambdaQueryWrapper<ScheduleTime>().eq(ScheduleTime::getScheduleId, scheduleId));
        ScheduleTimeParser.Slot slot = ScheduleTimeParser.parse(rawText);
        if (slot == null) {
            return;
        }
        ScheduleTime time = new ScheduleTime();
        time.setScheduleId(scheduleId);
        time.setDayOfWeek(slot.dayOfWeek());
        time.setStartSection(slot.startSection());
        time.setEndSection(slot.endSection());
        time.setWeekRange(slot.weekRange());
        time.setWeekType(slot.weekType());
        scheduleTimeMapper.insert(time);
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

    private String summarizeChanges(Map<String, String[]> changes) {
        return changes.entrySet().stream()
                .map(entry -> FIELD_LABELS.getOrDefault(entry.getKey(), entry.getKey())
                        + "：" + displayValue(entry.getValue()[0]) + " → " + displayValue(entry.getValue()[1]))
                .collect(Collectors.joining("；"));
    }

    private String displayValue(String value) {
        return value == null || value.isEmpty() ? "（空）" : value;
    }

    private String displayName(User user) {
        return user.getRealName() != null && !user.getRealName().isEmpty() ? user.getRealName() : user.getUsername();
    }

    /**
     * 课表导入结果，包含成功数量、失败数量与逐行错误明细。
     */
    public record ImportResult(int successCount, int failedCount, List<RowError> errors) {
    }

    /**
     * 导入解析预览：有效行数、逐行错误，以及将被覆盖的已修订记录统计。
     */
    public record ImportPreview(int totalCount, int validCount, List<RowError> errors,
                                int revisionConflicts, List<String> conflictCourses) {
    }

    /**
     * Excel 单条行导入错误，包含行号与错误信息。
     */
    public record RowError(int rowNum, String message) {
    }

    /**
     * 教师课表项：课程信息 + 结构化时间槽 + 修订标记，供周课表与卡片墙渲染。
     */
    public record MyScheduleItem(Long id, String courseCode, String courseName, BigDecimal totalHours,
                                 Long teacherId, String teacherName, String labCode, String labName,
                                 String classNames, Integer studentCount, String rawScheduleText,
                                 long revisionCount, List<SlotView> slots, LocalDateTime createdAt) {
    }

    /**
     * 时间槽视图：星期/节次/周次范围/单双周及实际生效周次集合。
     */
    public record SlotView(Integer dayOfWeek, Integer startSection, Integer endSection,
                           String weekRange, String weekType, List<Integer> weeks) {
    }

    /**
     * 教师上传进度：是否已上传、课表条数与最近上传时间。
     */
    public record TeacherUploadStatus(Long teacherId, String username, String realName,
                                      boolean uploaded, long scheduleCount, LocalDateTime latestUploadAt,
                                      long revisionCount) {
    }
}
