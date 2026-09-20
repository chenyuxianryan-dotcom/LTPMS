package com.ltpms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltpms.entity.Student;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StudentMapper extends BaseMapper<Student> {
}
