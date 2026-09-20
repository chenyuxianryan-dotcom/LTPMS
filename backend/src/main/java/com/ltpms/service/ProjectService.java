package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.dto.project.MemberAddRequest;
import com.ltpms.dto.project.ProjectRequest;
import com.ltpms.entity.LabProject;
import com.ltpms.entity.LabProjectMember;
import com.ltpms.entity.LabProjectTask;
import com.ltpms.entity.Student;
import com.ltpms.entity.User;
import com.ltpms.mapper.LabProjectMapper;
import com.ltpms.mapper.LabProjectMemberMapper;
import com.ltpms.mapper.LabProjectTaskMapper;
import com.ltpms.mapper.StudentMapper;
import com.ltpms.mapper.UserMapper;
import com.ltpms.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final ProjectAuthService authService;
    private final ProjectStatsService statsService;
    private final StudentService studentService;

    /** 课题列表视图（含完成度%/逾期数/成员数，同源 stats） */
    public record ProjectView(Long id, String projectCode, String projectName, Long teacherId,
                              String teacherName, Long courseId, Long semesterId, String description,
                              String status, LocalDateTime createdAt,
                              double projectRate, int overdueCount, int memberCount) {
    }

    /** 成员视图 */
    public record MemberView(Long studentId, String studentNo, String realName, String className,
                             String major, String source, LocalDateTime joinedAt) {
    }

    /**
     * 我的课题列表：TEACHER 仅归属本人，ADMIN 全部；行内汇总与详情矩阵同源（r2-B1）。
     */
    public List<ProjectView> list(Long userId, String role) {
        LambdaQueryWrapper<LabProject> wrapper = new LambdaQueryWrapper<>();
        if (SecurityUtils.ROLE_TEACHER.equals(role)) {
            wrapper.eq(LabProject::getTeacherId, userId);
        }
        wrapper.orderByDesc(LabProject::getCreatedAt);
        List<LabProject> projects = projectMapper.selectList(wrapper);
        if (projects.isEmpty()) {
            return List.of();
        }
        Map<Long, ProjectStatsService.ProjectSummary> summaries = statsService.summaries(
                projects.stream().map(LabProject::getId).collect(Collectors.toList()));
        Map<Long, String> teacherNames = userNames(projects.stream()
                .map(LabProject::getTeacherId).collect(Collectors.toSet()));
        return projects.stream()
                .map(p -> {
                    ProjectStatsService.ProjectSummary s =
                            summaries.getOrDefault(p.getId(), ProjectStatsService.ProjectSummary.empty());
                    return new ProjectView(p.getId(), p.getProjectCode(), p.getProjectName(),
                            p.getTeacherId(), teacherNames.get(p.getTeacherId()), p.getCourseId(),
                            p.getSemesterId(), p.getDescription(), p.getStatus(), p.getCreatedAt(),
                            s.projectRate(), s.overdueCount(), s.memberCount());
                })
                .collect(Collectors.toList());
    }

    public LabProject detail(Long projectId, Long userId, String role) {
        return authService.requireReadable(projectId, userId, role);
    }

    @Transactional
    public LabProject create(ProjectRequest request, Long userId, String role) {
        if (!SecurityUtils.ROLE_TEACHER.equals(role) && !SecurityUtils.ROLE_ADMIN.equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!StringUtils.hasText(request.getProjectName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "课题名称不能为空");
        }
        LabProject project = new LabProject();
        project.setProjectCode(generateCode());
        project.setProjectName(request.getProjectName().trim());
        project.setTeacherId(userId);
        project.setCourseId(request.getCourseId());
        project.setSemesterId(request.getSemesterId());
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
        update.setCourseId(request.getCourseId());
        update.setSemesterId(request.getSemesterId());
        projectMapper.updateById(update);
        return projectMapper.selectById(projectId);
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
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername()));
    }

    /** 课题编号 LP-YYYY-NNN：按当年序号递增（唯一键兜底并发） */
    private String generateCode() {
        String year = String.valueOf(LocalDate.now().getYear());
        String prefix = "LP-" + year + "-";
        long count = projectMapper.selectCount(
                new LambdaQueryWrapper<LabProject>().likeRight(LabProject::getProjectCode, prefix));
        return prefix + String.format("%03d", count + 1);
    }

    private List<StudentService.ImportRow> readImportRows(MultipartFile file) throws IOException {
        return studentService.readImportRows(file);
    }
}
