package com.ltpms.dto.project;

import lombok.Data;

import java.util.List;

/**
 * 分组组员移入请求（移入即从同实验其他组摘出，保证同一实验下一人只属一组）。
 */
@Data
public class GroupMembersRequest {

    private List<Long> studentIds;
}
