package com.ltpms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltpms.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 通知数据访问接口。
 */
public interface NotificationMapper extends BaseMapper<Notification> {
}
