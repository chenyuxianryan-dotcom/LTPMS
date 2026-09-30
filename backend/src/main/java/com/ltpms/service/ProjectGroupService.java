package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.dto.project.GroupCreateRequest;
import com.ltpms.dto.project.GroupMembersRequest;
import com.ltpms.entity.Experiment;
import com.ltpms.entity.LabProject;
import com.ltpms.entity.LabProjectGroup;
import com.ltpms.entity.LabProjectGroupMember;
import com.ltpms.entity.LabProjectMember;
import com.ltpms.entity.Student;
import com.ltpms.mapper.ExperimentMapper;
import com.ltpms.mapper.LabProjectGroupMapper;
import com.ltpms.mapper.LabProjectGroupMemberMapper;
import com.ltpms.mapper.LabProjectMemberMapper;
import com.ltpms.mapper.StudentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课题实验分组服务：分组按「每个实验各一套」建模，每组人数唯一事实源为 experiment.group_size
 * （只读目录、不接受手填）。权限判定单路收口 ProjectAuthService（H2 红线）；
 * 目录基准变更后只做不一致提示，重排需教师显式触发（覆盖式，会清空该实验现有分组关系）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectGroupService {

    private final LabProjectGroupMapper groupMapper;
    private final LabProjectGroupMemberMapper groupMemberMapper;
    private final LabProjectMemberMapper memberMapper;
    private final StudentMapper studentMapper;
    private final ExperimentMapper experimentMapper;
    private final ProjectAuthService authService;

    /** 单组视图（成员只给 studentId 列表，姓名由前端用成员表映射） */
    public record GroupView(Long id, String groupName, Integer baselineSize, List<Long> studentIds) {
    }

    /**
     * 某实验的分组全貌。baselineSize 实时读目录；
     * mismatch=任一组建组基准与当前目录不符 或 该组人数超基准；
     * 应分组数由当前目录基准与成员总数派生，供前端「按新基准重排」文案取数。
     */
    public record GroupPlan(Long experimentId, String experimentCode, String experimentName,
                            int baselineSize, int memberCount, int groupedCount, int ungroupedCount,
                            int actualGroupCount, int expectedGroupCount, boolean mismatch,
                            List<GroupView> groups) {
    }

    // ---------------------------------------------------------------- 读

    public GroupPlan plan(Long projectId, Long experimentId, Long userId, String role) {
        LabProject project = authService.requireReadable(projectId, userId, role);
        Experiment experiment = requireCourseExperiment(project, experimentId);
        return buildPlan(project.getId(), experiment);
    }

    // ---------------------------------------------------------------- 写

    /** 一键均分（覆盖式重排）：清空该实验分组关系后按目录每组人数均分，余数摊给前几组 */
    @Transactional
    public GroupPlan autoGroup(Long projectId, Long experimentId, Long userId, String role) {
        LabProject project = authService.requireOwned(projectId, userId, role);
        authService.requireActive(project);
        Experiment experiment = requireCourseExperiment(project, experimentId);
        int baseline = requireBaselineSize(experiment);
        List<Student> roster = orderedRoster(project.getId());
        if (roster.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "课题暂无成员，请先添加成员再分组");
        }
        clearExperimentGroups(project.getId(), experiment.getId());

        int groupCount = (int) Math.ceil(roster.size() / (double) baseline);
        int base = roster.size() / groupCount;
        int remainder = roster.size() % groupCount;
        int cursor = 0;
        for (int i = 0; i < groupCount; i++) {
            int size = base + (i < remainder ? 1 : 0);
            LabProjectGroup group = insertGroup(project.getId(), experiment.getId(),
                    "第" + (i + 1) + "组", baseline);
            for (int j = 0; j < size; j++) {
                insertGroupMember(group.getId(), roster.get(cursor++).getId());
            }
        }
        return buildPlan(project.getId(), experiment);
    }

    /** 手动新建空组（自定义组名），基准人数取当前目录值 */
    @Transactional
    public GroupView createGroup(Long projectId, GroupCreateRequest request, Long userId, String role) {
        LabProject project = authService.requireOwned(projectId, userId, role);
        authService.requireActive(project);
        Experiment experiment = requireCourseExperiment(project, request.getExperimentId());
        if (!StringUtils.hasText(request.getGroupName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "组名不能为空");
        }
        String groupName = request.getGroupName().trim();
        if (groupName.length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "组名不能超过 64 字");
        }
        LabProjectGroup group;
        try {
            group = insertGroup(project.getId(), experiment.getId(), groupName, requireBaselineSize(experiment));
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该实验下已存在同名分组");
        }
        return new GroupView(group.getId(), group.getGroupName(), group.getBaselineSize(), List.of());
    }

    /** 删除分组（连带清组内关系，成员退回未分组池） */
    @Transactional
    public void deleteGroup(Long projectId, Long groupId, Long userId, String role) {
        LabProject project = authService.requireOwned(projectId, userId, role);
        authService.requireActive(project);
        LabProjectGroup group = requireGroup(project.getId(), groupId);
        deleteRelationsOfGroups(List.of(group.getId()));
        groupMapper.deleteById(group.getId());
    }

    /** 移入成员：先从同实验其他组摘出（保证同一实验下一人只属一组），再落目标组 */
    @Transactional
    public GroupPlan assignMembers(Long projectId, Long groupId, GroupMembersRequest request,
                                   Long userId, String role) {
        LabProject project = authService.requireOwned(projectId, userId, role);
        authService.requireActive(project);
        LabProjectGroup group = requireGroup(project.getId(), groupId);
        if (request.getStudentIds() == null || request.getStudentIds().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择学生");
        }
        Set<Long> studentIds = new LinkedHashSet<>(request.getStudentIds());
        Set<Long> members = memberStudentIds(project.getId());
        if (!members.containsAll(studentIds)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存在不属于本课题的成员");
        }
        List<Long> siblingIds = groupIdsOfExperiment(project.getId(), group.getExperimentId(), group.getId());
        if (!siblingIds.isEmpty()) {
            groupMemberMapper.delete(new LambdaQueryWrapper<LabProjectGroupMember>()
                    .in(LabProjectGroupMember::getGroupId, siblingIds)
                    .in(LabProjectGroupMember::getStudentId, studentIds));
        }
        Set<Long> already = groupMemberMapper.selectList(new LambdaQueryWrapper<LabProjectGroupMember>()
                        .eq(LabProjectGroupMember::getGroupId, group.getId())
                        .in(LabProjectGroupMember::getStudentId, studentIds)).stream()
                .map(LabProjectGroupMember::getStudentId).collect(Collectors.toSet());
        for (Long studentId : studentIds) {
            if (!already.contains(studentId)) {
                insertGroupMember(group.getId(), studentId);
            }
        }
        return buildPlan(project.getId(), requireExperiment(group.getExperimentId()));
    }

    /** 从组内移出学生（不回退课题成员身份） */
    @Transactional
    public GroupPlan removeMemberFromGroup(Long projectId, Long groupId, Long studentId,
                                           Long userId, String role) {
        LabProject project = authService.requireOwned(projectId, userId, role);
        authService.requireActive(project);
        LabProjectGroup group = requireGroup(project.getId(), groupId);
        int deleted = groupMemberMapper.delete(new LambdaQueryWrapper<LabProjectGroupMember>()
                .eq(LabProjectGroupMember::getGroupId, group.getId())
                .eq(LabProjectGroupMember::getStudentId, studentId));
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该学生不在此分组中");
        }
        return buildPlan(project.getId(), requireExperiment(group.getExperimentId()));
    }

    /** 课题移出成员时清理其在全部实验分组中的关系（ProjectService.removeMember 调用） */
    @Transactional
    public void onMemberRemoved(Long projectId, Long studentId) {
        List<Long> groupIds = groupIdsOfProject(projectId);
        if (groupIds.isEmpty()) {
            return;
        }
        groupMemberMapper.delete(new LambdaQueryWrapper<LabProjectGroupMember>()
                .in(LabProjectGroupMember::getGroupId, groupIds)
                .eq(LabProjectGroupMember::getStudentId, studentId));
    }

    /** 实验删除时级联清理该实验的分组与组内关系（ExperimentService.delete 调用） */
    @Transactional
    public void onExperimentDeleted(Long experimentId) {
        List<Long> groupIds = groupMapper.selectList(new LambdaQueryWrapper<LabProjectGroup>()
                        .eq(LabProjectGroup::getExperimentId, experimentId)).stream()
                .map(LabProjectGroup::getId).collect(Collectors.toList());
        if (groupIds.isEmpty()) {
            return;
        }
        deleteRelationsOfGroups(groupIds);
        groupMapper.delete(new LambdaQueryWrapper<LabProjectGroup>()
                .in(LabProjectGroup::getId, groupIds));
    }

    // ---------------------------------------------------------------- 内部

    private GroupPlan buildPlan(Long projectId, Experiment experiment) {
        int baseline = experiment.getGroupSize() == null || experiment.getGroupSize() < 1
                ? 1 : experiment.getGroupSize();
        List<LabProjectGroup> groups = groupMapper.selectList(new LambdaQueryWrapper<LabProjectGroup>()
                .eq(LabProjectGroup::getProjectId, projectId)
                .eq(LabProjectGroup::getExperimentId, experiment.getId())
                .orderByAsc(LabProjectGroup::getId));
        Map<Long, List<Long>> membersByGroup = relationsOfGroups(groups.stream()
                .map(LabProjectGroup::getId).collect(Collectors.toList())).stream()
                .collect(Collectors.groupingBy(LabProjectGroupMember::getGroupId,
                        Collectors.mapping(LabProjectGroupMember::getStudentId, Collectors.toList())));
        Set<Long> members = memberStudentIds(projectId);

        List<GroupView> views = new ArrayList<>();
        boolean mismatch = false;
        int grouped = 0;
        for (LabProjectGroup group : groups) {
            // 只统计仍在课题成员池内的关系（防历史野行虚增已分组人数）
            List<Long> studentIds = membersByGroup.getOrDefault(group.getId(), List.of()).stream()
                    .filter(members::contains).collect(Collectors.toList());
            grouped += studentIds.size();
            if (!Objects.equals(group.getBaselineSize(), baseline) || studentIds.size() > baseline) {
                mismatch = true;
            }
            views.add(new GroupView(group.getId(), group.getGroupName(), group.getBaselineSize(), studentIds));
        }
        int memberCount = members.size();
        return new GroupPlan(experiment.getId(), experiment.getExperimentCode(), experiment.getExperimentName(),
                baseline, memberCount, grouped, memberCount - grouped, groups.size(),
                (int) Math.ceil(memberCount / (double) baseline), mismatch, views);
    }

    private List<LabProjectGroupMember> relationsOfGroups(List<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return groupMemberMapper.selectList(new LambdaQueryWrapper<LabProjectGroupMember>()
                .in(LabProjectGroupMember::getGroupId, groupIds)
                .orderByAsc(LabProjectGroupMember::getId));
    }

    private void deleteRelationsOfGroups(List<Long> groupIds) {
        groupMemberMapper.delete(new LambdaQueryWrapper<LabProjectGroupMember>()
                .in(LabProjectGroupMember::getGroupId, groupIds));
    }

    private List<Long> groupIdsOfProject(Long projectId) {
        return groupMapper.selectList(new LambdaQueryWrapper<LabProjectGroup>()
                        .eq(LabProjectGroup::getProjectId, projectId)).stream()
                .map(LabProjectGroup::getId).collect(Collectors.toList());
    }

    /** 同一实验下除 excludeGroupId 以外的组 ID */
    private List<Long> groupIdsOfExperiment(Long projectId, Long experimentId, Long excludeGroupId) {
        return groupMapper.selectList(new LambdaQueryWrapper<LabProjectGroup>()
                        .eq(LabProjectGroup::getProjectId, projectId)
                        .eq(LabProjectGroup::getExperimentId, experimentId)
                        .ne(LabProjectGroup::getId, excludeGroupId)).stream()
                .map(LabProjectGroup::getId).collect(Collectors.toList());
    }

    private void clearExperimentGroups(Long projectId, Long experimentId) {
        List<Long> groupIds = groupMapper.selectList(new LambdaQueryWrapper<LabProjectGroup>()
                        .eq(LabProjectGroup::getProjectId, projectId)
                        .eq(LabProjectGroup::getExperimentId, experimentId)).stream()
                .map(LabProjectGroup::getId).collect(Collectors.toList());
        if (groupIds.isEmpty()) {
            return;
        }
        deleteRelationsOfGroups(groupIds);
        groupMapper.delete(new LambdaQueryWrapper<LabProjectGroup>()
                .in(LabProjectGroup::getId, groupIds));
    }

    private LabProjectGroup insertGroup(Long projectId, Long experimentId, String groupName, int baselineSize) {
        LabProjectGroup group = new LabProjectGroup();
        group.setProjectId(projectId);
        group.setExperimentId(experimentId);
        group.setGroupName(groupName);
        group.setBaselineSize(baselineSize);
        groupMapper.insert(group);
        return group;
    }

    private void insertGroupMember(Long groupId, Long studentId) {
        LabProjectGroupMember relation = new LabProjectGroupMember();
        relation.setGroupId(groupId);
        relation.setStudentId(studentId);
        groupMemberMapper.insert(relation);
    }

    /** 课题成员池（分组候选人 = lab_project_member，与任务派发同一口径） */
    private Set<Long> memberStudentIds(Long projectId) {
        return memberMapper.selectList(new LambdaQueryWrapper<LabProjectMember>()
                        .eq(LabProjectMember::getProjectId, projectId)).stream()
                .map(LabProjectMember::getStudentId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 均分顺序：学号升序（学号缺失排末位），保证同一次重排结果可复现 */
    private List<Student> orderedRoster(Long projectId) {
        Set<Long> studentIds = memberStudentIds(projectId);
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return studentMapper.selectBatchIds(studentIds).stream()
                .sorted(Comparator.comparing(Student::getStudentNo,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    private Experiment requireCourseExperiment(LabProject project, Long experimentId) {
        if (project.getCourseId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "独立课题无实验目录，不支持分组");
        }
        Experiment experiment = requireExperiment(experimentId);
        if (!project.getCourseId().equals(experiment.getCourseId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "实验项目不属于本课题课程");
        }
        return experiment;
    }

    private Experiment requireExperiment(Long experimentId) {
        Experiment experiment = experimentId == null ? null : experimentMapper.selectById(experimentId);
        if (experiment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "实验项目不存在");
        }
        return experiment;
    }

    private int requireBaselineSize(Experiment experiment) {
        Integer size = experiment.getGroupSize();
        if (size == null || size < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该实验未设置有效的每组人数");
        }
        return size;
    }

    private LabProjectGroup requireGroup(Long projectId, Long groupId) {
        LabProjectGroup group = groupId == null ? null : groupMapper.selectById(groupId);
        if (group == null || !projectId.equals(group.getProjectId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分组不存在");
        }
        return group;
    }
}
