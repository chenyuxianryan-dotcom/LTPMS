package com.ltpms.dto;

import lombok.Data;

/**
 * 课表行编辑请求（教师本人/管理员）：字段为 null 表示不修改。
 */
@Data
public class ScheduleEditRequest {

    private String labCode;
    private String classNames;
    private Integer studentCount;
    private String rawScheduleText;
}
