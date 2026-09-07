package com.ltpms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltpms.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 用户数据访问接口。
 */
public interface UserMapper extends BaseMapper<User> {
}
