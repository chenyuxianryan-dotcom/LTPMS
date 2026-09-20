package com.ltpms.controller;

import com.ltpms.annotation.OperationLog;
import com.ltpms.annotation.RequireRole;
import com.ltpms.common.Result;
import com.ltpms.service.StudentService;
import com.ltpms.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 学生档案接口（TEACHER/ADMIN）：批量建档导入、检索、在管老师重置密码（r2-C3）。
 */
@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    /**
     * Excel 批量建档（列：学号/姓名/班级/专业）；陌生学号自动建账号（默认密码 Ltpms@学号），
     * 学号撞非 STUDENT username 的行拒绝（r2-C2）。
     */
    @PostMapping("/import")
    @RequireRole({"ADMIN", "TEACHER"})
    @OperationLog(action = "STUDENT_IMPORT", targetType = "STUDENT")
    public Result<StudentService.ImportResult> importStudents(@RequestParam("file") MultipartFile file)
            throws IOException {
        return Result.success(studentService.importFromExcel(file));
    }

    /** 学生检索：keyword 命中学号/姓名，className 精确过滤 */
    @GetMapping
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<List<StudentService.StudentView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String className) {
        return Result.success(studentService.search(keyword, className));
    }

    /** 重置学生密码为默认密码：仅该生任一在管课题老师或 ADMIN（r2-C3），切面写 operation_log 留痕 */
    @PostMapping("/{id}/reset-password")
    @OperationLog(action = "STUDENT_RESET_PASSWORD", targetType = "STUDENT")
    public Result<Void> resetPassword(@PathVariable Long id) {
        studentService.resetPassword(id, SecurityUtils.currentUserId(), SecurityUtils.currentRole());
        return Result.success();
    }
}
