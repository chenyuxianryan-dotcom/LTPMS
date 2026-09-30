package com.ltpms.dto.project;

import lombok.Data;

/**
 * 手动新建分组请求（组名可自定义；每组人数基准只读实验目录，不接受手填）。
 */
@Data
public class GroupCreateRequest {

    private Long experimentId;
    private String groupName;
}
