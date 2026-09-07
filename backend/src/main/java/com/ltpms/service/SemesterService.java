package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.dto.SemesterRequest;
import com.ltpms.entity.Semester;
import com.ltpms.mapper.SemesterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 学期服务，提供学期列表、当前学期解析（含状态动态计算与回写）以及学期的增改等能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemesterService {

    private final SemesterMapper semesterMapper;

    public static final String STATUS_UPCOMING = "UPCOMING";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ENDED = "ENDED";

    /**
     * 查询学期列表，按开始日期倒序，并动态计算各学期状态（未开始/进行中/已结束）后回写。
     *
     * @param status 学期状态过滤，可选；为空时返回全部
     * @return 学期列表
     */
    public List<Semester> list(String status) {
        List<Semester> semesters = semesterMapper.selectList(
                new LambdaQueryWrapper<Semester>().orderByDesc(Semester::getStartDate));

        LocalDate today = LocalDate.now();
        Map<Long, String> originalStatusMap = new java.util.HashMap<>();
        for (Semester semester : semesters) {
            originalStatusMap.put(semester.getId(), semester.getStatus());
            String computed = computeStatus(semester, today);
            semester.setStatus(computed);
        }

        // 回写缓存
        writeBackStatus(semesters, originalStatusMap);

        if (StringUtils.isNotBlank(status)) {
            return semesters.stream()
                    .filter(s -> status.equals(s.getStatus()))
                    .collect(Collectors.toList());
        }
        return semesters;
    }

    /**
     * 获取当前生效的学期：优先返回包含今天的学期，其次最近结束的，再次最近创建的未开始学期。
     *
     * @return 当前学期，无匹配时返回 null
     */
    public Semester getCurrentSemester() {
        List<Semester> all = list(null);
        LocalDate today = LocalDate.now();

        // 1. 落在某学期内
        for (Semester semester : all) {
            if (!today.isBefore(semester.getStartDate()) && !today.isAfter(semester.getEndDate())) {
                return semester;
            }
        }

        // 2. 寒暑假空档：返回最近结束的学期（ENDED 中 end_date 最大者）
        List<Semester> ended = all.stream()
                .filter(s -> STATUS_ENDED.equals(s.getStatus()))
                .sorted(Comparator.comparing(Semester::getEndDate).reversed())
                .toList();
        if (!ended.isEmpty()) {
            return ended.get(0);
        }

        // 3. 所有学期均未开始：返回最近创建的 UPCOMING 学期
        List<Semester> upcoming = all.stream()
                .filter(s -> STATUS_UPCOMING.equals(s.getStatus()))
                .sorted(Comparator.comparing(Semester::getCreatedAt).reversed())
                .toList();
        if (!upcoming.isEmpty()) {
            return upcoming.get(0);
        }

        return null;
    }

    /**
     * 新增学期，校验日期合法性、名称唯一性并设置初始状态。
     *
     * @param request 学期信息
     * @throws BusinessException 当日期不合法或学期名称已存在时抛出
     */
    @Transactional
    public void create(SemesterRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束日期不能早于开始日期");
        }
        String semesterName = request.getAcademicYear() + "-" + request.getSemesterNum();

        Long count = semesterMapper.selectCount(
                new LambdaQueryWrapper<Semester>().eq(Semester::getSemesterName, semesterName));
        if (count > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "学期已存在：" + semesterName);
        }

        Semester semester = new Semester();
        semester.setAcademicYear(request.getAcademicYear());
        semester.setSemesterNum(request.getSemesterNum());
        semester.setSemesterName(semesterName);
        semester.setStartDate(request.getStartDate());
        semester.setEndDate(request.getEndDate());
        semester.setStatus(computeStatus(semester, LocalDate.now()));
        semesterMapper.insert(semester);
    }

    /**
     * 更新指定学期，已开始或已结束的学期禁止编辑。
     *
     * @param id      学期 ID
     * @param request 学期更新信息
     * @throws BusinessException 当学期不存在、已开始的学期或日期不合法时抛出
     */
    @Transactional
    public void update(Long id, SemesterRequest request) {
        Semester semester = semesterMapper.selectById(id);
        if (semester == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学期不存在");
        }

        LocalDate today = LocalDate.now();
        if (semester.getStartDate() != null && !today.isBefore(semester.getStartDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已开始或已结束的学期不能编辑");
        }

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束日期不能早于开始日期");
        }

        semester.setAcademicYear(request.getAcademicYear());
        semester.setSemesterNum(request.getSemesterNum());
        semester.setSemesterName(request.getAcademicYear() + "-" + request.getSemesterNum());
        semester.setStartDate(request.getStartDate());
        semester.setEndDate(request.getEndDate());
        semester.setStatus(computeStatus(semester, today));
        semesterMapper.updateById(semester);
    }

    private String computeStatus(Semester semester, LocalDate today) {
        if (today.isBefore(semester.getStartDate())) {
            return STATUS_UPCOMING;
        }
        if (today.isAfter(semester.getEndDate())) {
            return STATUS_ENDED;
        }
        return STATUS_ACTIVE;
    }

    private void writeBackStatus(List<Semester> semesters, Map<Long, String> originalStatusMap) {
        for (Semester semester : semesters) {
            String original = originalStatusMap.get(semester.getId());
            if (!semester.getStatus().equals(original)) {
                Semester update = new Semester();
                update.setId(semester.getId());
                update.setStatus(semester.getStatus());
                semesterMapper.updateById(update);
            }
        }
    }
}
