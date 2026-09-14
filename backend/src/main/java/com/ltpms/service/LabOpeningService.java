package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.dto.LabConflict;
import com.ltpms.dto.LabOpenBatchRequest;
import com.ltpms.dto.LabOpenCopyRequest;
import com.ltpms.dto.LabOpenExceptionRequest;
import com.ltpms.dto.LabOpenGrid;
import com.ltpms.dto.LabOpenTimeRequest;
import com.ltpms.dto.LabOpeningOverview;
import com.ltpms.dto.LabOpenToday;
import com.ltpms.entity.Holiday;
import com.ltpms.entity.LabOpenException;
import com.ltpms.entity.LabOpenTime;
import com.ltpms.entity.Laboratory;
import com.ltpms.entity.Schedule;
import com.ltpms.entity.ScheduleTime;
import com.ltpms.entity.Semester;
import com.ltpms.mapper.HolidayMapper;
import com.ltpms.mapper.LabOpenExceptionMapper;
import com.ltpms.mapper.LabOpenTimeMapper;
import com.ltpms.mapper.LaboratoryMapper;
import com.ltpms.mapper.ScheduleMapper;
import com.ltpms.mapper.ScheduleTimeMapper;
import com.ltpms.mapper.SemesterMapper;
import com.ltpms.util.WeekMath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 实验室开放管理服务：开放时段增删改查、批量覆盖式设置、从上学期复制、例外日维护，
 * 以及占用总览 / 单间周网格 / 今日开放视图；排课与开放矛盾（D9 三维判定）统一由
 * {@link #findConflicts(Long)} 提供，供总览计数与数据智能预警复用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabOpeningService {

    /** 开放对象固定枚举（D7），VARCHAR 存中文，不进 dictionary 主数据 */
    private static final Set<String> AUDIENCES = Set.of("全校", "本院", "指定专业", "教师专用");

    private static final Set<String> WEEK_TYPES = Set.of("ALL", "ODD", "EVEN");

    private static final int GRID_DAYS = 7;
    private static final int GRID_SECTIONS = 12;

    private final LabOpenTimeMapper labOpenTimeMapper;
    private final LabOpenExceptionMapper labOpenExceptionMapper;
    private final HolidayMapper holidayMapper;
    private final LaboratoryMapper laboratoryMapper;
    private final ScheduleMapper scheduleMapper;
    private final ScheduleTimeMapper scheduleTimeMapper;
    private final SemesterMapper semesterMapper;
    private final SemesterService semesterService;

    /**
     * 查询开放时段列表，可按学期与实验室过滤。
     *
     * @param semesterId 学期 ID，可选
     * @param labCode    实验室编号，可选
     * @return 开放时段列表（按实验室、星期、起始节次排序）
     */
    public List<LabOpenTime> list(Long semesterId, String labCode) {
        LambdaQueryWrapper<LabOpenTime> wrapper = new LambdaQueryWrapper<LabOpenTime>()
                .eq(semesterId != null, LabOpenTime::getSemesterId, semesterId)
                .eq(labCode != null && !labCode.isBlank(), LabOpenTime::getLabCode, labCode)
                .orderByAsc(LabOpenTime::getLabCode)
                .orderByAsc(LabOpenTime::getDayOfWeek)
                .orderByAsc(LabOpenTime::getStartSection);
        return labOpenTimeMapper.selectList(wrapper);
    }

    /**
     * 新增单条开放时段（仅管理员）。
     *
     * @param request 开放时段信息
     */
    @Transactional
    public void create(LabOpenTimeRequest request) {
        resolveSemester(request.getSemesterId());
        checkLabExists(request.getLabCode());
        validateSections(request.getStartSection(), request.getEndSection());

        LabOpenTime row = new LabOpenTime();
        row.setLabCode(request.getLabCode());
        row.setSemesterId(request.getSemesterId());
        row.setDayOfWeek(request.getDayOfWeek());
        row.setStartSection(request.getStartSection());
        row.setEndSection(request.getEndSection());
        row.setWeekRange(request.getWeekRange());
        row.setWeekType(normalizeWeekType(request.getWeekType()));
        row.setAudience(normalizeAudience(request.getAudience()));
        row.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        row.setRemark(request.getRemark());
        labOpenTimeMapper.insert(row);
    }

    /**
     * 更新单条开放时段（仅管理员）。
     *
     * @param id      记录 ID
     * @param request 开放时段信息
     */
    @Transactional
    public void update(Long id, LabOpenTimeRequest request) {
        LabOpenTime existing = labOpenTimeMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "开放时段不存在");
        }
        resolveSemester(request.getSemesterId());
        checkLabExists(request.getLabCode());
        validateSections(request.getStartSection(), request.getEndSection());

        existing.setLabCode(request.getLabCode());
        existing.setSemesterId(request.getSemesterId());
        existing.setDayOfWeek(request.getDayOfWeek());
        existing.setStartSection(request.getStartSection());
        existing.setEndSection(request.getEndSection());
        existing.setWeekRange(request.getWeekRange());
        existing.setWeekType(normalizeWeekType(request.getWeekType()));
        existing.setAudience(normalizeAudience(request.getAudience()));
        existing.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        existing.setRemark(request.getRemark());
        labOpenTimeMapper.updateById(existing);
    }

    /**
     * 删除单条开放时段（仅管理员）。
     *
     * @param id 记录 ID
     */
    @Transactional
    public void delete(Long id) {
        LabOpenTime existing = labOpenTimeMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "开放时段不存在");
        }
        labOpenTimeMapper.deleteById(id);
    }

    /**
     * 批量覆盖式设置：对选中实验室先删后插（语义对齐课表「整学期覆盖」）。
     *
     * @param request 批量设置请求
     * @return 实际写入的时段条数
     */
    @Transactional
    public int batch(LabOpenBatchRequest request) {
        resolveSemester(request.getSemesterId());
        String audience = normalizeAudience(request.getAudience());
        for (LabOpenBatchRequest.Slot slot : request.getSlots()) {
            validateSections(slot.getStartSection(), slot.getEndSection());
            normalizeWeekType(slot.getWeekType());
        }

        int inserted = 0;
        for (String labCode : new LinkedHashSet<>(request.getLabCodes())) {
            checkLabExists(labCode);
            labOpenTimeMapper.delete(new LambdaQueryWrapper<LabOpenTime>()
                    .eq(LabOpenTime::getLabCode, labCode)
                    .eq(LabOpenTime::getSemesterId, request.getSemesterId()));
            for (LabOpenBatchRequest.Slot slot : request.getSlots()) {
                LabOpenTime row = new LabOpenTime();
                row.setLabCode(labCode);
                row.setSemesterId(request.getSemesterId());
                row.setDayOfWeek(slot.getDayOfWeek());
                row.setStartSection(slot.getStartSection());
                row.setEndSection(slot.getEndSection());
                row.setWeekRange(slot.getWeekRange());
                row.setWeekType(normalizeWeekType(slot.getWeekType()));
                row.setAudience(audience);
                row.setEnabled(1);
                row.setRemark(request.getRemark());
                labOpenTimeMapper.insert(row);
                inserted++;
            }
        }
        return inserted;
    }

    /**
     * 从上学期复制开放计划（覆盖式、幂等）：按选中实验室覆盖目标学期已有计划；
     * 未指定实验室时复制源学期内全部已有计划的实验室。
     *
     * @param request 复制请求
     * @return 实际复制的实验室间数
     */
    @Transactional
    public int copy(LabOpenCopyRequest request) {
        if (request.getFromSemesterId().equals(request.getToSemesterId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源学期与目标学期不能相同");
        }
        resolveSemester(request.getFromSemesterId());
        resolveSemester(request.getToSemesterId());

        List<LabOpenTime> sourceRows = labOpenTimeMapper.selectList(new LambdaQueryWrapper<LabOpenTime>()
                .eq(LabOpenTime::getSemesterId, request.getFromSemesterId()));
        if (sourceRows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源学期没有可复制的开放计划");
        }
        Map<String, List<LabOpenTime>> byLab = sourceRows.stream()
                .collect(Collectors.groupingBy(LabOpenTime::getLabCode));

        List<String> targets = request.getLabCodes() == null || request.getLabCodes().isEmpty()
                ? byLab.keySet().stream().sorted().toList()
                : new ArrayList<>(new LinkedHashSet<>(request.getLabCodes()));

        int copiedLabs = 0;
        for (String labCode : targets) {
            List<LabOpenTime> rows = byLab.get(labCode);
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            labOpenTimeMapper.delete(new LambdaQueryWrapper<LabOpenTime>()
                    .eq(LabOpenTime::getLabCode, labCode)
                    .eq(LabOpenTime::getSemesterId, request.getToSemesterId()));
            for (LabOpenTime source : rows) {
                LabOpenTime row = new LabOpenTime();
                row.setLabCode(source.getLabCode());
                row.setSemesterId(request.getToSemesterId());
                row.setDayOfWeek(source.getDayOfWeek());
                row.setStartSection(source.getStartSection());
                row.setEndSection(source.getEndSection());
                row.setWeekRange(source.getWeekRange());
                row.setWeekType(source.getWeekType());
                row.setAudience(source.getAudience());
                row.setEnabled(source.getEnabled());
                row.setRemark(source.getRemark());
                labOpenTimeMapper.insert(row);
            }
            copiedLabs++;
        }
        if (copiedLabs == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "所选实验室在源学期没有开放计划");
        }
        return copiedLabs;
    }

    /**
     * 查询例外日列表，可按学期与实验室过滤。
     *
     * @param semesterId 学期 ID，可选
     * @param labCode    实验室编号，可选
     * @return 例外日列表（按开始日期排序）
     */
    public List<LabOpenException> listExceptions(Long semesterId, String labCode) {
        return labOpenExceptionMapper.selectList(new LambdaQueryWrapper<LabOpenException>()
                .eq(semesterId != null, LabOpenException::getSemesterId, semesterId)
                .eq(labCode != null && !labCode.isBlank(), LabOpenException::getLabCode, labCode)
                .orderByAsc(LabOpenException::getStartDate));
    }

    /**
     * 新增例外日（仅管理员）。
     *
     * @param request 例外日信息
     */
    @Transactional
    public void createException(LabOpenExceptionRequest request) {
        resolveSemester(request.getSemesterId());
        checkLabExists(request.getLabCode());
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束日期不能早于开始日期");
        }
        LabOpenException row = new LabOpenException();
        row.setLabCode(request.getLabCode());
        row.setSemesterId(request.getSemesterId());
        row.setStartDate(request.getStartDate());
        row.setEndDate(request.getEndDate());
        row.setReason(request.getReason());
        labOpenExceptionMapper.insert(row);
    }

    /**
     * 删除例外日（仅管理员）。
     *
     * @param id 记录 ID
     */
    @Transactional
    public void deleteException(Long id) {
        LabOpenException existing = labOpenExceptionMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "例外日不存在");
        }
        labOpenExceptionMapper.deleteById(id);
    }

    /**
     * 占用总览：每间实验室的开放节时 / 占用节时 / 利用率 / 矛盾数 / 是否已配置。
     * 节时口径 = Σ(节数 × 生效周数)（D13，单双周减半，不含节假日/例外日扣减）。
     *
     * @param semesterId 学期 ID
     * @return 占用总览
     */
    public LabOpeningOverview overview(Long semesterId) {
        Semester semester = resolveSemester(semesterId);
        LocalDate week1Start = week1StartOf(semester);

        List<Laboratory> labs = laboratoryMapper.selectList(new LambdaQueryWrapper<Laboratory>()
                .orderByAsc(Laboratory::getLabCode));
        List<LabOpenTime> allOpenRows = labOpenTimeMapper.selectList(new LambdaQueryWrapper<LabOpenTime>()
                .eq(LabOpenTime::getSemesterId, semesterId));
        Map<String, List<LabOpenTime>> openByLab = allOpenRows.stream()
                .collect(Collectors.groupingBy(LabOpenTime::getLabCode));

        Map<String, Integer> occupiedByLab = occupiedSlotHoursByLab(semesterId);
        Map<String, Long> conflictByLab = findConflicts(semesterId).stream()
                .collect(Collectors.groupingBy(LabConflict::getLabCode, Collectors.counting()));

        List<LabOpeningOverview.LabItem> items = new ArrayList<>(labs.size());
        for (Laboratory lab : labs) {
            List<LabOpenTime> rows = openByLab.getOrDefault(lab.getLabCode(), List.of());
            List<LabOpenTime> enabledRows = rows.stream()
                    .filter(r -> r.getEnabled() != null && r.getEnabled() == 1)
                    .toList();

            int openSlotHours = enabledRows.stream()
                    .mapToInt(r -> sectionSpan(r.getStartSection(), r.getEndSection())
                            * WeekMath.weekCount(r.getWeekRange(), r.getWeekType()))
                    .sum();
            int occupiedSlotHours = occupiedByLab.getOrDefault(lab.getLabCode(), 0);
            Double rate = openSlotHours > 0
                    ? Math.round(occupiedSlotHours * 1000.0 / openSlotHours) / 10.0
                    : null;
            String audience = enabledRows.stream()
                    .map(LabOpenTime::getAudience)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining("、"));

            items.add(LabOpeningOverview.LabItem.builder()
                    .labId(lab.getId())
                    .labCode(lab.getLabCode())
                    .labName(lab.getLabName())
                    .planned(!enabledRows.isEmpty())
                    .audience(audience.isEmpty() ? null : audience)
                    .openSlotHours(openSlotHours)
                    .occupiedSlotHours(occupiedSlotHours)
                    .rate(rate)
                    .conflictCount(conflictByLab.getOrDefault(lab.getLabCode(), 0L).intValue())
                    .build());
        }
        return LabOpeningOverview.builder()
                .semesterId(semester.getId())
                .week1StartDate(week1Start)
                .totalWeeks(WeekMath.totalWeeks(week1Start, semester.getEndDate()))
                .labs(items)
                .build();
    }

    /**
     * 单间实验室周网格：7 天 × 12 节，四色态（占用 / 开放空闲 / 未开放 / 闭馆）。
     * 未指定周次时取当前周并夹取到学期范围内。
     *
     * @param semesterId 学期 ID
     * @param labCode    实验室编号
     * @param week       周次，可选
     * @return 周网格
     */
    public LabOpenGrid grid(Long semesterId, String labCode, Integer week) {
        Semester semester = resolveSemester(semesterId);
        Laboratory lab = laboratoryMapper.selectOne(new LambdaQueryWrapper<Laboratory>()
                .eq(Laboratory::getLabCode, labCode).last("LIMIT 1"));
        if (lab == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "实验室不存在");
        }
        LocalDate week1Start = week1StartOf(semester);
        int totalWeeks = WeekMath.totalWeeks(week1Start, semester.getEndDate());
        if (totalWeeks < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该学期周历无效，请先配置第 1 周起始日");
        }

        int weekNo = resolveWeekNo(week, week1Start, totalWeeks);
        LocalDate weekStart = WeekMath.weekDateRange(week1Start, weekNo)[0];

        Map<LocalDate, String> holidayMap = holidayMapInSemester(semester);
        List<LabOpenException> exceptions = labOpenExceptionMapper.selectList(new LambdaQueryWrapper<LabOpenException>()
                .eq(LabOpenException::getSemesterId, semesterId)
                .eq(LabOpenException::getLabCode, labCode));

        Set<Integer> occupiedCells = gridOccupiedCells(labCode, semesterId, weekNo);
        Set<Integer> openCells = gridOpenCells(labCode, semesterId, weekNo);

        List<LabOpenGrid.Cell> cells = new ArrayList<>(GRID_DAYS * GRID_SECTIONS);
        for (int dow = 1; dow <= GRID_DAYS; dow++) {
            LocalDate date = weekStart.plusDays(dow - 1L);
            boolean closed = holidayMap.containsKey(date) || exceptionCovers(exceptions, date);
            for (int section = 1; section <= GRID_SECTIONS; section++) {
                String state;
                if (closed) {
                    state = LabOpenGrid.STATE_CLOSED;
                } else if (occupiedCells.contains(dow * 100 + section)) {
                    state = LabOpenGrid.STATE_OCCUPIED;
                } else if (openCells.contains(dow * 100 + section)) {
                    state = LabOpenGrid.STATE_OPEN;
                } else {
                    state = LabOpenGrid.STATE_NOT_OPEN;
                }
                cells.add(LabOpenGrid.Cell.builder().dayOfWeek(dow).section(section).state(state).build());
            }
        }

        List<LabOpenGrid.ClosedDate> closedDates = new ArrayList<>();
        for (Map.Entry<LocalDate, String> entry : holidayMap.entrySet()) {
            closedDates.add(closedDate(entry.getKey(), entry.getValue(), "HOLIDAY", week1Start, totalWeeks));
        }
        for (LabOpenException exception : exceptions) {
            LocalDate date = exception.getStartDate();
            while (!date.isAfter(exception.getEndDate())) {
                String name = exception.getReason() == null || exception.getReason().isBlank()
                        ? "临时闭馆" : exception.getReason();
                closedDates.add(closedDate(date, name, "EXCEPTION", week1Start, totalWeeks));
                date = date.plusDays(1);
            }
        }
        closedDates.sort(Comparator.comparing(LabOpenGrid.ClosedDate::getDate));

        LocalDate[] weekRange = WeekMath.weekDateRange(week1Start, weekNo);
        return LabOpenGrid.builder()
                .semesterId(semester.getId())
                .labCode(lab.getLabCode())
                .labName(lab.getLabName())
                .week1StartDate(week1Start)
                .totalWeeks(totalWeeks)
                .week(weekNo)
                .weekStartDate(weekRange[0])
                .weekEndDate(weekRange[1])
                .cells(cells)
                .closedDates(closedDates)
                .build();
    }

    /**
     * 今日开放实验室（供仪表盘小块与教师端）：按今日星期与所在周次取生效时段，
     * 节假日与例外日闭馆；未指定学期时取当前学期。
     *
     * @param semesterId 学期 ID，可选
     * @return 今日开放信息；无学期时 semesterId 为 null 且列表为空
     */
    public LabOpenToday today(Long semesterId) {
        Semester semester = semesterId == null ? semesterService.getCurrentSemester() : resolveSemester(semesterId);
        if (semester == null) {
            return LabOpenToday.builder().labs(List.of()).build();
        }

        LocalDate today = LocalDate.now();
        LocalDate week1Start = week1StartOf(semester);
        int totalWeeks = WeekMath.totalWeeks(week1Start, semester.getEndDate());
        int weekNo = WeekMath.weekNumberOf(today, week1Start);
        boolean inSemester = totalWeeks >= 1 && weekNo >= 1 && weekNo <= totalWeeks;

        Holiday holiday = holidayMapper.selectOne(new LambdaQueryWrapper<Holiday>()
                .eq(Holiday::getHolidayDate, today).last("LIMIT 1"));

        LabOpenToday.LabOpenTodayBuilder builder = LabOpenToday.builder()
                .semesterId(semester.getId())
                .semesterName(semester.getSemesterName())
                .date(today)
                .dayOfWeek(today.getDayOfWeek().getValue())
                .weekNo(inSemester ? weekNo : null)
                .inSemester(inSemester)
                .holiday(holiday != null)
                .holidayName(holiday == null ? null : holiday.getName())
                .labs(List.of());

        if (!inSemester || holiday != null) {
            return builder.build();
        }

        List<LabOpenTime> rows = labOpenTimeMapper.selectList(new LambdaQueryWrapper<LabOpenTime>()
                .eq(LabOpenTime::getSemesterId, semester.getId())
                .eq(LabOpenTime::getEnabled, 1)
                .eq(LabOpenTime::getDayOfWeek, today.getDayOfWeek().getValue()));
        List<LabOpenTime> activeRows = rows.stream()
                .filter(r -> WeekMath.parseWeeks(r.getWeekRange(), r.getWeekType()).contains(weekNo))
                .toList();
        if (activeRows.isEmpty()) {
            return builder.build();
        }

        List<LabOpenException> exceptions = labOpenExceptionMapper.selectList(new LambdaQueryWrapper<LabOpenException>()
                .eq(LabOpenException::getSemesterId, semester.getId())
                .le(LabOpenException::getStartDate, today)
                .ge(LabOpenException::getEndDate, today));
        Set<String> closedLabs = exceptions.stream().map(LabOpenException::getLabCode).collect(Collectors.toSet());

        Map<String, String> labNames = laboratoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Laboratory::getLabCode, Laboratory::getLabName, (a, b) -> a));

        Map<String, List<LabOpenTime>> byLab = activeRows.stream()
                .filter(r -> !closedLabs.contains(r.getLabCode()))
                .collect(Collectors.groupingBy(LabOpenTime::getLabCode, LinkedHashMap::new, Collectors.toList()));

        List<LabOpenToday.LabItem> items = new ArrayList<>(byLab.size());
        for (Map.Entry<String, List<LabOpenTime>> entry : byLab.entrySet()) {
            List<LabOpenTime> labRows = entry.getValue();
            String audience = labRows.stream()
                    .map(LabOpenTime::getAudience)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining("、"));
            items.add(LabOpenToday.LabItem.builder()
                    .labCode(entry.getKey())
                    .labName(labNames.getOrDefault(entry.getKey(), entry.getKey()))
                    .audience(audience.isEmpty() ? null : audience)
                    .sectionText(mergeSectionText(labRows))
                    .build());
        }
        items.sort(Comparator.comparing(LabOpenToday.LabItem::getLabCode));
        return builder.labs(items).build();
    }

    /**
     * 排课与开放矛盾（D9）：星期 + 节次 + 周次三维不重叠即矛盾（含周次交集与单双周相容性，
     * 不含节假日/例外日）。
     *
     * @param semesterId 学期 ID
     * @return 矛盾明细列表
     */
    public List<LabConflict> findConflicts(Long semesterId) {
        resolveSemester(semesterId);
        List<Schedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getSemesterId, semesterId));
        if (schedules.isEmpty()) {
            return List.of();
        }
        List<Long> scheduleIds = schedules.stream().map(Schedule::getId).toList();
        Map<Long, List<ScheduleTime>> timesBySchedule = scheduleTimeMapper.selectList(
                        new LambdaQueryWrapper<ScheduleTime>().in(ScheduleTime::getScheduleId, scheduleIds))
                .stream().collect(Collectors.groupingBy(ScheduleTime::getScheduleId));

        List<LabOpenTime> enabledRows = labOpenTimeMapper.selectList(new LambdaQueryWrapper<LabOpenTime>()
                .eq(LabOpenTime::getSemesterId, semesterId)
                .eq(LabOpenTime::getEnabled, 1));
        Map<String, List<LabOpenTime>> openByLab = enabledRows.stream()
                .collect(Collectors.groupingBy(LabOpenTime::getLabCode));
        Map<String, String> labNames = laboratoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Laboratory::getLabCode, Laboratory::getLabName, (a, b) -> a));

        List<LabConflict> conflicts = new ArrayList<>();
        for (Schedule schedule : schedules) {
            List<LabOpenTime> labOpens = openByLab.getOrDefault(schedule.getLabCode(), List.of());
            for (ScheduleTime time : timesBySchedule.getOrDefault(schedule.getId(), List.of())) {
                Set<Integer> timeWeeks = WeekMath.parseWeeks(time.getWeekRange(), time.getWeekType());
                boolean covered = labOpens.stream().anyMatch(open ->
                        Objects.equals(open.getDayOfWeek(), time.getDayOfWeek())
                                && WeekMath.sectionsOverlap(open.getStartSection(), open.getEndSection(),
                                        time.getStartSection(), time.getEndSection())
                                && WeekMath.weeksOverlap(
                                        WeekMath.parseWeeks(open.getWeekRange(), open.getWeekType()), timeWeeks));
                if (!covered) {
                    conflicts.add(LabConflict.builder()
                            .labCode(schedule.getLabCode())
                            .labName(labNames.getOrDefault(schedule.getLabCode(), schedule.getLabCode()))
                            .courseName(schedule.getCourseName())
                            .classNames(schedule.getClassNames())
                            .dayOfWeek(time.getDayOfWeek())
                            .startSection(time.getStartSection())
                            .endSection(time.getEndSection())
                            .weekText(weekText(time.getWeekRange(), time.getWeekType(), timeWeeks))
                            .build());
                }
            }
        }
        return conflicts;
    }

    private Semester resolveSemester(Long semesterId) {
        Semester semester = semesterMapper.selectById(semesterId);
        if (semester == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学期不存在");
        }
        return semester;
    }

    private void checkLabExists(String labCode) {
        Long count = laboratoryMapper.selectCount(new LambdaQueryWrapper<Laboratory>()
                .eq(Laboratory::getLabCode, labCode));
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "实验室编号不存在：" + labCode);
        }
    }

    private void validateSections(Integer startSection, Integer endSection) {
        if (startSection == null || endSection == null || startSection > endSection) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "起始节次不能大于结束节次");
        }
    }

    private String normalizeWeekType(String weekType) {
        String normalized = weekType == null || weekType.isBlank() ? "ALL" : weekType.trim().toUpperCase();
        if (!WEEK_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "周类型取值为 ALL/ODD/EVEN");
        }
        return normalized;
    }

    private String normalizeAudience(String audience) {
        if (audience == null || audience.isBlank()) {
            return "全校";
        }
        String trimmed = audience.trim();
        if (!AUDIENCES.contains(trimmed)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "开放对象取值为：全校/本院/指定专业/教师专用");
        }
        return trimmed;
    }

    private LocalDate week1StartOf(Semester semester) {
        return semester.getWeek1StartDate() != null ? semester.getWeek1StartDate() : semester.getStartDate();
    }

    private int resolveWeekNo(Integer week, LocalDate week1Start, int totalWeeks) {
        if (week != null) {
            if (week < 1 || week > totalWeeks) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "周次超出学期范围（1-" + totalWeeks + "）");
            }
            return week;
        }
        int current = WeekMath.weekNumberOf(LocalDate.now(), week1Start);
        if (current < 1) {
            return 1;
        }
        return Math.min(current, totalWeeks);
    }

    private Map<LocalDate, String> holidayMapInSemester(Semester semester) {
        return holidayMapper.selectList(new LambdaQueryWrapper<Holiday>()
                        .ge(Holiday::getHolidayDate, semester.getStartDate())
                        .le(Holiday::getHolidayDate, semester.getEndDate())
                        .orderByAsc(Holiday::getHolidayDate))
                .stream()
                .collect(Collectors.toMap(Holiday::getHolidayDate, Holiday::getName, (a, b) -> a, LinkedHashMap::new));
    }

    private boolean exceptionCovers(List<LabOpenException> exceptions, LocalDate date) {
        return exceptions.stream()
                .anyMatch(e -> !date.isBefore(e.getStartDate()) && !date.isAfter(e.getEndDate()));
    }

    private LabOpenGrid.ClosedDate closedDate(LocalDate date, String name, String type,
                                              LocalDate week1Start, int totalWeeks) {
        int week = WeekMath.weekNumberOf(date, week1Start);
        return LabOpenGrid.ClosedDate.builder()
                .date(date)
                .name(name)
                .type(type)
                .week(Math.min(Math.max(week, 0), totalWeeks))
                .dayOfWeek(date.getDayOfWeek().getValue())
                .build();
    }

    /** 网格占用格：dayOfWeek*100+section 标记（仅指定周次内生效的课） */
    private Set<Integer> gridOccupiedCells(String labCode, Long semesterId, int weekNo) {
        List<Schedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getSemesterId, semesterId)
                .eq(Schedule::getLabCode, labCode));
        if (schedules.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = schedules.stream().map(Schedule::getId).toList();
        List<ScheduleTime> times = scheduleTimeMapper.selectList(new LambdaQueryWrapper<ScheduleTime>()
                .in(ScheduleTime::getScheduleId, ids));
        Set<Integer> cells = new LinkedHashSet<>();
        for (ScheduleTime time : times) {
            if (!WeekMath.parseWeeks(time.getWeekRange(), time.getWeekType()).contains(weekNo)) {
                continue;
            }
            for (int section = time.getStartSection(); section <= time.getEndSection(); section++) {
                cells.add(time.getDayOfWeek() * 100 + section);
            }
        }
        return cells;
    }

    /** 网格开放格：dayOfWeek*100+section 标记（仅指定周次内生效的启用时段） */
    private Set<Integer> gridOpenCells(String labCode, Long semesterId, int weekNo) {
        List<LabOpenTime> rows = labOpenTimeMapper.selectList(new LambdaQueryWrapper<LabOpenTime>()
                .eq(LabOpenTime::getSemesterId, semesterId)
                .eq(LabOpenTime::getLabCode, labCode)
                .eq(LabOpenTime::getEnabled, 1));
        Set<Integer> cells = new LinkedHashSet<>();
        for (LabOpenTime row : rows) {
            if (!WeekMath.parseWeeks(row.getWeekRange(), row.getWeekType()).contains(weekNo)) {
                continue;
            }
            for (int section = row.getStartSection(); section <= row.getEndSection(); section++) {
                cells.add(row.getDayOfWeek() * 100 + section);
            }
        }
        return cells;
    }

    private Map<String, Integer> occupiedSlotHoursByLab(Long semesterId) {
        List<Schedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getSemesterId, semesterId));
        if (schedules.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> labBySchedule = schedules.stream()
                .collect(Collectors.toMap(Schedule::getId, Schedule::getLabCode, (a, b) -> a));
        List<ScheduleTime> times = scheduleTimeMapper.selectList(new LambdaQueryWrapper<ScheduleTime>()
                .in(ScheduleTime::getScheduleId, labBySchedule.keySet()));
        Map<String, Integer> result = new LinkedHashMap<>();
        for (ScheduleTime time : times) {
            String labCode = labBySchedule.get(time.getScheduleId());
            if (labCode == null) {
                continue;
            }
            int hours = sectionSpan(time.getStartSection(), time.getEndSection())
                    * WeekMath.weekCount(time.getWeekRange(), time.getWeekType());
            result.merge(labCode, hours, Integer::sum);
        }
        return result;
    }

    private int sectionSpan(Integer startSection, Integer endSection) {
        return endSection - startSection + 1;
    }

    private String weekText(String weekRange, String weekType, Set<Integer> weeks) {
        String text = WeekMath.formatWeeks(weeks) + "周";
        if ("ODD".equalsIgnoreCase(weekType)) {
            return text + "（单周）";
        }
        if ("EVEN".equalsIgnoreCase(weekType)) {
            return text + "（双周）";
        }
        return text;
    }

    private String mergeSectionText(List<LabOpenTime> rows) {
        List<int[]> ranges = rows.stream()
                .map(r -> new int[]{r.getStartSection(), r.getEndSection()})
                .sorted(Comparator.comparingInt(r -> r[0]))
                .toList();
        List<int[]> merged = new ArrayList<>();
        for (int[] range : ranges) {
            if (merged.isEmpty() || range[0] > merged.get(merged.size() - 1)[1] + 1) {
                merged.add(new int[]{range[0], range[1]});
            } else {
                merged.get(merged.size() - 1)[1] = Math.max(merged.get(merged.size() - 1)[1], range[1]);
            }
        }
        return merged.stream()
                .map(r -> r[0] == r[1] ? String.valueOf(r[0]) : r[0] + "-" + r[1])
                .collect(Collectors.joining("、")) + "节";
    }
}
