package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.entity.LabProject;
import com.ltpms.entity.LabProjectMember;
import com.ltpms.entity.Student;
import com.ltpms.mapper.LabProjectMapper;
import com.ltpms.mapper.LabProjectMemberMapper;
import com.ltpms.mapper.StudentMapper;
import com.ltpms.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 课题模块行级权限唯一入口（技术方案 §4 硬红线 H2）：
 * 读侧 ADMIN 直通 / TEACHER 比 teacher_id / STUDENT 查 lab_project_member；
 * 写侧仅归属教师——ADMIN 全局只读（2026-09-20 权限边界收口：管理员可浏览，不可增删改验收）。
 * 本模块所有端点的可见/可写判定必须经由此类，禁止在业务 service 内再复制 if-else。
 */
@Service
@RequiredArgsConstructor
public class ProjectAuthService {

    private final LabProjectMapper projectMapper;
    private final LabProjectMemberMapper memberMapper;
    private final StudentMapper studentMapper;

    /** 取课题，不存在抛 404 */
    public LabProject getProjectOrThrow(Long projectId) {
        LabProject project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课题不存在");
        }
        return project;
    }

    /**
     * 读权限：ADMIN / 归属教师 / 在组成员 之一，否则 403。
     */
    public LabProject requireReadable(Long projectId, Long userId, String role) {
        LabProject project = getProjectOrThrow(projectId);
        return requireReadable(project, userId, role);
    }

    public LabProject requireReadable(LabProject project, Long userId, String role) {
        if (SecurityUtils.ROLE_ADMIN.equals(role)) {
            return project;
        }
        if (SecurityUtils.ROLE_TEACHER.equals(role)) {
            if (!project.getTeacherId().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            return project;
        }
        if (SecurityUtils.ROLE_STUDENT.equals(role)) {
            Student student = requireStudentByUserId(userId);
            if (!isMember(project.getId(), student.getId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            return project;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    /**
     * 写权限（建任务/验收/改课题/加移成员等）：仅归属教师。
     * ADMIN 只读——可经 requireReadable 浏览任意课题，但一切写操作 403。
     */
    public LabProject requireOwned(Long projectId, Long userId, String role) {
        return requireOwned(getProjectOrThrow(projectId), userId, role);
    }

    public LabProject requireOwned(LabProject project, Long userId, String role) {
        requireNotAdmin(role);
        if (SecurityUtils.ROLE_TEACHER.equals(role) && project.getTeacherId().equals(userId)) {
            return project;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    /**
     * 写侧角色闸门（2026-09-20 权限边界收口）：ADMIN 在实验项目模块全局只读。
     * 供"成员可写"类路径使用（如数据集全员可传新版本——学生允许、管理员仍拒绝）。
     */
    public void requireNotAdmin(String role) {
        if (SecurityUtils.ROLE_ADMIN.equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "管理员为只读视角，不可执行该操作");
        }
    }

    /** 学生视角：由登录用户 ID 取学生档案 */
    public Student requireStudentByUserId(Long userId) {
        Student student = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getUserId, userId));
        if (student == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "学生档案不存在，请联系教师");
        }
        return student;
    }

    public boolean isMember(Long projectId, Long studentId) {
        return memberMapper.selectCount(
                new LambdaQueryWrapper<LabProjectMember>()
                        .eq(LabProjectMember::getProjectId, projectId)
                        .eq(LabProjectMember::getStudentId, studentId)) > 0;
    }

    /** 归档课题全写接口 403（S7 批次三启用归档动作，本校验先行收口） */
    public void requireActive(LabProject project) {
        if ("ARCHIVED".equals(project.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "课题已归档，只读不可修改");
        }
    }
}
