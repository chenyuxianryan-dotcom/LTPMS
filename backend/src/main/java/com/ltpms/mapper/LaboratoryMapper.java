package com.ltpms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltpms.entity.Laboratory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 实验室数据访问接口。
 */
public interface LaboratoryMapper extends BaseMapper<Laboratory> {
}
