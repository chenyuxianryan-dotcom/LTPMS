package com.ltpms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltpms.entity.LabOpenException;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 实验室开放例外日数据访问接口。
 */
public interface LabOpenExceptionMapper extends BaseMapper<LabOpenException> {
}
