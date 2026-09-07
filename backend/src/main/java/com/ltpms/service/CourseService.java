package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.common.PageResult;
import com.ltpms.entity.Course;
import com.ltpms.entity.Experiment;
import com.ltpms.entity.User;
import com.ltpms.mapper.CourseMapper;
import com.ltpms.mapper.ExperimentMapper;
import com.ltpms.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 课程服务，提供课程列表查询、课程详情（含教师与实验项目）等能力。
 */
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseMapper courseMapper;
    private final UserMapper userMapper;
    private final ExperimentMapper experimentMapper;

    /**
     * 查询课程列表，可按学期筛选；教师角色仅返回本人负责课程。
     *
     * @param semesterId    学期 ID，为空时不按学期筛选
     * @param currentUserId 当前登录用户 ID
     * @param role          当前用户角色
     * @return 课程列表
     */
    public List<Course> list(Long semesterId, Long currentUserId, String role) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        if (semesterId != null) {
            wrapper.eq(Course::getSemesterId, semesterId);
        }
        if ("TEACHER".equals(role)) {
            wrapper.eq(Course::getTeacherId, currentUserId);
        }
        wrapper.orderByDesc(Course::getCreatedAt);
        return courseMapper.selectList(wrapper);
    }

    /**
     * 根据 ID 获取课程，不存在时抛出异常。
     *
     * @param id 课程 ID
     * @return 课程实体
     * @throws BusinessException 当课程不存在时抛出
     */
    public Course getById(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        return course;
    }

    /**
     * 获取课程详情，包含课程基本信息、授课教师与实验项目列表。
     *
     * @param id 课程 ID
     * @return 课程详情（含教师与实验项目）
     * @throws BusinessException 当课程不存在时抛出
     */
    public CourseDetail getDetail(Long id) {
        Course course = getById(id);
        User teacher = userMapper.selectById(course.getTeacherId());
        List<Experiment> experiments = experimentMapper.selectList(
                new LambdaQueryWrapper<Experiment>()
                        .eq(Experiment::getCourseId, id)
                        .orderByAsc(Experiment::getSortOrder));
        return new CourseDetail(course, teacher, experiments);
    }

    public record CourseDetail(Course course, User teacher, List<Experiment> experiments) {
    }
}
