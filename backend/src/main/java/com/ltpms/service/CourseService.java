package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.common.PageResult;
import com.ltpms.dto.CourseEditRequest;
import com.ltpms.entity.Course;
import com.ltpms.entity.Experiment;
import com.ltpms.entity.Semester;
import com.ltpms.entity.User;
import com.ltpms.mapper.CourseMapper;
import com.ltpms.mapper.ExperimentMapper;
import com.ltpms.mapper.SemesterMapper;
import com.ltpms.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课程服务，提供课程列表查询、课程详情（含教师与实验项目）、
 * 聚合行编辑与修订留痕等能力。
 */
@Service
@RequiredArgsConstructor
public class CourseService {

    private static final Map<String, String> FIELD_LABELS = Map.of(
            "courseName", "课程名称",
            "major", "专业",
            "totalHours", "总学时");

    private final CourseMapper courseMapper;
    private final UserMapper userMapper;
    private final SemesterMapper semesterMapper;
    private final ExperimentMapper experimentMapper;
    private final RevisionService revisionService;
    private final NotificationService notificationService;

    /**
     * 查询课程列表，可按学期筛选；教师角色仅返回本人负责课程。
     *
     * @param semesterId    学期 ID，为空时不按学期筛选
     * @param currentUserId 当前登录用户 ID
     * @param role          当前用户角色
     * @return 课程列表
     */
    public List<Course> list(Long semesterId, Long currentUserId, String role) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        if (semesterId != null) {
            wrapper.eq(Course::getSemesterId, semesterId);
        }
        if ("TEACHER".equals(role)) {
            wrapper.eq(Course::getTeacherId, currentUserId);
        }
        wrapper.orderByDesc(Course::getCreatedAt);
        List<Course> courses = courseMapper.selectList(wrapper);
        enrichNames(courses);
        return courses;
    }

    /** 列表响应补充授课教师姓名与学期名称（实体无此表字段，前端列展示用） */
    private void enrichNames(List<Course> courses) {
        if (courses.isEmpty()) {
            return;
        }
        Set<Long> teacherIds = courses.stream()
                .map(Course::getTeacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!teacherIds.isEmpty()) {
            Map<Long, String> nameMap = new HashMap<>();
            for (User user : userMapper.selectBatchIds(teacherIds)) {
                nameMap.put(user.getId(), user.getRealName() != null ? user.getRealName() : user.getUsername());
            }
            for (Course course : courses) {
                if (course.getTeacherId() != null) {
                    course.setTeacherName(nameMap.get(course.getTeacherId()));
                }
            }
        }
        Set<Long> semesterIds = courses.stream()
                .map(Course::getSemesterId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!semesterIds.isEmpty()) {
            Map<Long, String> semesterMap = new HashMap<>();
            for (Semester semester : semesterMapper.selectBatchIds(semesterIds)) {
                semesterMap.put(semester.getId(),
                        semester.getSemesterName() != null ? semester.getSemesterName() : semester.getAcademicYear());
            }
            for (Course course : courses) {
                if (course.getSemesterId() != null) {
                    course.setSemesterName(semesterMap.get(course.getSemesterId()));
                }
            }
        }
    }

    /**
     * 根据 ID 获取课程，不存在时抛出异常。
     *
     * @param id 课程 ID
     * @return 课程实体
     * @throws BusinessException 当课程不存在时抛出
     */
    public Course getById(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        return course;
    }

    /**
     * 获取课程详情，包含课程基本信息、授课教师与实验项目列表。
     *
     * @param id 课程 ID
     * @return 课程详情（含教师与实验项目）
     * @throws BusinessException 当课程不存在时抛出
     */
    public CourseDetail getDetail(Long id) {
        Course course = getById(id);
        User teacher = userMapper.selectById(course.getTeacherId());
        List<Experiment> experiments = experimentMapper.selectList(
                new LambdaQueryWrapper<Experiment>()
                        .eq(Experiment::getCourseId, id)
                        .orderByAsc(Experiment::getSortOrder));
        return new CourseDetail(course, teacher, experiments);
    }

    /**
     * 编辑课程聚合行（教师限本人、管理员不限）：字段级 diff 落修订留痕；
     * 管理员修订会通知对应教师。
     *
     * @param id            课程 ID
     * @param request       编辑请求，字段为 null 表示不修改
     * @param currentUserId 当前登录用户 ID
     * @param role          当前登录用户角色
     */
    @Transactional
    public void update(Long id, CourseEditRequest request, Long currentUserId, String role) {
        Course course = getById(id);
        if ("TEACHER".equals(role) && !course.getTeacherId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能修改本人的课程");
        }

        Map<String, String[]> changes = new LinkedHashMap<>();
        Course update = new Course();
        update.setId(id);

        if (request.getCourseName() != null && !request.getCourseName().equals(course.getCourseName())) {
            if (request.getCourseName().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "课程名称不能为空");
            }
            changes.put("courseName", new String[]{course.getCourseName(), request.getCourseName()});
            update.setCourseName(request.getCourseName());
        }
        if (request.getMajor() != null) {
            String newMajor = request.getMajor().isBlank() ? null : request.getMajor();
            if (!java.util.Objects.equals(newMajor, course.getMajor())) {
                changes.put("major", new String[]{course.getMajor(), newMajor});
                update.setMajor(newMajor);
            }
        }
        if (request.getTotalHours() != null && !isSameHours(request.getTotalHours(), course.getTotalHours())) {
            if (request.getTotalHours().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "总学时必须大于0");
            }
            changes.put("totalHours", new String[]{
                    course.getTotalHours() == null ? null : course.getTotalHours().stripTrailingZeros().toPlainString(),
                    request.getTotalHours().stripTrailingZeros().toPlainString()});
            update.setTotalHours(request.getTotalHours());
        }

        if (changes.isEmpty()) {
            return;
        }

        courseMapper.updateById(update);
        revisionService.record(RevisionService.TARGET_COURSE, id, changes, role, currentUserId);

        if ("ADMIN".equals(role)) {
            notificationService.createScheduleRevised(course.getTeacherId(), summarizeChanges(changes));
        }
    }

    /**
     * 查询某课程的修订明细（教师限本人、管理员不限）。
     *
     * @param id            课程 ID
     * @param currentUserId 当前登录用户 ID
     * @param role          当前登录用户角色
     * @return 修订明细列表（按时间升序）
     */
    public List<RevisionService.RevisionView> listRevisions(Long id, Long currentUserId, String role) {
        Course course = getById(id);
        if ("TEACHER".equals(role) && !course.getTeacherId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能查看本人课程的修订记录");
        }
        return revisionService.list(RevisionService.TARGET_COURSE, id);
    }

    /**
     * 批量查询课程修订条数（行级笔形标记数据源）；教师仅能查询本人课程。
     *
     * @param ids           课程 ID 列表
     * @param currentUserId 当前登录用户 ID
     * @param role          当前登录用户角色
     * @return 课程 ID → 修订条数（无修订的不在结果中）
     */
    public Map<Long, Long> revisionFlags(List<Long> ids, Long currentUserId, String role) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        if ("TEACHER".equals(role)) {
            Set<Long> ownIds = courseMapper.selectList(
                            new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, currentUserId))
                    .stream()
                    .map(Course::getId)
                    .collect(Collectors.toSet());
            ids = ids.stream().filter(ownIds::contains).collect(Collectors.toList());
        }
        return revisionService.counts(RevisionService.TARGET_COURSE, ids);
    }

    private boolean isSameHours(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
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

    public record CourseDetail(Course course, User teacher, List<Experiment> experiments) {
    }
}
