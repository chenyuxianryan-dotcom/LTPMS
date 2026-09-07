package com.ltpms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltpms.entity.Dictionary;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 字典数据访问接口。
 */
public interface DictionaryMapper extends BaseMapper<Dictionary> {
}
