package com.ltpms.mapper;

import com.ltpms.dto.ReportDetailItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/**
 * 报表统计数据访问接口。
 */
public interface ReportMapper {

    List<ReportDetailItem> selectCourseProjectStats(
            @Param("academicYear") String academicYear,
            @Param("teacherId") Long teacherId);

    List<ReportDetailItem> selectPersonHours(
            @Param("academicYear") String academicYear,
            @Param("teacherId") Long teacherId);
}
