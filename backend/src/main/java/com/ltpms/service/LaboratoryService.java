package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.common.PageResult;
import com.ltpms.dto.LaboratoryRequest;
import com.ltpms.entity.Laboratory;
import com.ltpms.entity.Schedule;
import com.ltpms.mapper.LaboratoryMapper;
import com.ltpms.mapper.ScheduleMapper;
import com.ltpms.util.ExcelUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 实验室服务，提供实验室的分页查询、增删改、Excel 导入与模板下载等能力，
 * 删除前校验是否存在关联课表数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LaboratoryService {

    private final LaboratoryMapper laboratoryMapper;
    private final ScheduleMapper scheduleMapper;

    /**
     * 分页查询实验室列表，支持按编号或名称关键字模糊搜索。
     *
     * @param page    页码，从 1 开始
     * @param size    每页大小
     * @param keyword 关键字，可选
     * @return 分页后的实验室列表
     */
    public PageResult<Laboratory> list(int page, int size, String keyword) {
        LambdaQueryWrapper<Laboratory> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Laboratory::getCreatedAt);
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(Laboratory::getLabCode, keyword)
                    .or()
                    .like(Laboratory::getLabName, keyword));
        }
        Page<Laboratory> result = laboratoryMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据 ID 获取实验室，不存在时抛出异常。
     *
     * @param id 实验室 ID
     * @return 实验室实体
     * @throws BusinessException 当实验室不存在时抛出
     */
    public Laboratory getById(Long id) {
        Laboratory lab = laboratoryMapper.selectById(id);
        if (lab == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "实验室不存在");
        }
        return lab;
    }

    /**
     * 新增实验室，校验实验室编号唯一性。
     *
     * @param request 实验室信息
     * @throws BusinessException 当实验室编号已存在时抛出
     */
    @Transactional
    public void create(LaboratoryRequest request) {
        if (existsByLabCode(request.getLabCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "实验室编号已存在");
        }
        Laboratory lab = new Laboratory();
        lab.setLabCode(request.getLabCode());
        lab.setLabName(request.getLabName());
        lab.setManager(request.getManager());
        lab.setDeviceCount(request.getDeviceCount());
        lab.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        laboratoryMapper.insert(lab);
    }

    /**
     * 更新指定实验室，校验实验室编号唯一性。
     *
     * @param id      实验室 ID
     * @param request 实验室更新信息
     * @throws BusinessException 当实验室不存在或编号已存在时抛出
     */
    @Transactional
    public void update(Long id, LaboratoryRequest request) {
        Laboratory existing = getById(id);
        if (!existing.getLabCode().equals(request.getLabCode()) && existsByLabCode(request.getLabCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "实验室编号已存在");
        }
        existing.setLabCode(request.getLabCode());
        existing.setLabName(request.getLabName());
        existing.setManager(request.getManager());
        existing.setDeviceCount(request.getDeviceCount());
        if (request.getStatus() != null) {
            existing.setStatus(request.getStatus());
        }
        laboratoryMapper.updateById(existing);
    }

    /**
     * 删除指定实验室，若存在关联课表数据则禁止删除。
     *
     * @param id 实验室 ID
     * @throws BusinessException 当实验室不存在或已关联课表数据时抛出
     */
    @Transactional
    public void delete(Long id) {
        Laboratory lab = getById(id);
        long relatedSchedules = scheduleMapper.selectCount(
                new LambdaQueryWrapper<Schedule>().eq(Schedule::getLabCode, lab.getLabCode()));
        if (relatedSchedules > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该实验室已关联课表数据，无法删除");
        }
        laboratoryMapper.deleteById(id);
    }

    /**
     * 批量删除实验室。逐条复用单条删除的关联课表校验逻辑，
     * 存在关联的实验室跳过并记录错误，最终统一返回成功/失败明细（事务内一次性提交）。
     *
     * @param ids 待删除的实验室 ID 列表
     * @return 批量删除结果，含成功数量、失败数量与逐条错误明细
     */
    @Transactional
    public BatchDeleteResult deleteBatch(List<Long> ids) {
        int successCount = 0;
        List<BatchDeleteError> errors = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return new BatchDeleteResult(0, 0, errors);
        }
        for (Long id : ids) {
            try {
                Laboratory lab = laboratoryMapper.selectById(id);
                if (lab == null) {
                    errors.add(new BatchDeleteError(id, "实验室不存在"));
                    continue;
                }
                long relatedSchedules = scheduleMapper.selectCount(
                        new LambdaQueryWrapper<Schedule>().eq(Schedule::getLabCode, lab.getLabCode()));
                if (relatedSchedules > 0) {
                    errors.add(new BatchDeleteError(id, "该实验室已关联课表数据，无法删除"));
                    continue;
                }
                laboratoryMapper.deleteById(id);
                successCount++;
            } catch (Exception e) {
                log.warn("实验室批量删除单条失败 id={}", id, e);
                errors.add(new BatchDeleteError(id, "删除失败：" + e.getMessage()));
            }
        }
        return new BatchDeleteResult(successCount, errors.size(), errors);
    }

    /**
     * 批量删除结果，包含成功数量、失败数量与逐条错误明细。
     */
    public record BatchDeleteResult(int successCount, int failedCount, List<BatchDeleteError> errors) {
    }

    /**
     * 批量删除单条错误，包含实验室 ID 与错误信息。
     */
    public record BatchDeleteError(Long id, String message) {
    }

    private boolean existsByLabCode(String labCode) {
        return laboratoryMapper.selectCount(
                new LambdaQueryWrapper<Laboratory>().eq(Laboratory::getLabCode, labCode)) > 0;
    }

    /**
     * 从 Excel 文件批量导入实验室，逐行校验编号唯一性与必填项，返回导入结果。
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

                String labCode = ExcelUtil.getStringValue(row.getCell(0));
                String labName = ExcelUtil.getStringValue(row.getCell(1));
                String manager = ExcelUtil.getStringValue(row.getCell(2));
                Integer deviceCount = ExcelUtil.getIntValue(row.getCell(3));

                if (!StringUtils.isNotBlank(labCode) || !StringUtils.isNotBlank(labName)) {
                    errors.add(new ExcelRowError(i + 1, "实验室编号和名称不能为空"));
                    continue;
                }

                if (existsByLabCode(labCode)) {
                    errors.add(new ExcelRowError(i + 1, "实验室编号已存在：" + labCode));
                    continue;
                }

                Laboratory lab = new Laboratory();
                lab.setLabCode(labCode);
                lab.setLabName(labName);
                lab.setManager(manager);
                lab.setDeviceCount(deviceCount != null ? deviceCount : 0);
                lab.setStatus(1);
                laboratoryMapper.insert(lab);
                successCount++;
            }
        }

        return new ImportResult(successCount, errors.size(), errors);
    }

    /**
     * 生成实验室 Excel 导入模板的字节内容。
     *
     * @return 模板文件字节数组
     * @throws IOException 生成模板失败时抛出
     */
    public byte[] downloadTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("实验室导入模板");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("实验室编号");
            header.createCell(1).setCellValue("实验室名称");
            header.createCell(2).setCellValue("负责人");
            header.createCell(3).setCellValue("设备数");

            sheet.setColumnWidth(0, 20);
            sheet.setColumnWidth(1, 25);
            sheet.setColumnWidth(2, 15);
            sheet.setColumnWidth(3, 12);

            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    /**
     * 实验室导入结果，包含成功数量、失败数量与逐行错误明细。
     */
    public record ImportResult(int successCount, int failedCount, List<ExcelRowError> errors) {
    }

    /**
     * Excel 单条行导入错误，包含行号与错误信息。
     */
    public record ExcelRowError(int rowNum, String message) {
    }
}
