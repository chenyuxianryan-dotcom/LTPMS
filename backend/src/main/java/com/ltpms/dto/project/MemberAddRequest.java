package com.ltpms.dto.project;

import lombok.Data;

import java.util.List;

/**
 * 课题成员批量加入请求（学生库勾选；学生档案不存在时先经导入建档）。
 */
@Data
public class MemberAddRequest {

    private List<Long> studentIds;
}
