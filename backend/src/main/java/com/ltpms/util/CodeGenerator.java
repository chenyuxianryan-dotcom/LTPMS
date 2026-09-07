package com.ltpms.util;

import com.ltpms.entity.Course;
import com.ltpms.entity.Experiment;
import com.ltpms.entity.Semester;
import com.ltpms.mapper.ExperimentMapper;
import com.ltpms.mapper.SemesterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 实验编号生成器
 * 格式：{课程号}-{学期编码}-{三位流水号}
 * 示例：CS101-20261-001
 * 学期编码规则：起始年份 + 学期序号（1/2）
 */
@Component
@RequiredArgsConstructor
public class CodeGenerator {

    private final ExperimentMapper experimentMapper;
    private final SemesterMapper semesterMapper;

    /**
     * 生成实验项目编号，格式为 {课程号}-{学期编码}-{三位流水号}，如 CS101-20261-001。
     *
     * @param course 所属课程
     * @return 生成的实验项目编号
     */
    public String generateExperimentCode(Course course) {
        String semesterCode = encodeSemester(course.getSemesterId());
        String prefix = course.getCourseCode() + "-" + semesterCode + "-";

        long nextSeq = experimentMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Experiment>()
                        .eq(Experiment::getCourseId, course.getId())) + 1;

        return prefix + String.format("%03d", nextSeq);
    }

    /**
     * 将学期 ID 编码为学期编码（起始年份 + 学期序号）。
     *
     * @param semesterId 学期 ID
     * @return 学期编码；学期信息不完整时回退为学期 ID 的字符串
     */
    private String encodeSemester(Long semesterId) {
        Semester semester = semesterMapper.selectById(semesterId);
        if (semester == null || semester.getAcademicYear() == null || semester.getSemesterNum() == null) {
            return String.valueOf(semesterId);
        }
        String startYear = semester.getAcademicYear().split("-")[0];
        return startYear + semester.getSemesterNum();
    }
}
