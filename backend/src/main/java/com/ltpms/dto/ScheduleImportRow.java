package com.ltpms.dto;

import lombok.Data;

/**
 * 课表导入单行（Excel 导入与课表照片识别草稿共用）。
 * 字段与课表导入模板列对齐：课程号/课程名/总学时/教师工号/实验室编号/上课班级/选课人数/上课时间。
 */
@Data
public class ScheduleImportRow {

    private String courseCode;
    private String courseName;
    private Double totalHours;
    private String teacherNo;
    private Long teacherId;
    private String labCode;
    private String classNames;
    private Integer studentCount;
    private String rawScheduleText;
}
