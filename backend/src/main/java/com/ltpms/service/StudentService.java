package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.entity.LabProject;
import com.ltpms.entity.LabProjectMember;
import com.ltpms.entity.Student;
import com.ltpms.entity.User;
import com.ltpms.mapper.LabProjectMapper;
import com.ltpms.mapper.LabProjectMemberMapper;
import com.ltpms.mapper.StudentMapper;
import com.ltpms.mapper.UserMapper;
import com.ltpms.util.ExcelUtil;
import com.ltpms.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学生档案服务：Excel 批量建档（陌生学号自动建 STUDENT 账号）、检索、教师端重置密码。
 * 学生账号仅由教师端产生（Q1），登录名=学号，默认密码 Ltpms@学号，首登强制改密。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final LabProjectMapper projectMapper;
    private final LabProjectMemberMapper memberMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    /** 导入结果：新行=学号/姓名/班级/专业 */
    public record ImportRow(int row, String studentNo, String realName, String className, String major) {
    }

    /** 导入回执 */
    public record ImportResult(int successCount, int createdAccounts, List<RowError> errors) {
    }

    public record RowError(int row, String reason) {
    }

    /** 学生检索视图 */
    public record StudentView(Long id, Long userId, String studentNo, String realName,
                              String className, String major, Integer status) {
    }

    /**
     * 从 Excel 批量建档/更新学生（列：学号/姓名/班级/专业）。
     * 学号已被非 STUDENT 用户占用（工号撞学号）→ 该行拒绝，不触碰既有账号（r2-C2）。
     */
    @Transactional
    public ImportResult importFromExcel(MultipartFile file) throws IOException {
        return importRows(readImportRows(file));
    }

    /** 逐行建档：同事务供课题内导入复用 */
    @Transactional
    public ImportResult importRows(List<ImportRow> rows) {
        List<RowError> errors = new ArrayList<>();
        int success = 0;
        int created = 0;
        Set<String> seenInFile = new HashSet<>();

        for (ImportRow row : rows) {
            if (!StringUtils.hasText(row.studentNo()) || !StringUtils.hasText(row.realName())) {
                errors.add(new RowError(row.row(), "学号和姓名不能为空"));
                continue;
            }
            if (!seenInFile.add(row.studentNo())) {
                errors.add(new RowError(row.row(), "文件内学号重复：" + row.studentNo()));
                continue;
            }
            try {
                UpsertOutcome outcome = upsertStudent(row);
                success++;
                if (outcome.created()) {
                    created++;
                }
            } catch (BusinessException e) {
                errors.add(new RowError(row.row(), e.getMessage()));
            }
        }
        return new ImportResult(success, created, errors);
    }

    /** upsert 结果：档案 + 本次是否新建账号 */
    public record UpsertOutcome(Student student, boolean created) {
    }

    /**
     * 学号 upsert：已有 STUDENT 档案 → 更新班级/专业（不重建账号）；
     * 撞非 STUDENT username → 拒绝（r2-C2）。
     */
    @Transactional
    public UpsertOutcome upsertStudent(ImportRow row) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, row.studentNo()));
        Student student = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, row.studentNo()));
        if (user != null && !SecurityUtils.ROLE_STUDENT.equals(user.getRole())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "学号与既有账号冲突：" + row.studentNo());
        }
        if (student == null) {
            boolean created = false;
            if (user == null) {
                user = new User();
                user.setUsername(row.studentNo());
                user.setRealName(row.realName());
                user.setPassword(passwordEncoder.encode(defaultPassword(row.studentNo())));
                user.setRole(SecurityUtils.ROLE_STUDENT);
                user.setStatus(1);
                user.setIsDefaultPwd(1);
                user.setFailedAttempts(0);
                userMapper.insert(user);
                created = true;
            } else if (!StringUtils.hasText(user.getRealName())) {
                User rename = new User();
                rename.setId(user.getId());
                rename.setRealName(row.realName());
                userMapper.updateById(rename);
                created = true;
            }
            student = new Student();
            student.setUserId(user.getId());
            student.setStudentNo(row.studentNo());
            student.setClassName(row.className());
            student.setMajor(row.major());
            studentMapper.insert(student);
            return new UpsertOutcome(student, created);
        }
        Student update = new Student();
        update.setId(student.getId());
        if (StringUtils.hasText(row.className())) {
            update.setClassName(row.className());
        }
        if (StringUtils.hasText(row.major())) {
            update.setMajor(row.major());
        }
        studentMapper.updateById(update);
        return new UpsertOutcome(student, false);
    }

    /** 手动添加单名学生（教师输入学号+姓名） */
    @Transactional
    public Student addManual(String studentNo, String realName, String className, String major) {
        return upsertStudent(new ImportRow(0, studentNo, realName, className, major)).student();
    }

    /** 学生检索：关键字命中 学号/姓名，班级精确过滤 */
    public List<StudentView> search(String keyword, String className) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(className)) {
            wrapper.eq(Student::getClassName, className);
        }
        List<Student> students = studentMapper.selectList(wrapper.orderByAsc(Student::getStudentNo));
        if (students.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userMapper.selectBatchIds(
                        students.stream().map(Student::getUserId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<StudentView> views = new ArrayList<>();
        for (Student s : students) {
            User u = users.get(s.getUserId());
            if (u == null) {
                continue;
            }
            if (StringUtils.hasText(keyword) && !containsEither(u, keyword)) {
                continue;
            }
            views.add(new StudentView(s.getId(), s.getUserId(), s.getStudentNo(),
                    u.getRealName(), s.getClassName(), s.getMajor(), u.getStatus()));
        }
        return views;
    }

    private boolean containsEither(User u, String keyword) {
        return (u.getUsername() != null && u.getUsername().contains(keyword))
                || (u.getRealName() != null && u.getRealName().contains(keyword));
    }

    /**
     * 重置学生密码为默认密码（r2-C3）：仅该生任一在管课题的老师或 ADMIN。
     * 留痕由控制器 @OperationLog 切面写 operation_log。
     */
    @Transactional
    public void resetPassword(Long studentId, Long operatorId, String operatorRole) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学生不存在");
        }
        if (!SecurityUtils.ROLE_ADMIN.equals(operatorRole)) {
            if (!SecurityUtils.ROLE_TEACHER.equals(operatorRole) || !isManagingTeacher(studentId, operatorId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "仅该生所参与课题的在管老师或管理员可重置");
            }
        }
        User update = new User();
        update.setId(student.getUserId());
        update.setPassword(passwordEncoder.encode(defaultPassword(student.getStudentNo())));
        update.setIsDefaultPwd(1);
        update.setFailedAttempts(0);
        update.setLockedUntil(LocalDateTime.now().minusSeconds(1));
        userMapper.updateById(update);
        log.info("Student {} password reset by operator {}", student.getStudentNo(), operatorId);
    }

    /** 该生是否属于 operator 名下任一课题（在管） */
    public boolean isManagingTeacher(Long studentId, Long teacherUserId) {
        List<LabProjectMember> memberRows = memberMapper.selectList(
                new LambdaQueryWrapper<LabProjectMember>().eq(LabProjectMember::getStudentId, studentId));
        if (memberRows.isEmpty()) {
            return false;
        }
        Set<Long> projectIds = memberRows.stream()
                .map(LabProjectMember::getProjectId).collect(Collectors.toSet());
        return projectMapper.selectCount(
                new LambdaQueryWrapper<LabProject>()
                        .in(LabProject::getId, projectIds)
                        .eq(LabProject::getTeacherId, teacherUserId)) > 0;
    }

    /** userId → Student 映射（任务/成员列表补姓名用） */
    public Map<Long, Student> mapByIds(Collection<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Map.of();
        }
        return studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, s -> s));
    }

    /** studentId → 显示名（真实姓名，回退登录名） */
    public Map<Long, String> namesByIds(Collection<Long> studentIds) {
        Map<Long, Student> students = mapByIds(studentIds);
        if (students.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        Map<Long, User> users = userMapper.selectBatchIds(
                        students.values().stream().map(Student::getUserId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        students.forEach((id, s) -> {
            User u = users.get(s.getUserId());
            names.put(id, u == null ? "学生#" + id
                    : (StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername()));
        });
        return names;
    }

    public static String defaultPassword(String studentNo) {
        return "Ltpms@" + studentNo;
    }

    /** 读取导入 Excel 行（列：学号/姓名/班级/专业），供本服务与课题内导入复用 */
    public List<ImportRow> readImportRows(MultipartFile file) throws IOException {
        List<ImportRow> rows = new ArrayList<>();
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                rows.add(new ImportRow(i + 1,
                        ExcelUtil.getStringValue(row.getCell(0)),
                        ExcelUtil.getStringValue(row.getCell(1)),
                        ExcelUtil.getStringValue(row.getCell(2)),
                        ExcelUtil.getStringValue(row.getCell(3))));
            }
        }
        return rows;
    }
}
