package com.ltpms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltpms.entity.ScheduleRevision;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 修订留痕数据访问接口。
 */
public interface ScheduleRevisionMapper extends BaseMapper<ScheduleRevision> {
}
