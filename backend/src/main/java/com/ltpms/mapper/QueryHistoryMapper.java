package com.ltpms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltpms.entity.QueryHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 查询历史数据访问接口。
 */
public interface QueryHistoryMapper extends BaseMapper<QueryHistory> {
}
