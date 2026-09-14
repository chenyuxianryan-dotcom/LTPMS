package com.ltpms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltpms.entity.Holiday;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 节假日数据访问接口。
 */
public interface HolidayMapper extends BaseMapper<Holiday> {
}
