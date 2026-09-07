package com.ltpms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltpms.entity.OperationLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 操作日志数据访问接口。
 */
public interface OperationLogMapper extends BaseMapper<OperationLogEntity> {
}
