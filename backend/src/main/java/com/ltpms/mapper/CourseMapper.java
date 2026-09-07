package com.ltpms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltpms.entity.Course;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 课程数据访问接口。
 */
public interface CourseMapper extends BaseMapper<Course> {
}
