package com.ltpms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltpms.entity.ScheduleTime;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 课表时间段数据访问接口。
 */
public interface ScheduleTimeMapper extends BaseMapper<ScheduleTime> {
}
