package com.ltpms.common;

import lombok.Getter;

/**
 * 错误码枚举
 */
@Getter
public enum ErrorCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未认证或Token已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 认证相关 41xxx
    ACCOUNT_LOCKED(41001, "账号已锁定"),
    OLD_PASSWORD_WRONG(41002, "原密码错误"),
    PASSWORD_TOO_SIMPLE(41003, "新密码至少8位且包含字母和数字"),

    // 课表导入相关 42xxx
    SCHEDULE_TEACHER_NOT_FOUND(42001, "课表导入-教师工号不存在"),
    SCHEDULE_LAB_NOT_FOUND(42002, "课表导入-实验室编号不存在"),
    SCHEDULE_TEACHER_CONFLICT(42003, "课表导入-同一课程号存在多位教师"),

    // 实验项目复制 43xxx
    NO_PREVIOUS_EXPERIMENTS(43001, "上学期无实验项目"),

    // NL2SQL 相关 44xxx
    NLQ_UNSAFE(44001, "NL2SQL 问题无法安全转换"),
    NLQ_PERMISSION_REJECT(44002, "NL2SQL 无法建立数据权限"),
    NLQ_MODEL_TIMEOUT(44003, "NL2SQL 外部模型超时或不可用"),

    // 通用业务 45xxx
    NOT_IMPLEMENTED(45001, "功能尚未实现");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
