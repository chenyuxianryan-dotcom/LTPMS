package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.entity.Experiment;
import com.ltpms.entity.LabProject;
import com.ltpms.entity.LabTaskSubmission;
import com.ltpms.entity.Student;
import com.ltpms.entity.User;
import com.ltpms.mapper.LabTaskSubmissionMapper;
import com.ltpms.mapper.StudentMapper;
import com.ltpms.mapper.UserMapper;
import com.ltpms.service.DatasetService.DatasetView;
import com.ltpms.service.ProjectService.MemberView;
import com.ltpms.service.TaskService.TaskView;
import com.ltpms.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课题导出服务（教学闭环收口 T05/T06）：成绩册与项目台账 Excel。
 *
 * <p>权限：仅归属教师与 ADMIN 可导出（成绩册含学号姓名，学生不可导出全班册）；行级判定单路走
 * {@link ProjectAuthService}。文件下载不进任何 AI 链路，故学号姓名允许落表。</p>
 *
 * <p>口径与页面同源：完成率分母剔除 CLOSED、逾期为派生（{@link ProjectStatsService#isOverdue}）；
 * 数据全部经既有 service（members/tasks/datasets/experiments）取出，杜绝双口径。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectExportService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 任务状态中文标签（与前端 constants TASK_STATUS_MAP 一致） */
    private static final Map<String, String> STATUS_LABELS = Map.of(
            "NOT_STARTED", "待领取",
            "IN_PROGRESS", "进行中",
            "SUBMITTED", "待验收",
            "REJECTED", "被驳回",
            "ACCEPTED", "已验收",
            "CLOSED", "已关闭");

    /** 成员来源中文标签 */
    private static final Map<String, String> MEMBER_SOURCE_LABELS = Map.of(
            "AUTO_SNAPSHOT", "名单快照",
            "MANUAL", "手动加入",
            "CREATE", "导入建档");

    private final ProjectAuthService authService;
    private final ProjectService projectService;
    private final TaskService taskService;
    private final DatasetService datasetService;
    private final ExperimentService experimentService;
    private final ReportService reportService;
    private final LabTaskSubmissionMapper submissionMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;

    // ---------------------------------------------------------------- T05 成绩册

    /**
     * 成绩册：Sheet1 汇总（每生一行）+ Sheet2 明细（每任务一行）。
     */
    public byte[] exportGrades(Long projectId, Long userId, String role) throws IOException {
        LabProject project = requireExportable(projectId, userId, role);
        List<MemberView> members = projectService.members(projectId, userId, role);
        List<TaskView> tasks = taskService.listByProject(projectId, userId, role);
        Map<Long, LabTaskSubmission> latestByTask = latestSubmissionByTask(tasks);

        Map<Long, List<TaskView>> byStudent = tasks.stream()
                .filter(t -> t.assigneeStudentId() != null)
                .collect(Collectors.groupingBy(TaskView::assigneeStudentId,
                        LinkedHashMap::new, Collectors.toList()));

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            CellStyle headerStyle = headerStyle(wb);

            Sheet s1 = wb.createSheet("成绩汇总");
            writeHeader(s1, headerStyle, "姓名", "学号", "任务数", "已验收", "完成率", "逾期", "待验收");
            int r1 = 1;
            for (MemberView m : members) {
                List<TaskView> own = byStudent.getOrDefault(m.studentId(), List.of());
                int total = own.size();
                long accepted = own.stream().filter(t -> "ACCEPTED".equals(t.status())).count();
                long effective = own.stream().filter(t -> !"CLOSED".equals(t.status())).count();
                long overdue = own.stream().filter(TaskView::overdue).count();
                long submitted = own.stream().filter(t -> "SUBMITTED".equals(t.status())).count();
                Row row = s1.createRow(r1++);
                setCell(row, 0, blankTo(m.realName(), "学生#" + m.studentId()));
                setCell(row, 1, m.studentNo());
                row.createCell(2).setCellValue(total);
                row.createCell(3).setCellValue(accepted);
                setCell(row, 4, rate(accepted, effective) + "%");
                row.createCell(5).setCellValue(overdue);
                row.createCell(6).setCellValue(submitted);
            }
            autoSize(s1, 7);

            Sheet s2 = wb.createSheet("任务明细");
            writeHeader(s2, headerStyle, "任务标题", "负责人", "状态", "截止时间", "提交时间", "验收时间", "验收意见");
            int r2 = 1;
            for (TaskView t : tasks) {
                LabTaskSubmission sub = latestByTask.get(t.id());
                Row row = s2.createRow(r2++);
                setCell(row, 0, t.title());
                setCell(row, 1, blankTo(t.assigneeName(), "-"));
                setCell(row, 2, statusLabel(t.status()) + (t.overdue() ? "·逾期" : ""));
                setCell(row, 3, fmt(t.deadline()));
                setCell(row, 4, sub == null ? "-" : fmt(sub.getSubmittedAt()));
                setCell(row, 5, sub == null || sub.getReviewedAt() == null ? "-" : fmt(sub.getReviewedAt()));
                setCell(row, 6, sub == null ? "-" : blankTo(sub.getReviewComment(), "-"));
            }
            autoSize(s2, 7);

            wb.write(bos);
            log.debug("Exported grades for project {} ({} members, {} tasks)",
                    project.getId(), members.size(), tasks.size());
            return bos.toByteArray();
        }
    }

    // ---------------------------------------------------------------- T06 项目台账

    /**
     * 项目台账：成员 / 实验目录 / 任务 / 提交记录 / 数据集登记 五 sheet。
     * 独立课题无实验目录 → 该 sheet 仅表头。
     */
    public byte[] exportLedger(Long projectId, Long userId, String role) throws IOException {
        LabProject project = requireExportable(projectId, userId, role);
        List<MemberView> members = projectService.members(projectId, userId, role);
        List<Experiment> experiments = experimentService.listByContainer(projectId, userId, role);
        List<TaskView> tasks = taskService.listByProject(projectId, userId, role);
        List<DatasetView> datasets = datasetService.listByProject(projectId, userId, role);

        Map<Long, String> taskTitles = tasks.stream()
                .collect(Collectors.toMap(TaskView::id, TaskView::title, (a, b) -> a));
        List<LabTaskSubmission> submissions = listSubmissions(tasks);
        Map<Long, String> submitterNames = studentNames(submissions.stream()
                .map(LabTaskSubmission::getSubmitterStudentId).collect(Collectors.toSet()));

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            CellStyle headerStyle = headerStyle(wb);

            Sheet membersSheet = wb.createSheet("成员");
            writeHeader(membersSheet, headerStyle, "学号", "姓名", "班级", "专业", "来源", "加入时间");
            int rm = 1;
            for (MemberView m : members) {
                Row row = membersSheet.createRow(rm++);
                setCell(row, 0, m.studentNo());
                setCell(row, 1, blankTo(m.realName(), "学生#" + m.studentId()));
                setCell(row, 2, m.className());
                setCell(row, 3, m.major());
                setCell(row, 4, MEMBER_SOURCE_LABELS.getOrDefault(m.source(), m.source()));
                setCell(row, 5, fmt(m.joinedAt()));
            }
            autoSize(membersSheet, 6);

            Sheet expSheet = wb.createSheet("实验目录");
            writeHeader(expSheet, headerStyle, "实验编号", "实验名称", "类别", "类型", "每组人数", "学时");
            int re = 1;
            for (Experiment e : experiments) {
                Row row = expSheet.createRow(re++);
                setCell(row, 0, e.getExperimentCode());
                setCell(row, 1, e.getExperimentName());
                setCell(row, 2, e.getCategory());
                setCell(row, 3, e.getType());
                if (e.getGroupSize() != null) {
                    row.createCell(4).setCellValue(e.getGroupSize());
                } else {
                    setCell(row, 4, "-");
                }
                setCell(row, 5, e.getHours() == null ? "-" : e.getHours().toPlainString());
            }
            autoSize(expSheet, 6);

            Sheet taskSheet = wb.createSheet("任务");
            writeHeader(taskSheet, headerStyle, "任务标题", "负责人", "状态", "关联实验",
                    "截止时间", "关联数据集", "创建时间");
            int rt = 1;
            for (TaskView t : tasks) {
                Row row = taskSheet.createRow(rt++);
                setCell(row, 0, t.title());
                setCell(row, 1, blankTo(t.assigneeName(), "-"));
                setCell(row, 2, statusLabel(t.status()) + (t.overdue() ? "·逾期" : ""));
                setCell(row, 3, blankTo(t.experimentName(), "-"));
                setCell(row, 4, fmt(t.deadline()));
                setCell(row, 5, t.datasetId() == null ? "-" : "#" + t.datasetId());
                setCell(row, 6, fmt(t.createdAt()));
            }
            autoSize(taskSheet, 7);

            Sheet subSheet = wb.createSheet("提交记录");
            writeHeader(subSheet, headerStyle, "任务标题", "提交人", "提交内容", "数据集版本",
                    "提交时间", "验收时间", "验收意见");
            int rs = 1;
            for (LabTaskSubmission s : submissions) {
                Row row = subSheet.createRow(rs++);
                setCell(row, 0, blankTo(taskTitles.get(s.getTaskId()), "任务#" + s.getTaskId()));
                setCell(row, 1, blankTo(submitterNames.get(s.getSubmitterStudentId()),
                        "学生#" + s.getSubmitterStudentId()));
                setCell(row, 2, blankTo(s.getContent(), "-"));
                setCell(row, 3, s.getDatasetVersionId() == null ? "-" : "版本#" + s.getDatasetVersionId());
                setCell(row, 4, fmt(s.getSubmittedAt()));
                setCell(row, 5, s.getReviewedAt() == null ? "-" : fmt(s.getReviewedAt()));
                setCell(row, 6, blankTo(s.getReviewComment(), "-"));
            }
            autoSize(subSheet, 7);

            Sheet dsSheet = wb.createSheet("数据集登记");
            writeHeader(dsSheet, headerStyle, "数据集名称", "描述", "最新版本", "创建人", "创建时间");
            int rd = 1;
            for (DatasetView d : datasets) {
                Row row = dsSheet.createRow(rd++);
                setCell(row, 0, d.datasetName());
                setCell(row, 1, blankTo(d.description(), "-"));
                setCell(row, 2, d.latestVersion() == null ? "-" : "v" + d.latestVersion());
                setCell(row, 3, blankTo(d.createdByName(), "-"));
                setCell(row, 4, fmt(d.createdAt()));
            }
            autoSize(dsSheet, 5);

            wb.write(bos);
            log.debug("Exported ledger for project {} (members={}, experiments={}, tasks={}, submissions={}, datasets={})",
                    project.getId(), members.size(), experiments.size(), tasks.size(),
                    submissions.size(), datasets.size());
            return bos.toByteArray();
        }
    }

    // ---------------------------------------------------------------- 内部工具

    /**
     * 全项目报告 PDF（T09）：六节叙述正文由前端所见即所得提交，后端只排版；
     * 附录「成员完成情况表」（姓名/学号/完成情况）后端从库直出，不经前端、不进模型上下文。
     *
     * @param projectId   课题 ID
     * @param title       文档标题（可空，兜底「实验项目报告 · 课题名」）
     * @param text        六节叙述正文
     * @param generatedAt 数据截至时间（可空）
     * @return PDF 字节
     */
    public byte[] exportReportPdf(Long projectId, String title, String text, String generatedAt,
                                  Long userId, String role) throws IOException {
        LabProject project = requireExportable(projectId, userId, role);
        List<MemberView> members = projectService.members(projectId, userId, role);
        List<TaskView> tasks = taskService.listByProject(projectId, userId, role);
        Map<Long, List<TaskView>> byStudent = tasks.stream()
                .filter(t -> t.assigneeStudentId() != null)
                .collect(Collectors.groupingBy(TaskView::assigneeStudentId));

        List<String[]> appendix = new ArrayList<>();
        for (MemberView m : members) {
            List<TaskView> own = byStudent.getOrDefault(m.studentId(), List.of());
            long accepted = own.stream().filter(t -> "ACCEPTED".equals(t.status())).count();
            long effective = own.stream().filter(t -> !"CLOSED".equals(t.status())).count();
            long overdue = own.stream().filter(TaskView::overdue).count();
            String completion = "任务 " + own.size() + " 项 / 已验收 " + accepted
                    + " / 完成率 " + rate(accepted, effective) + "% / 逾期 " + overdue;
            appendix.add(new String[]{blankTo(m.realName(), "学生#" + m.studentId()), m.studentNo(), completion});
        }

        String reportTitle = StringUtils.hasText(title) ? title : "实验项目报告 · " + project.getProjectName();
        StringBuilder subtitle = new StringBuilder("项目编号 ").append(project.getProjectCode());
        if (StringUtils.hasText(generatedAt)) {
            subtitle.append(" ｜ 数据截至 ").append(generatedAt);
        }
        log.debug("Exported report PDF for project {} ({} appendix rows)", project.getId(), appendix.size());
        return reportService.exportProjectReportPdf(reportTitle, subtitle.toString(), text,
                "附录：成员完成情况表", new String[]{"姓名", "学号", "完成情况"}, appendix);
    }

    /** 导出闸门：仅归属教师 + ADMIN；学生与其他角色 403（行级判定单路走 ProjectAuthService）。 */
    private LabProject requireExportable(Long projectId, Long userId, String role) {
        if (!SecurityUtils.ROLE_ADMIN.equals(role) && !SecurityUtils.ROLE_TEACHER.equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return authService.requireReadable(projectId, userId, role);
    }

    /** 每任务取最新一条提交（按 submittedAt/id 降序），供明细页提交/验收时间列。 */
    private Map<Long, LabTaskSubmission> latestSubmissionByTask(List<TaskView> tasks) {
        Map<Long, LabTaskSubmission> latest = new HashMap<>();
        for (LabTaskSubmission s : listSubmissions(tasks)) {
            LabTaskSubmission cur = latest.get(s.getTaskId());
            if (cur == null || isLater(s, cur)) {
                latest.put(s.getTaskId(), s);
            }
        }
        return latest;
    }

    private List<LabTaskSubmission> listSubmissions(List<TaskView> tasks) {
        Set<Long> taskIds = tasks.stream().map(TaskView::id).collect(Collectors.toSet());
        if (taskIds.isEmpty()) {
            return List.of();
        }
        return submissionMapper.selectList(new LambdaQueryWrapper<LabTaskSubmission>()
                .in(LabTaskSubmission::getTaskId, taskIds)
                .orderByAsc(LabTaskSubmission::getSubmittedAt)
                .orderByAsc(LabTaskSubmission::getId));
    }

    private boolean isLater(LabTaskSubmission a, LabTaskSubmission b) {
        int cmp = nullsLast(a.getSubmittedAt()).compareTo(nullsLast(b.getSubmittedAt()));
        return cmp != 0 ? cmp > 0 : a.getId() > b.getId();
    }

    private static LocalDateTime nullsLast(LocalDateTime t) {
        return t == null ? LocalDateTime.MIN : t;
    }

    private Map<Long, String> studentNames(Collection<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Student> students = studentMapper.selectBatchIds(studentIds);
        if (students.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> toUser = students.stream()
                .collect(Collectors.toMap(Student::getId, Student::getUserId));
        Map<Long, User> users = userMapper.selectBatchIds(toUser.values()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, String> names = new HashMap<>();
        toUser.forEach((sid, uid) -> {
            User u = users.get(uid);
            names.put(sid, u == null ? "学生#" + sid
                    : (StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername()));
        });
        return names;
    }

    private static String statusLabel(String status) {
        return STATUS_LABELS.getOrDefault(status, status);
    }

    /** 完成率百分比数值（分母剔除 CLOSED，除零保护 0）——与 ProjectStatsService.rate 同口径（保留两位小数）。 */
    private static double rate(long accepted, long effective) {
        return effective == 0 ? 0d : Math.round(accepted * 10000.0 / effective) / 100.0;
    }

    private static CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static void writeHeader(Sheet sheet, CellStyle style, String... titles) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < titles.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(titles[i]);
            cell.setCellStyle(style);
        }
    }

    private static void setCell(Row row, int col, String value) {
        row.createCell(col).setCellValue(value == null ? "" : value);
    }

    private static void autoSize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
            int width = Math.min(sheet.getColumnWidth(i) + 512, 255 * 256);
            sheet.setColumnWidth(i, width);
        }
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? "-" : t.format(DT);
    }

    private static String blankTo(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
