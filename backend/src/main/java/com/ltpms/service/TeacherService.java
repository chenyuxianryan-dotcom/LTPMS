package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.common.PageResult;
import com.ltpms.entity.User;
import com.ltpms.mapper.UserMapper;
import com.ltpms.util.ExcelUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 教师（用户）服务，提供教师分页查询、密码重置、状态变更、Excel 导入与模板下载等能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * 分页查询教师（用户）列表，支持按工号或姓名关键字模糊搜索。
     *
     * @param page    页码，从 1 开始
     * @param size    每页大小
     * @param keyword 关键字，可选
     * @return 分页后的教师列表
     */
    public PageResult<User> list(int page, int size, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getRole, "TEACHER");
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or()
                    .like(User::getRealName, keyword));
        }
        wrapper.orderByDesc(User::getCreatedAt);
        Page<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result);
    }

    /**
     * 将指定教师的密码重置为默认密码（Ltpms@工号）。
     *
     * @param id 教师（用户）ID
     * @throws BusinessException 当教师不存在时抛出
     */
    @Transactional
    public void resetPassword(Long id) {
        User user = userMapper.selectById(id);
        if (user == null || !"TEACHER".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "教师不存在");
        }
        String defaultPwd = "Ltpms@" + user.getUsername();
        User update = new User();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(defaultPwd));
        update.setIsDefaultPwd(1);
        userMapper.updateById(update);
    }

    /**
     * 更新指定教师的状态（启用/禁用）。
     *
     * @param id     教师（用户）ID
     * @param status 目标状态，1 启用，0 禁用
     * @throws BusinessException 当教师不存在时抛出
     */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null || !"TEACHER".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "教师不存在");
        }
        User update = new User();
        update.setId(id);
        update.setStatus(status);
        userMapper.updateById(update);
    }

    /**
     * 从 Excel 文件批量导入教师，逐行校验工号唯一性与必填项，返回导入结果。
     *
     * @param file 上传的 Excel 文件
     * @return 导入结果，含成功数量、失败数量与错误明细
     * @throws IOException 读取文件失败时抛出
     */
    @Transactional
    public ImportResult importFromExcel(MultipartFile file) throws IOException {
        List<ExcelRowError> errors = new ArrayList<>();
        int successCount = 0;

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String username = ExcelUtil.getStringValue(row.getCell(0));
                String realName = ExcelUtil.getStringValue(row.getCell(1));
                String department = ExcelUtil.getStringValue(row.getCell(2));
                String phone = ExcelUtil.getStringValue(row.getCell(3));

                if (!StringUtils.isNotBlank(username) || !StringUtils.isNotBlank(realName)) {
                    errors.add(new ExcelRowError(i + 1, "工号和姓名不能为空"));
                    continue;
                }

                Long count = userMapper.selectCount(
                        new LambdaQueryWrapper<User>().eq(User::getUsername, username));
                if (count > 0) {
                    errors.add(new ExcelRowError(i + 1, "工号已存在：" + username));
                    continue;
                }

                User teacher = new User();
                teacher.setUsername(username);
                teacher.setRealName(realName);
                teacher.setPassword(passwordEncoder.encode("Ltpms@" + username));
                teacher.setRole("TEACHER");
                teacher.setDepartment(department);
                teacher.setPhone(phone);
                teacher.setStatus(1);
                teacher.setIsDefaultPwd(1);
                teacher.setFailedAttempts(0);
                userMapper.insert(teacher);
                successCount++;
            }
        }

        return new ImportResult(successCount, errors.size(), errors);
    }

    /**
     * 生成教师 Excel 导入模板的字节内容。
     *
     * @return 模板文件字节数组
     * @throws IOException 生成模板失败时抛出
     */
    public byte[] downloadTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("教师导入模板");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("工号");
            header.createCell(1).setCellValue("姓名");
            header.createCell(2).setCellValue("院系");
            header.createCell(3).setCellValue("联系电话");

            sheet.setColumnWidth(0, 18);
            sheet.setColumnWidth(1, 15);
            sheet.setColumnWidth(2, 25);
            sheet.setColumnWidth(3, 18);

            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    /**
     * 教师导入结果，包含成功数量、失败数量与逐行错误明细。
     */
    public record ImportResult(int successCount, int failedCount, List<ExcelRowError> errors) {
    }

    /**
     * Excel 单条行导入错误，包含行号与错误信息。
     */
    public record ExcelRowError(int rowNum, String message) {
    }
}
