package com.ltpms.dto;

import lombok.Data;

import java.util.List;

/**
 * 课表上传催办请求（管理员）。
 */
@Data
public class UploadRemindRequest {

    private Long semesterId;
    private List<Long> teacherIds;
}
