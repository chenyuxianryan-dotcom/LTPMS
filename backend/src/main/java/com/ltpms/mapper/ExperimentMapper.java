package com.ltpms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltpms.entity.Experiment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 实验项目数据访问接口。
 */
public interface ExperimentMapper extends BaseMapper<Experiment> {
}
