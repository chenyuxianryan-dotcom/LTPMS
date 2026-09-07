package com.ltpms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltpms.entity.Schedule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 课表数据访问接口。
 */
public interface ScheduleMapper extends BaseMapper<Schedule> {
}
