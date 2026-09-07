package com.ltpms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltpms.entity.Semester;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 学期数据访问接口。
 */
public interface SemesterMapper extends BaseMapper<Semester> {
}
