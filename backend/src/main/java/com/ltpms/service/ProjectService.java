package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.dto.project.MemberAddRequest;
import com.ltpms.dto.project.ProjectRequest;
import com.ltpms.entity.Course;
import com.ltpms.entity.CourseStudent;
import com.ltpms.entity.LabDataset;
import com.ltpms.entity.LabProject;
import com.ltpms.entity.LabProjectMember;
import com.ltpms.entity.LabProjectTask;
import com.ltpms.entity.OperationLogEntity;
import com.ltpms.entity.Semester;
import com.ltpms.entity.Student;
import com.ltpms.entity.User;
import com.ltpms.mapper.CourseMapper;
import com.ltpms.mapper.CourseStudentMapper;
import com.ltpms.mapper.LabDatasetMapper;
import com.ltpms.mapper.LabProjectMapper;
import com.ltpms.mapper.LabProjectMemberMapper;
import com.ltpms.mapper.LabProjectTaskMapper;
import com.ltpms.mapper.OperationLogMapper;
import com.ltpms.mapper.SemesterMapper;
import com.ltpms.mapper.StudentMapper;
import com.ltpms.mapper.UserMapper;
import com.ltpms.util.IpUtil;
import com.ltpms.util.JsonUtil;
import com.ltpms.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课题与成员服务（S1）：课题 CRUD、成员快照带入/移出、项目内名单导入自动建档。
 * 权限判定全部收口 ProjectAuthService；统计走 ProjectStatsService 单源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final LabProjectMapper projectMapper;
    private final LabProjectMemberMapper memberMapper;
    private final LabProjectTaskMapper taskMapper;
    private final LabDatasetMapper datasetMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final CourseMapper courseMapper;
    private final CourseStudentMapper courseStudentMapper;
    private final SemesterMapper semesterMapper;
    private final OperationLogMapper operationLogMapper;
    private final ProjectAuthService authService;
    private final ProjectStatsService statsService;
    private final StudentService studentService;

    /** 课题/容器 source 取值 */
    public static final String SOURCE_SYSTEM = "SYSTEM";
    public static final String SOURCE_USER = "USER";

    /** 课题列表视图（含完成度%/逾期数/成员数，同源 stats；source/courseName 供统一列表分段与课程徽标） */
    public record ProjectView(Long id, String projectCode, String projectName, Long teacherId,
                              String teacherName, Long courseId, Long semesterId, String courseName,
                              String source, String description,
                              String status, LocalDateTime createdAt,
                              double projectRate, int overdueCount, int memberCount) {
    }

    /** 容器 ensure 结果（containerCreated 供日志/前端提示区分"新生成"与"已存在"） */
    public record EnsureOutcome(LabProject project, boolean created) {
    }

    /** 成员视图 */
    public record MemberView(Long studentId, String studentNo, String realName, String className,
                             String major, String source, LocalDateTime joinedAt) {
    }

    /**
     * 统一实验项目列表（M-Q4）：TEACHER 仅归属本人、ADMIN 全部、STUDENT 仅所在课题；
     * source 过滤供 Segmented 分段（SYSTEM=课程课题 / USER=我的课题 / 空=全部）。
     * 行内汇总与详情矩阵同源（r2-B1）。
     */
    public List<ProjectView> list(Long userId, String role, String source) {
        LambdaQueryWrapper<LabProject> wrapper = new LambdaQueryWrapper<>();
        if (SecurityUtils.ROLE_STUDENT.equals(role)) {
            Student student = authService.requireStudentByUserId(userId);
            Set<Long> projectIds = memberMapper.selectList(
                            new LambdaQueryWrapper<LabProjectMember>()
                                    .eq(LabProjectMember::getStudentId, student.getId())).stream()
                    .map(LabProjectMember::getProjectId).collect(Collectors.toSet());
            if (projectIds.isEmpty()) {
                return List.of();
            }
            wrapper.in(LabProject::getId, projectIds);
        } else if (SecurityUtils.ROLE_TEACHER.equals(role)) {
            wrapper.eq(LabProject::getTeacherId, userId);
        }
        if (StringUtils.hasText(source)) {
            wrapper.eq(LabProject::getSource, source.trim().toUpperCase());
        }
        wrapper.orderByDesc(LabProject::getCreatedAt);
        List<LabProject> projects = projectMapper.selectList(wrapper);
        if (projects.isEmpty()) {
            return List.of();
        }
        return toViews(projects);
    }

    /** 单个课题视图（详情页头部与列表同一口径：指导教师/成员数/完成度等汇总字段） */
    public ProjectView view(Long projectId, Long userId, String role) {
        LabProject project = authService.requireReadable(projectId, userId, role);
        return toViews(List.of(project)).get(0);
    }

    private List<ProjectView> toViews(List<LabProject> projects) {
        Map<Long, ProjectStatsService.ProjectSummary> summaries = statsService.summaries(
                projects.stream().map(LabProject::getId).collect(Collectors.toList()));
        Map<Long, String> teacherNames = userNames(projects.stream()
                .map(LabProject::getTeacherId).collect(Collectors.toSet()));
        Map<Long, String> courseNames = courseNames(projects.stream()
                .map(LabProject::getCourseId).collect(Collectors.toSet()));
        return projects.stream()
                .map(p -> {
                    ProjectStatsService.ProjectSummary s =
                            summaries.getOrDefault(p.getId(), ProjectStatsService.ProjectSummary.empty());
                    return new ProjectView(p.getId(), p.getProjectCode(), p.getProjectName(),
                            p.getTeacherId(), teacherNames.get(p.getTeacherId()), p.getCourseId(),
                            p.getSemesterId(), courseNames.get(p.getCourseId()), p.getSource(),
                            p.getDescription(), p.getStatus(), p.getCreatedAt(),
                            s.projectRate(), s.overdueCount(), s.memberCount());
                })
                .collect(Collectors.toList());
    }

    public LabProject detail(Long projectId, Long userId, String role) {
        return authService.requireReadable(projectId, userId, role);
    }

    @Transactional
    public LabProject create(ProjectRequest request, Long userId, String role) {
        // 权限边界收口（2026-09-20）：ADMIN 只读，自建课题仅教师可建
        authService.requireNotAdmin(role);
        if (!SecurityUtils.ROLE_TEACHER.equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!StringUtils.hasText(request.getProjectName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "课题名称不能为空");
        }
        LabProject project = new LabProject();
        project.setProjectCode(generateProjectCode());
        project.setProjectName(request.getProjectName().trim());
        project.setTeacherId(userId);
        project.setSource(SOURCE_USER);
        project.setDescription(request.getDescription());
        project.setStatus("ACTIVE");
        projectMapper.insert(project);
        return project;
    }

    @Transactional
    public LabProject update(Long projectId, ProjectRequest request, Long userId, String role) {
        LabProject project = authService.requireOwned(projectId, userId, role);
        authService.requireActive(project);
        if (!StringUtils.hasText(request.getProjectName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "课题名称不能为空");
        }
        LabProject update = new LabProject();
        update.setId(projectId);
        update.setProjectName(request.getProjectName().trim());
        update.setDescription(request.getDescription());
        projectMapper.updateById(update);
        return projectMapper.selectById(projectId);
    }

    // ------------------------------------------------------------ 课程容器（M-Q3/R1）

    /**
     * 懒生成课程容器（幂等 GET-or-create，R1）：TEACHER 限课程归属本人，STUDENT 限课程名单内，
     * ADMIN 只读——命中已有容器原样返回，未生成则 403（容器由师生首次进入时生成，2026-09-20 权限边界收口）。
     * 并发由 uk(course_id, semester_id, source) 兜底——命中已有即原样返回。
     */
    @Transactional
    public EnsureOutcome ensureCourseContainer(Long courseId, Long userId, String role) {
        Course course = requireEnsurePermission(courseId, userId, role);
        LabProject existing = findContainer(course, course.getSemesterId());
        if (existing != null) {
            return new EnsureOutcome(existing, false);
        }
        authService.requireNotAdmin(role);
        return createContainer(course, userId);
    }

    /**
     * 批量 ensure（统一列表页入口，R1）：TEACHER 扫本人当前学期课程，STUDENT 扫课程名单内
     * 当前学期课程；仅对 ACTIVE 学期生效——历史学期容器只显式深链创建，不做被动扫描。
     */
    @Transactional
    public List<LabProject> ensureMyCourseContainers(Long userId, String role) {
        List<Course> courses;
        if (SecurityUtils.ROLE_ADMIN.equals(role)) {
            return List.of();
        } else if (SecurityUtils.ROLE_TEACHER.equals(role)) {
            courses = courseMapper.selectList(
                    new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, userId));
        } else if (SecurityUtils.ROLE_STUDENT.equals(role)) {
            Student student = authService.requireStudentByUserId(userId);
            Set<Long> courseIds = courseStudentMapper.selectList(
                            new LambdaQueryWrapper<CourseStudent>()
                                    .eq(CourseStudent::getStudentId, student.getId())).stream()
                    .map(CourseStudent::getCourseId).collect(Collectors.toSet());
            courses = courseIds.isEmpty() ? List.of() : courseMapper.selectBatchIds(courseIds);
        } else {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        courses = courses.stream()
                .filter(c -> c.getSemesterId() != null)
                .filter(c -> {
                    Semester semester = semesterMapper.selectById(c.getSemesterId());
                    return semester != null && "ACTIVE".equals(semester.getStatus());
                })
                .collect(Collectors.toList());
        List<LabProject> containers = new ArrayList<>();
        for (Course course : courses) {
            LabProject existing = findContainer(course, course.getSemesterId());
            containers.add(existing != null ? existing : createContainer(course, userId).project());
        }
        return containers;
    }

    /** 归档定格（S7）：归属老师；ADMIN 只读（2026-09-20 权限边界收口）；已归档幂等返回；归档后全写接口 403（requireActive）。 */
    @Transactional
    public LabProject archive(Long projectId, Long userId, String role) {
        LabProject project = authService.requireOwned(projectId, userId, role);
        if ("ARCHIVED".equals(project.getStatus())) {
            return project;
        }
        LabProject update = new LabProject();
        update.setId(projectId);
        update.setStatus("ARCHIVED");
        update.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(update);
        return projectMapper.selectById(projectId);
    }

    /**
     * 删除课题：SYSTEM 容器 403（M-Q3 不可删除、可归档）；自建课题仅空壳（无成员/任务/数据）可删。
     */
    @Transactional
    public void delete(Long projectId, Long userId, String role) {
        LabProject project = authService.requireOwned(projectId, userId, role);
        if (SOURCE_SYSTEM.equals(project.getSource())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "课程容器不可删除，可归档留痕");
        }
        long members = memberMapper.selectCount(
                new LambdaQueryWrapper<LabProjectMember>().eq(LabProjectMember::getProjectId, projectId));
        long tasks = taskMapper.selectCount(
                new LambdaQueryWrapper<LabProjectTask>().eq(LabProjectTask::getProjectId, projectId));
        long datasets = datasetMapper.selectCount(
                new LambdaQueryWrapper<LabDataset>().eq(LabDataset::getProjectId, projectId));
        if (members + tasks + datasets > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "课题已含成员/任务/数据，不可删除，可归档留痕");
        }
        projectMapper.deleteById(projectId);
    }

    private Course requireEnsurePermission(Long courseId, Long userId, String role) {
        if (courseId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "课程 ID 不能为空");
        }
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        if (course.getSemesterId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "课程未关联学期，无法生成容器");
        }
        if (course.getTeacherId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "课程未关联教师，无法生成容器");
        }
        if (SecurityUtils.ROLE_ADMIN.equals(role)) {
            // ADMIN 只读：允许定位课程以做"命中已有容器"的只读回退，生成侧由 ensureCourseContainer 拦截
            return course;
        }
        if (SecurityUtils.ROLE_TEACHER.equals(role)) {
            if (!course.getTeacherId().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "仅课程归属教师可生成课程容器");
            }
            return course;
        }
        if (SecurityUtils.ROLE_STUDENT.equals(role)) {
            Student student = authService.requireStudentByUserId(userId);
            long inRoster = courseStudentMapper.selectCount(
                    new LambdaQueryWrapper<CourseStudent>()
                            .eq(CourseStudent::getCourseId, course.getId())
                            .eq(CourseStudent::getStudentId, student.getId()));
            if (inRoster == 0) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "仅课程名单内学生可生成课程容器");
            }
            return course;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private LabProject findContainer(Course course, Long semesterId) {
        return projectMapper.selectOne(new LambdaQueryWrapper<LabProject>()
                .eq(LabProject::getCourseId, course.getId())
                .eq(LabProject::getSemesterId, semesterId)
                .eq(LabProject::getSource, SOURCE_SYSTEM)
                .last("LIMIT 1"));
    }

    /**
     * 并发兜底专用当前读：本事务快照建立在前（REPEATABLE READ），普通 SELECT 看不见并发方刚提交的
     * 容器行，只有 FOR UPDATE 能读到最新已提交版本，从而真正命中撞唯一键的对手。
     */
    private LabProject findContainerCurrent(Course course, Long semesterId) {
        return projectMapper.selectOne(new LambdaQueryWrapper<LabProject>()
                .eq(LabProject::getCourseId, course.getId())
                .eq(LabProject::getSemesterId, semesterId)
                .eq(LabProject::getSource, SOURCE_SYSTEM)
                .last("LIMIT 1 FOR UPDATE"));
    }

    /**
     * 容器落库：编号 max+1（§8.1 编码约定），并发撞号靠 uk_project_code / uk_course_semester_source
     * 兜底——同一容器竞态命中已有即复用（created=false），编号撞号重算重试一次。
     */
    private EnsureOutcome createContainer(Course course, Long userId) {
        for (int attempt = 0; attempt < 2; attempt++) {
            LabProject project = new LabProject();
            project.setProjectCode(generateProjectCode());
            project.setProjectName(course.getCourseName());
            project.setTeacherId(course.getTeacherId());
            project.setCourseId(course.getId());
            project.setSemesterId(course.getSemesterId());
            project.setSource(SOURCE_SYSTEM);
            project.setDescription("系统自动生成的课程容器，承载该课程的实验目录");
            project.setStatus("ACTIVE");
            try {
                projectMapper.insert(project);
                snapshotMembers(project, userId);
                return new EnsureOutcome(project, true);
            } catch (DuplicateKeyException e) {
                LabProject raced = findContainerCurrent(course, course.getSemesterId());
                if (raced != null) {
                    return new EnsureOutcome(raced, false);
                }
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "课程容器生成失败，请重试");
    }

    /** 生成即快照（M-Q6）：课程名单全量带入 source=AUTO_SNAPSHOT，之后增删不回写。 */
    private void snapshotMembers(LabProject container, Long userId) {
        Set<Long> studentIds = courseStudentMapper.selectList(
                        new LambdaQueryWrapper<CourseStudent>()
                                .eq(CourseStudent::getCourseId, container.getCourseId())).stream()
                .map(CourseStudent::getStudentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (Long studentId : studentIds) {
            LabProjectMember member = new LabProjectMember();
            member.setProjectId(container.getId());
            member.setStudentId(studentId);
            member.setSource("AUTO_SNAPSHOT");
            member.setJoinedAt(LocalDateTime.now());
            memberMapper.insert(member);
        }
        writeSnapshotLog(container, userId, studentIds.size());
    }

    /** R1/AC12：快照带入写 operation_log，回答"这些学生哪来的"。 */
    private void writeSnapshotLog(LabProject container, Long userId, int memberCount) {
        try {
            String ip = "";
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                ip = IpUtil.getIpAddress(attributes.getRequest());
            }
            OperationLogEntity record = new OperationLogEntity();
            record.setUserId(userId);
            record.setAction("PROJECT_AUTO_SNAPSHOT");
            record.setTargetType("LAB_PROJECT");
            record.setTargetId(container.getId());
            record.setDetail(JsonUtil.toJson(Map.of(
                    "courseId", container.getCourseId(),
                    "semesterId", container.getSemesterId(),
                    "memberCount", memberCount)));
            record.setIp(ip);
            operationLogMapper.insert(record);
        } catch (Exception e) {
            log.error("Failed to record auto-snapshot log", e);
        }
    }

    /** 成员列表（老师端全字段；学生端同接口同权限=可读成员） */
    public List<MemberView> members(Long projectId, Long userId, String role) {
        authService.requireReadable(projectId, userId, role);
        List<LabProjectMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<LabProjectMember>()
                        .eq(LabProjectMember::getProjectId, projectId)
                        .orderByAsc(LabProjectMember::getId));
        if (members.isEmpty()) {
            return List.of();
        }
        Map<Long, Student> students = studentService.mapByIds(
                members.stream().map(LabProjectMember::getStudentId).collect(Collectors.toSet()));
        Map<Long, User> users = students.isEmpty() ? Map.of() : userMapper.selectBatchIds(
                        students.values().stream().map(Student::getUserId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<MemberView> views = new ArrayList<>();
        for (LabProjectMember m : members) {
            Student s = students.get(m.getStudentId());
            if (s == null) {
                continue;
            }
            User u = users.get(s.getUserId());
            views.add(new MemberView(s.getId(), s.getStudentNo(),
                    u == null ? null : u.getRealName(), s.getClassName(), s.getMajor(),
                    m.getSource(), m.getJoinedAt()));
        }
        return views;
    }

    /**
     * 批量加入成员（学生库勾选，source=MANUAL）；已在组内自动跳过（快照唯一键）。
     */
    @Transactional
    public int addMembers(Long projectId, MemberAddRequest request, Long userId, String role) {
        LabProject project = authService.requireOwned(projectId, userId, role);
        authService.requireActive(project);
        if (request.getStudentIds() == null || request.getStudentIds().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择学生");
        }
        Set<Long> studentIds = new LinkedHashSet<>(request.getStudentIds());
        long exists = studentMapper.selectCount(
                new LambdaQueryWrapper<Student>().in(Student::getId, studentIds));
        if (exists != studentIds.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存在无效学生 ID");
        }
        Set<Long> already = memberMapper.selectList(
                        new LambdaQueryWrapper<LabProjectMember>()
                                .eq(LabProjectMember::getProjectId, projectId)
                                .in(LabProjectMember::getStudentId, studentIds)).stream()
                .map(LabProjectMember::getStudentId).collect(Collectors.toSet());
        int added = 0;
        for (Long studentId : studentIds) {
            if (already.contains(studentId)) {
                continue;
            }
            LabProjectMember member = new LabProjectMember();
            member.setProjectId(projectId);
            member.setStudentId(studentId);
            member.setSource("MANUAL");
            member.setJoinedAt(LocalDateTime.now());
            memberMapper.insert(member);
            added++;
        }
        return added;
    }

    /**
     * 项目内导入学生名单 Excel：陌生学号自动建档（默认密码，source=CREATE）并带入成员；
     * 已在组内的学号幂等跳过（快照唯一键）。
     */
    @Transactional
    public StudentService.ImportResult importMembers(Long projectId, MultipartFile file,
                                                     Long userId, String role) throws IOException {
        LabProject project = authService.requireOwned(projectId, userId, role);
        authService.requireActive(project);
        List<StudentService.ImportRow> rows = readImportRows(file);
        StudentService.ImportResult result = studentService.importRows(rows);
        for (StudentService.ImportRow row : rows) {
            if (!StringUtils.hasText(row.studentNo()) || !StringUtils.hasText(row.realName())) {
                continue;
            }
            Student student = studentMapper.selectOne(
                    new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, row.studentNo()));
            if (student == null) {
                continue; // 失败行（如撞号）不建档
            }
            if (!authService.isMember(projectId, student.getId())) {
                LabProjectMember member = new LabProjectMember();
                member.setProjectId(projectId);
                member.setStudentId(student.getId());
                member.setSource("CREATE");
                member.setJoinedAt(LocalDateTime.now());
                memberMapper.insert(member);
            }
        }
        return result;
    }

    /**
     * 移出成员：其未完成任务（未开始/进行中/已驳回）置 CLOSED 留痕，
     * 已提交/已验收不动（r2-B1 分母口径：CLOSED 剔除分母、ACCEPTED 永久计入）。
     */
    @Transactional
    public void removeMember(Long projectId, Long studentId, Long userId, String role) {
        LabProject project = authService.requireOwned(projectId, userId, role);
        authService.requireActive(project);
        int deleted = memberMapper.delete(
                new LambdaQueryWrapper<LabProjectMember>()
                        .eq(LabProjectMember::getProjectId, projectId)
                        .eq(LabProjectMember::getStudentId, studentId));
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该学生不在课题成员中");
        }
        LabProjectTask update = new LabProjectTask();
        update.setStatus("CLOSED");
        update.setUpdatedAt(LocalDateTime.now());
        taskMapper.update(update, new LambdaQueryWrapper<LabProjectTask>()
                .eq(LabProjectTask::getProjectId, projectId)
                .eq(LabProjectTask::getAssigneeStudentId, studentId)
                .in(LabProjectTask::getStatus, "NOT_STARTED", "IN_PROGRESS", "REJECTED"));
    }

    private Map<Long, String> userNames(Set<Long> userIds) {
        Set<Long> ids = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            // 与 courseNames 同款：toViews 会以行上教师 ID 查此表，Map.of() 对 null 键抛 NPE
            return new HashMap<>();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername()));
    }

    private Map<Long, String> courseNames(Set<Long> courseIds) {
        Set<Long> ids = courseIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            // 独立课题 courseId 为 null，toViews 会以 null 键查此表；Map.of() 对 null 键抛 NPE
            return new HashMap<>();
        }
        return courseMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Course::getId, Course::getCourseName));
    }

    /**
     * 课题编号 LP-YYYY-NNN：取当年已有编号最大流水 +1（§8.1 编码约定；存量迁移的
     * S 前缀编号不参与流水计算），并发撞号由 uk_project_code 兜底、调用方重试。
     */
    private String generateProjectCode() {
        String year = String.valueOf(LocalDate.now().getYear());
        String prefix = "LP-" + year + "-";
        List<LabProject> rows = projectMapper.selectList(new LambdaQueryWrapper<LabProject>()
                .select(LabProject::getProjectCode)
                .likeRight(LabProject::getProjectCode, prefix));
        int max = 0;
        for (LabProject row : rows) {
            String suffix = row.getProjectCode().substring(prefix.length());
            if (suffix.chars().allMatch(Character::isDigit)) {
                max = Math.max(max, Integer.parseInt(suffix));
            }
        }
        return prefix + String.format("%03d", max + 1);
    }

    private List<StudentService.ImportRow> readImportRows(MultipartFile file) throws IOException {
        return studentService.readImportRows(file);
    }
}
